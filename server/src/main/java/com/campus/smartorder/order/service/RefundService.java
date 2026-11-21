package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.order.api.OrderDto;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStateMachine;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.StockEffect;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import com.campus.smartorder.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
public class RefundService {

    private static final Set<OrderStatus> REFUND_SOURCES = Set.of(
            OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.COOKING);

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OpenStateService clock;

    public RefundService(RefundMapper refundMapper, OrderMapper orderMapper, OrderStatusLogMapper statusLogMapper,
                         OrderService orderService, PaymentService paymentService, OpenStateService clock) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Transactional
    public OrderDto.RefundView audit(Long merchantId, Long accountId, String refundNo, boolean approved, String note) {
        return auditCore(merchantId, accountId, refundNo, approved, note, OperatorType.MERCHANT);
    }

    @Transactional
    public OrderDto.RefundView platformAudit(Long accountId, String refundNo, boolean approved, String note) {
        if (accountId == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return auditCore(null, accountId, refundNo, approved, note, OperatorType.PLATFORM);
    }

    private OrderDto.RefundView auditCore(Long merchantId, Long accountId, String refundNo, boolean approved,
                                         String note, OperatorType operatorType) {
        String text = Ids.blank(note).trim();
        if (text.length() > 255 || (!approved && text.isEmpty())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "驳回须填写原因，审核说明不能超过 255 字");
        }
        Refund refund = refundMapper.selectByRefundNoForUpdate(refundNo);
        if (refund == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "退款申请不存在");
        }
        if (operatorType == OperatorType.MERCHANT && !Objects.equals(merchantId, refund.getMerchantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能审核其他档口的退款");
        }
        Order order = orderMapper.selectByIdForUpdate(refund.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(refund.getMerchantId(), order.getMerchantId()) || !Objects.equals(refund.getUserId(), order.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String target = approved ? Refund.STATUS_SUCCEEDED : Refund.STATUS_REJECTED;
        if (operatorType == OperatorType.MERCHANT && target.equals(refund.getStatus())) {
            return view(refund, order);
        }
        if (!Refund.STATUS_PENDING.equals(refund.getStatus()) || order.getStatus() != OrderStatus.REFUNDING) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "退款已处理，请刷新订单");
        }
        OrderStatusLog application = statusLogMapper.selectOne(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, order.getId())
                .eq(OrderStatusLog::getToStatus, OrderStatus.REFUNDING)
                .orderByDesc(OrderStatusLog::getId).last("LIMIT 1"));
        if (application == null || application.getFromStatus() == null
                || !REFUND_SOURCES.contains(application.getFromStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "缺少退款前的订单状态，无法审核");
        }
        OrderStatus previous = application.getFromStatus();
        LocalDateTime now = clock.now();
        OrderStatus next = approved ? OrderStatus.REFUNDED : previous;
        int changed;
        if (approved) {
            requireFullRefund(refund, order);
            paymentService.requireRefundablePayment(order.getId(), refund.getRefundAmount());
            changed = orderMapper.casToRefunded(order.getId());
        } else {
            changed = orderMapper.casRestoreFromRefunding(order.getId(), previous);
        }
        if (changed == 0) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }
        if (approved) {
            if (OrderStateMachine.stockEffect(OrderStatus.REFUNDING, next, previous) == StockEffect.RELEASE_LOCK) {
                orderService.releaseHeldResources(order);
            }
            refund.setSucceededAt(now);
            refund.setChannelRefundNo("MOCK" + refund.getRefundNo());
        }
        refund.setStatus(target);
        refund.setAuditorId(accountId);
        refund.setAuditNote(text);
        refund.setAuditedAt(now);
        refund.setUpdatedAt(now);
        refundMapper.updateById(refund);

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(OrderStatus.REFUNDING);
        log.setToStatus(next);
        log.setOperatorType(operatorType);
        log.setOperatorId(accountId);
        log.setOperatorName(operatorType == OperatorType.PLATFORM ? "平台管理员 #" + accountId : "档口负责人");
        log.setNote(text.isEmpty() ? "同意退款" : text);
        log.setCreatedAt(now);
        statusLogMapper.insert(log);
        return view(refund, order);
    }

    private void requireFullRefund(Refund refund, Order order) {
        if (refund.getRefundAmount() == null || refund.getRefundAmount() < 0
                || !refund.getRefundAmount().equals(order.getPayAmount())) {
            throw new BizException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "当前仅支持整单退款");
        }
        Long succeeded = refundMapper.selectCount(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, order.getId()).eq(Refund::getStatus, Refund.STATUS_SUCCEEDED));
        if (succeeded > 0) {
            throw new BizException(ErrorCode.ORDER_REFUND_NOT_ALLOWED, "该订单已有成功退款");
        }
    }

    private OrderDto.RefundView view(Refund refund, Order order) {
        return new OrderDto.RefundView(refund.getRefundNo(), order.getOrderNo(), refund.getRefundAmount(),
                refund.getStatus(), Ids.blank(refund.getReasonText()), Ids.blank(refund.getAuditNote()),
                refund.getCreatedAt(), refund.getAuditedAt(), refund.getSucceededAt());
    }
}
