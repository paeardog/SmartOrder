package com.campus.smartorder.order.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import com.campus.smartorder.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

    private final RefundMapper refunds = mock(RefundMapper.class);
    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderStatusLogMapper logs = mock(OrderStatusLogMapper.class);
    private final OrderService orderService = mock(OrderService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final OpenStateService clock = mock(OpenStateService.class);
    private final RefundService service = new RefundService(refunds, orders, logs, orderService, payments, clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
    private Refund refund;
    private Order order;
    private OrderStatusLog application;

    @BeforeEach
    void setUp() {
        refund = new Refund();
        refund.setId(1L);
        refund.setRefundNo("R2026111800010001");
        refund.setOrderId(10L);
        refund.setUserId(20L);
        refund.setMerchantId(30L);
        refund.setRefundAmount(1200);
        refund.setStatus(Refund.STATUS_PENDING);
        refund.setReasonText("等待太久");
        refund.setCreatedAt(now.minusMinutes(2));
        order = new Order();
        order.setId(10L);
        order.setOrderNo("2026111800010001");
        order.setUserId(20L);
        order.setMerchantId(30L);
        order.setPayAmount(1200);
        order.setStatus(OrderStatus.REFUNDING);
        application = new OrderStatusLog();
        application.setFromStatus(OrderStatus.PAID);
        application.setToStatus(OrderStatus.REFUNDING);
        when(refunds.selectByRefundNoForUpdate(refund.getRefundNo())).thenReturn(refund);
        when(orders.selectByIdForUpdate(10L)).thenReturn(order);
        when(logs.selectOne(any())).thenReturn(application);
        when(clock.now()).thenReturn(now);
        when(orders.casToRefunded(10L)).thenReturn(1);
        when(orders.casRestoreFromRefunding(eq(10L), any())).thenReturn(1);
        when(refunds.selectCount(any())).thenReturn(0L);
    }

    @Test
    void approvesAndRecordsAudit() {
        var result = audit(true, "  同意  ");
        assertEquals(Refund.STATUS_SUCCEEDED, result.status());
        assertEquals("同意", result.auditNote());
        assertEquals(now, result.succeededAt());
        assertEquals(40L, refund.getAuditorId());
        assertEquals("MOCK" + refund.getRefundNo(), refund.getChannelRefundNo());
        verify(payments).requireRefundablePayment(10L, 1200);
        verify(orders).casToRefunded(10L);
        verify(orderService).releaseHeldResources(order);
        verify(refunds).updateById(refund);
        ArgumentCaptor<OrderStatusLog> saved = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(logs).insert(saved.capture());
        assertEquals(OrderStatus.REFUNDED, saved.getValue().getToStatus());
        assertEquals(40L, saved.getValue().getOperatorId());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "ACCEPTED", "COOKING"})
    void rejectsToOriginalStatusWithoutReleasingResources(OrderStatus previous) {
        application.setFromStatus(previous);
        var result = audit(false, "已经备餐");
        assertEquals(Refund.STATUS_REJECTED, result.status());
        assertNull(result.succeededAt());
        verify(orders).casRestoreFromRefunding(10L, previous);
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void repeatsSameDecisionWithoutSideEffects() {
        refund.setStatus(Refund.STATUS_SUCCEEDED);
        order.setStatus(OrderStatus.REFUNDED);
        assertEquals(Refund.STATUS_SUCCEEDED, audit(true, "").status());
        verify(orders, never()).casToRefunded(anyLong());
        verify(refunds, never()).updateById(any(Refund.class));
        verifyNoInteractions(payments, orderService, logs);
    }

    @Test
    void repeatsRejectionWithoutRestoringOrderAgain() {
        refund.setStatus(Refund.STATUS_REJECTED);
        order.setStatus(OrderStatus.READY);
        assertEquals(Refund.STATUS_REJECTED, audit(false, "已经处理").status());
        verify(orders, never()).casRestoreFromRefunding(anyLong(), any());
        verifyNoInteractions(payments, orderService, logs);
    }

    @Test
    void rejectsOppositeDecision() {
        refund.setStatus(Refund.STATUS_SUCCEEDED);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> audit(false, "改为驳回"));
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void rejectsOtherMerchantBeforeReadingOrder() {
        assertError(ErrorCode.FORBIDDEN, () -> service.audit(31L, 40L, refund.getRefundNo(), true, ""));
        verify(orders, never()).selectByIdForUpdate(anyLong());
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void rejectsOrderOwnershipMismatch() {
        order.setMerchantId(31L);
        assertError(ErrorCode.FORBIDDEN, () -> audit(true, ""));
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void rejectsMissingRefund() {
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> service.audit(30L, 40L, "missing", true, ""));
    }

    @Test
    void rejectsMissingOrder() {
        when(orders.selectByIdForUpdate(10L)).thenReturn(null);
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> audit(true, ""));
    }

    @Test
    void rejectsInvalidNote() {
        assertError(ErrorCode.INVALID_PARAM, () -> audit(false, "  "));
        assertError(ErrorCode.INVALID_PARAM, () -> audit(true, "字".repeat(256)));
        verifyNoInteractions(refunds, orders, payments);
    }

    @Test
    void rejectsOrderOutsideRefundingState() {
        order.setStatus(OrderStatus.COMPLETED);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> audit(true, ""));
    }

    @Test
    void rejectsMissingApplicationHistory() {
        when(logs.selectOne(any())).thenReturn(null);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> audit(false, "不能确认原状态"));
    }

    @Test
    void rejectsPartialRefundAndOverRefund() {
        refund.setRefundAmount(1100);
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> audit(true, ""));
        refund.setRefundAmount(1300);
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> audit(true, ""));
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void rejectsAnotherSuccessfulRefund() {
        when(refunds.selectCount(any())).thenReturn(1L);
        assertError(ErrorCode.ORDER_REFUND_NOT_ALLOWED, () -> audit(true, ""));
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void rejectsUnsupportedPaymentBeforeUpdatingOrder() {
        doThrow(new BizException(ErrorCode.PAYMENT_CHANNEL_DISABLED))
                .when(payments).requireRefundablePayment(10L, 1200);
        assertError(ErrorCode.PAYMENT_CHANNEL_DISABLED, () -> audit(true, ""));
        verify(orders, never()).casToRefunded(anyLong());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsConcurrentChangeBeforeRefundAndRelease() {
        when(orders.casToRefunded(10L)).thenReturn(0);
        assertError(ErrorCode.ORDER_CONCURRENT_MODIFIED, () -> audit(true, ""));
        verify(refunds, never()).updateById(any(Refund.class));
        verifyNoInteractions(orderService);
    }

    @Test
    void platformApprovesAcrossMerchantsAndRecordsActualOperator() {
        var result = service.platformAudit(99L, refund.getRefundNo(), true, "平台核实");
        assertEquals(Refund.STATUS_SUCCEEDED, result.status());
        assertEquals(99L, refund.getAuditorId());
        ArgumentCaptor<OrderStatusLog> saved = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(logs).insert(saved.capture());
        assertEquals(com.campus.smartorder.order.domain.OperatorType.PLATFORM, saved.getValue().getOperatorType());
        assertEquals(99L, saved.getValue().getOperatorId());
        verify(payments).requireRefundablePayment(10L, 1200);
        verify(orderService).releaseHeldResources(order);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SUCCEEDED", "REJECTED"})
    void platformCannotOverwriteOrReplayProcessedRefund(String status) {
        refund.setStatus(status);
        refund.setAuditorId(40L);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.platformAudit(99L, refund.getRefundNo(), true, "同意"));
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.platformAudit(99L, refund.getRefundNo(), false, "驳回"));
        assertEquals(40L, refund.getAuditorId());
        verify(refunds, never()).updateById(any(Refund.class));
        verifyNoInteractions(payments, orderService, logs);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "ACCEPTED", "COOKING"})
    void platformRejectionOnlyRestoresRecordedState(OrderStatus previous) {
        application.setFromStatus(previous);
        service.platformAudit(99L, refund.getRefundNo(), false, "核实后驳回");
        verify(orders).casRestoreFromRefunding(10L, previous);
        verifyNoInteractions(payments, orderService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 1100, 1300})
    void platformRetainsAmountProtection(int amount) {
        refund.setRefundAmount(amount);
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> service.platformAudit(99L, refund.getRefundNo(), true, ""));
        verify(orders, never()).casToRefunded(anyLong());
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void platformRetainsSuccessfulRefundAndOrderStateProtection() {
        when(refunds.selectCount(any())).thenReturn(1L);
        assertError(ErrorCode.ORDER_REFUND_NOT_ALLOWED, () -> service.platformAudit(99L, refund.getRefundNo(), true, ""));
        order.setStatus(OrderStatus.COMPLETED);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.platformAudit(99L, refund.getRefundNo(), true, ""));
        verify(refunds, never()).updateById(any(Refund.class));
        verifyNoInteractions(payments, orderService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void platformRetainsCasProtection(boolean approved) {
        when(orders.casToRefunded(10L)).thenReturn(0);
        when(orders.casRestoreFromRefunding(10L, OrderStatus.PAID)).thenReturn(0);
        assertError(ErrorCode.ORDER_CONCURRENT_MODIFIED,
                () -> service.platformAudit(99L, refund.getRefundNo(), approved, "核实"));
        verify(refunds, never()).updateById(any(Refund.class));
        verify(logs, never()).insert(any(OrderStatusLog.class));
        verifyNoInteractions(orderService);
    }

    @Test
    void platformLocksRefundBeforeOrderAndStillChecksOwnershipConsistency() {
        order.setMerchantId(31L);
        assertError(ErrorCode.FORBIDDEN, () -> service.platformAudit(99L, refund.getRefundNo(), true, ""));
        var sequence = inOrder(refunds, orders);
        sequence.verify(refunds).selectByRefundNoForUpdate(refund.getRefundNo());
        sequence.verify(orders).selectByIdForUpdate(10L);
        verifyNoInteractions(payments, orderService);
    }

    @Test
    void platformRequiresAuditorAndRejectionReason() {
        assertError(ErrorCode.FORBIDDEN, () -> service.platformAudit(null, refund.getRefundNo(), true, ""));
        assertError(ErrorCode.INVALID_PARAM, () -> service.platformAudit(99L, refund.getRefundNo(), false, " "));
        verifyNoInteractions(refunds, orders, payments, orderService);
    }

    private com.campus.smartorder.order.api.OrderDto.RefundView audit(boolean approved, String note) {
        return service.audit(30L, 40L, refund.getRefundNo(), approved, note);
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BizException.class, action).errorCode());
    }
}
