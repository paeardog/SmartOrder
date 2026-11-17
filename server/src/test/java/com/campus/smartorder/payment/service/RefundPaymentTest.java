package com.campus.smartorder.payment.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.domain.PaymentStatus;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundPaymentTest {

    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final PaymentService service = new PaymentService(mapper, null, null, null, null, null);

    @Test
    void acceptsOnlyMatchingSuccessfulMockPayment() {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setChannel("MOCK");
        payment.setPayAmount(1200);
        when(mapper.selectLatestByOrderId(10L)).thenReturn(payment);
        assertDoesNotThrow(() -> service.requireRefundablePayment(10L, 1200));
        assertEquals(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                assertThrows(BizException.class, () -> service.requireRefundablePayment(10L, 1300)).errorCode());
        payment.setChannel("WXPAY_H5");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_DISABLED,
                assertThrows(BizException.class, () -> service.requireRefundablePayment(10L, 1200)).errorCode());
        payment.setStatus(PaymentStatus.PAYING);
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND,
                assertThrows(BizException.class, () -> service.requireRefundablePayment(10L, 1200)).errorCode());
    }

    @Test
    void rejectsMissingPayment() {
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND,
                assertThrows(BizException.class, () -> service.requireRefundablePayment(10L, 1200)).errorCode());
    }
}
