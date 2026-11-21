package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.order.api.OrderDto;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class PlatformOrderQueryService {
    public record Filter(String orderNo, Long userId, Long merchantId, String status,
                         LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        public Filter {
            if (page < 1 || pageSize < 1 || pageSize > 100
                    || (userId != null && userId <= 0) || (merchantId != null && merchantId <= 0)
                    || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
                throw new BizException(ErrorCode.INVALID_PARAM, "分页、编号或日期范围不合法");
            }
            orderNo = Ids.blank(orderNo).trim();
            status = Ids.blank(status).trim();
            if (orderNo.length() > 64) throw new BizException(ErrorCode.INVALID_PARAM);
        }
    }

    public record RefundRow(OrderDto.RefundView refund, String userId, String merchantId, String auditorId) { }

    private final OrderMapper orders;
    private final RefundMapper refunds;
    private final OrderService orderService;

    public PlatformOrderQueryService(OrderMapper orders, RefundMapper refunds, OrderService orderService) {
        this.orders = orders;
        this.refunds = refunds;
        this.orderService = orderService;
    }

    public PageDto<Order> orders(Filter filter) {
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
                .like(!filter.orderNo().isEmpty(), Order::getOrderNo, filter.orderNo())
                .eq(filter.userId() != null, Order::getUserId, filter.userId())
                .eq(filter.merchantId() != null, Order::getMerchantId, filter.merchantId());
        if (!filter.status().isEmpty()) {
            try {
                query.eq(Order::getStatus, OrderStatus.valueOf(filter.status()));
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.INVALID_PARAM, "订单状态不合法");
            }
        }
        if (filter.startDate() != null) query.ge(Order::getCreatedAt, filter.startDate().atStartOfDay());
        if (filter.endDate() != null) query.lt(Order::getCreatedAt, filter.endDate().plusDays(1).atStartOfDay());
        long total = orders.selectCount(query);
        List<Order> rows = total == 0 ? List.of() : orders.selectList(query.orderByDesc(Order::getId).last(limit(filter)));
        return PageDto.of(rows, total, filter.page(), filter.pageSize());
    }

    public Order order(String orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    public PageDto<RefundRow> refunds(Filter filter, String refundNo) {
        if (!filter.status().isEmpty() && !Set.of(Refund.STATUS_PENDING, Refund.STATUS_SUCCEEDED,
                Refund.STATUS_REJECTED).contains(filter.status())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "退款状态不合法");
        }
        String number = Ids.blank(refundNo).trim();
        if (number.length() > 64) throw new BizException(ErrorCode.INVALID_PARAM);
        LambdaQueryWrapper<Refund> query = new LambdaQueryWrapper<Refund>()
                .like(!number.isEmpty(), Refund::getRefundNo, number)
                .eq(filter.userId() != null, Refund::getUserId, filter.userId())
                .eq(filter.merchantId() != null, Refund::getMerchantId, filter.merchantId())
                .eq(!filter.status().isEmpty(), Refund::getStatus, filter.status());
        if (!filter.orderNo().isEmpty()) {
            Order order = orderService.findByOrderNo(filter.orderNo());
            if (order == null) return PageDto.of(List.of(), 0, filter.page(), filter.pageSize());
            query.eq(Refund::getOrderId, order.getId());
        }
        if (filter.startDate() != null) query.ge(Refund::getCreatedAt, filter.startDate().atStartOfDay());
        if (filter.endDate() != null) query.lt(Refund::getCreatedAt, filter.endDate().plusDays(1).atStartOfDay());
        long total = refunds.selectCount(query);
        List<RefundRow> rows = total == 0 ? List.of() : refunds.selectList(query.orderByDesc(Refund::getId)
                .last(limit(filter))).stream().map(this::refundRow).toList();
        return PageDto.of(rows, total, filter.page(), filter.pageSize());
    }

    public RefundRow refund(String refundNo) {
        Refund refund = refunds.selectOne(new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        if (refund == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "退款申请不存在");
        return refundRow(refund);
    }

    private RefundRow refundRow(Refund refund) {
        Order order = orders.selectById(refund.getOrderId());
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return new RefundRow(new OrderDto.RefundView(refund.getRefundNo(), order.getOrderNo(), refund.getRefundAmount(),
                refund.getStatus(), Ids.blank(refund.getReasonText()), Ids.blank(refund.getAuditNote()),
                refund.getCreatedAt(), refund.getAuditedAt(), refund.getSucceededAt()),
                Ids.of(refund.getUserId()), Ids.of(refund.getMerchantId()), Ids.of(refund.getAuditorId()));
    }

    private String limit(Filter filter) {
        return "LIMIT " + filter.pageSize() + " OFFSET " + (long) (filter.page() - 1) * filter.pageSize();
    }
}
