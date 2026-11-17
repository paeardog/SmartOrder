package com.campus.smartorder.order.api;

import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.order.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefundControllerTest {

    private final RefundService service = mock(RefundService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RefundController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void exposesAuditEndpointWithEnvelope() throws Exception {
        when(service.audit(30L, 40L, "R1", true, "同意"))
                .thenReturn(new OrderDto.RefundView("R1", "O1", 1200, "SUCCEEDED", "等待太久", "同意", null, null, null));
        mvc.perform(owner().content("{\"approved\":true,\"note\":\"同意\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").value("R1"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT_STAFF", "PLATFORM_ADMIN", ""})
    void rejectsNonOwnerAccounts(String role) throws Exception {
        mvc.perform(owner().requestAttr(ConsoleAccess.ROLE_CODE, role)
                        .content("{\"approved\":true,\"note\":\"\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20003));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsStaffWithMatrixParameters() throws Exception {
        mvc.perform(post("/api/v1/refunds/R1/audit;x=1").contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                        .requestAttr(ConsoleAccess.ROLE_CODE, "MERCHANT_STAFF")
                        .requestAttr(ConsoleAccess.ACCOUNT_ID, 40L)
                        .requestAttr(ConsoleAccess.MERCHANT_ID, 30L)
                        .content("{\"approved\":true,\"note\":\"\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsStudentEvenWithOwnerRole() throws Exception {
        mvc.perform(owner().requestAttr(ConsoleAccess.SUBJECT_TYPE, "USER")
                        .content("{\"approved\":true,\"note\":\"\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void requiresAccountBoundToMerchant() throws Exception {
        mvc.perform(post("/api/v1/refunds/R1/audit").contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                        .requestAttr(ConsoleAccess.ROLE_CODE, "MERCHANT_OWNER")
                        .requestAttr(ConsoleAccess.ACCOUNT_ID, 40L)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"approved\":null}", "{\"note\":\"原因\"}"})
    void requiresExplicitDecision(String body) throws Exception {
        mvc.perform(owner().content(body)).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test
    void limitsNoteLength() throws Exception {
        mvc.perform(owner().content("{\"approved\":false,\"note\":\"" + "字".repeat(256) + "\"}"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder owner() {
        return post("/api/v1/refunds/R1/audit").contentType(MediaType.APPLICATION_JSON)
                .requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                .requestAttr(ConsoleAccess.ROLE_CODE, "MERCHANT_OWNER")
                .requestAttr(ConsoleAccess.ACCOUNT_ID, 40L)
                .requestAttr(ConsoleAccess.MERCHANT_ID, 30L);
    }
}
