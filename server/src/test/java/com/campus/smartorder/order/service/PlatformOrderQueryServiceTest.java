package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformOrderQueryServiceTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final RefundMapper refunds = mock(RefundMapper.class);
    private final OrderService orderService = mock(OrderService.class);
    private final PlatformOrderQueryService service = new PlatformOrderQueryService(orders, refunds, orderService);
    private final LocalDate date = LocalDate.of(2026, 11, 18);

    @BeforeEach
    void initMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "platform-orders-test");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, Refund.class);
    }

    @Test
    void filtersOrdersAndUsesStableBoundedPagination() {
        when(orders.selectCount(any())).thenReturn(30L);
        when(orders.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Order> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("order_no LIKE"));
            assertTrue(sql.contains("user_id ="));
            assertTrue(sql.contains("merchant_id ="));
            assertTrue(sql.contains("created_at >="));
            assertTrue(sql.contains("created_at <"));
            assertTrue(sql.contains("ORDER BY id DESC"));
            assertTrue(sql.endsWith("LIMIT 20 OFFSET 20"));
            var values = query.getParamNameValuePairs().values();
            assertTrue(values.contains("%O1%"));
            assertTrue(values.contains(20L));
            assertTrue(values.contains(30L));
            assertTrue(values.contains(date.atStartOfDay()));
            assertTrue(values.contains(date.plusDays(1).atStartOfDay()));
            return List.of();
        });
        var result = service.orders(new PlatformOrderQueryService.Filter("O1", 20L, 30L, "REFUNDING", date, date, 2, 20));
        assertEquals(30, result.total());
        assertEquals(2, result.page());
    }

    @Test
    void refundOrderFilterNeverFallsBackToAllRowsWhenOrderMissing() {
        var result = service.refunds(new PlatformOrderQueryService.Filter("missing", null, null, "", null, null, 1, 20), "");
        assertTrue(result.list().isEmpty());
        verifyNoInteractions(refunds);
    }

    @Test
    void refundDetailIncludesActualAuditorAndOrderNumber() {
        Refund refund = new Refund();
        refund.setRefundNo("R1");
        refund.setOrderId(10L);
        refund.setMerchantId(30L);
        refund.setUserId(20L);
        refund.setAuditorId(99L);
        refund.setRefundAmount(1200);
        refund.setStatus("SUCCEEDED");
        Order order = new Order();
        order.setOrderNo("O1");
        when(refunds.selectOne(any())).thenReturn(refund);
        when(orders.selectById(10L)).thenReturn(order);
        var result = service.refund("R1");
        assertEquals("99", result.auditorId());
        assertEquals("20", result.userId());
        assertEquals("30", result.merchantId());
        assertEquals("O1", result.refund().orderNo());
        assertEquals(1200, result.refund().refundAmount());
    }

    @Test
    void rejectsUnknownStatusesAndInvalidPages() {
        var invalid = new PlatformOrderQueryService.Filter("", null, null, "UNKNOWN", null, null, 1, 20);
        assertEquals(ErrorCode.INVALID_PARAM, assertThrows(BizException.class, () -> service.orders(invalid)).errorCode());
        assertEquals(ErrorCode.INVALID_PARAM, assertThrows(BizException.class, () -> service.refunds(invalid, "")).errorCode());
        assertThrows(BizException.class, () -> new PlatformOrderQueryService.Filter("", null, null, "", date, date.minusDays(1), 1, 20));
        assertThrows(BizException.class, () -> new PlatformOrderQueryService.Filter("", null, null, "", null, null, 0, 20));
        assertThrows(BizException.class, () -> new PlatformOrderQueryService.Filter("", null, null, "", null, null, 1, 101));
        verifyNoInteractions(orders, refunds);
    }

    @Test
    void missingDetailsUseBusinessErrors() {
        assertEquals(ErrorCode.ORDER_NOT_FOUND, assertThrows(BizException.class, () -> service.order("missing")).errorCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BizException.class, () -> service.refund("missing")).errorCode());
    }
}
