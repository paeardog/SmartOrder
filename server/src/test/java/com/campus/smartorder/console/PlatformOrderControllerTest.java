package com.campus.smartorder.console;

import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.order.api.OrderDto;
import com.campus.smartorder.order.service.PlatformOrderQueryService;
import com.campus.smartorder.order.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformOrderControllerTest {
    private final PlatformOrderQueryService queries = mock(PlatformOrderQueryService.class);
    private final RefundService refunds = mock(RefundService.class);
    private final ConsoleOrderAssembler assembler = mock(ConsoleOrderAssembler.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlatformOrderController(queries, refunds, assembler,
                new com.campus.smartorder.system.service.OperationLogService(
                        mock(com.campus.smartorder.system.mapper.OperationLogMapper.class),
                        new com.fasterxml.jackson.databind.ObjectMapper())))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void platformAuditUsesActualAccountWithoutMerchantBinding() throws Exception {
        when(refunds.platformAudit(99L, "R1", true, "核实"))
                .thenReturn(new OrderDto.RefundView("R1", "O1", 1200, "SUCCEEDED", "", "核实", null, null, null));
        mvc.perform(platform(post("/api/v1/platform/refunds/R1/audit"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"note\":\"核实\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundAmount").value(1200));
        verify(refunds).platformAudit(99L, "R1", true, "核实");
        verify(refunds, never()).audit(any(), any(), any(), anyBoolean(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT_OWNER", "MERCHANT_STAFF", ""})
    void merchantCannotReadOrAuditPlatformOrders(String role) throws Exception {
        for (String path : List.of("orders", "orders/O1", "refunds", "refunds/R1")) {
            mvc.perform(platform(get("/api/v1/platform/" + path)).requestAttr(ConsoleAccess.ROLE_CODE, role))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(platform(post("/api/v1/platform/refunds/R1/audit;x=1"))
                        .requestAttr(ConsoleAccess.ROLE_CODE, role).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries, refunds, assembler);
    }

    @Test
    void studentCannotUsePlatformRole() throws Exception {
        mvc.perform(platform(get("/api/v1/platform/orders")).requestAttr(ConsoleAccess.SUBJECT_TYPE, "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(platform(post("/api/v1/platform/refunds/R1/audit"))
                        .requestAttr(ConsoleAccess.SUBJECT_TYPE, "USER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries, refunds);
    }

    @Test
    void passesTypedFiltersAndPagination() throws Exception {
        when(queries.orders(any())).thenReturn(PageDto.of(List.of(), 0, 2, 50));
        mvc.perform(platform(get("/api/v1/platform/orders"))
                        .param("orderNo", "O1").param("userId", "20").param("merchantId", "30")
                        .param("status", "REFUNDING").param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-18").param("page", "2").param("pageSize", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.list").isEmpty());
        var captured = ArgumentCaptor.forClass(PlatformOrderQueryService.Filter.class);
        verify(queries).orders(captured.capture());
        var filter = captured.getValue();
        assertEquals("O1", filter.orderNo());
        assertEquals(20L, filter.userId());
        assertEquals(30L, filter.merchantId());
        assertEquals("REFUNDING", filter.status());
        assertEquals(LocalDate.of(2026, 11, 1), filter.startDate());
        assertEquals(LocalDate.of(2026, 11, 18), filter.endDate());
    }

    @Test
    void exposesRefundListAndOriginalOrderDetail() throws Exception {
        var refund = new PlatformOrderQueryService.RefundRow(
                new OrderDto.RefundView("R1", "O1", 1200, "PENDING", "原因", "", null, null, null), "20", "30", "");
        when(queries.refunds(any(), eq("R1"))).thenReturn(PageDto.of(List.of(refund), 1, 1, 20));
        mvc.perform(platform(get("/api/v1/platform/refunds")).param("refundNo", "R1"))
                .andExpect(jsonPath("$.data.list[0].refund.refundNo").value("R1"));
        var order = new com.campus.smartorder.order.domain.Order();
        order.setUserId(20L);
        when(queries.refund("R1")).thenReturn(refund);
        when(queries.order("O1")).thenReturn(order);
        mvc.perform(platform(get("/api/v1/platform/refunds/R1")))
                .andExpect(jsonPath("$.data.refund.refund.orderNo").value("O1"))
                .andExpect(jsonPath("$.data.order.userId").value("20"));
        verify(assembler).view(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"approved\":null}"})
    void auditRequiresExplicitDecision(String body) throws Exception {
        mvc.perform(platform(post("/api/v1/platform/refunds/R1/audit"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(refunds);
    }

    @Test
    void rejectsInvalidRangeAndPageSize() throws Exception {
        mvc.perform(platform(get("/api/v1/platform/orders")).param("pageSize", "101"))
                .andExpect(jsonPath("$.code").value(10001));
        mvc.perform(platform(get("/api/v1/platform/refunds"))
                        .param("startDate", "2026-11-19").param("endDate", "2026-11-18"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(queries);
    }

    private MockHttpServletRequestBuilder platform(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                .requestAttr(ConsoleAccess.ROLE_CODE, "PLATFORM_ADMIN").requestAttr(ConsoleAccess.ACCOUNT_ID, 99L);
    }
}
