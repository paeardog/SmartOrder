package com.campus.smartorder.console;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import com.campus.smartorder.merchant.service.MerchantStatsService;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PlatformMerchantService;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformControllerTest {

    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final MerchantProfileService profiles = mock(MerchantProfileService.class);
    private final OpenStateService openState = mock(OpenStateService.class);
    private MockMvc mvc;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        PlatformService service = new PlatformService(new PlatformMerchantService(merchants, profiles),
                mock(DishMapper.class), mock(OrderMapper.class), mock(PickupPointMapper.class),
                mock(SysConfigMapper.class), openState, mock(MerchantStatsService.class), profiles,
                new SmartOrderProperties("Asia/Shanghai", null, null, null, null), new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new PlatformController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        merchant = new Merchant();
        merchant.setId(10L);
        merchant.setName("档口");
        merchant.setStatus(1);
        merchant.setRejectReason("");
        when(profiles.require(10L)).thenReturn(merchant);
        when(openState.statesOf(eq(List.of(10L)), any())).thenReturn(Map.of());
    }

    @Test
    void exposesPatchStatusWithAuditRowEnvelope() throws Exception {
        when(merchants.casStatus(10L, 1, 3, null)).thenReturn(1);
        mvc.perform(patch("/api/v1/platform/merchants/10/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":3,\"expectedStatus\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.merchantId").value("10"))
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.rejectReason").value(""));
        verify(merchants).casStatus(10L, 1, 3, null);
        verifyNoMoreInteractions(merchants);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":2}", "{\"expectedStatus\":1}",
            "{\"status\":null,\"expectedStatus\":1}", "{\"status\":2,\"expectedStatus\":null}",
            "{\"status\":0,\"expectedStatus\":1}", "{\"status\":4,\"expectedStatus\":1}",
            "{\"status\":1,\"expectedStatus\":0}", "{\"status\":1,\"expectedStatus\":4}",
            "{\"status\":1,\"expectedStatus\":1}", "{\"status\":2,\"expectedStatus\":3}"})
    void refusesInvalidPatchBody(String body) throws Exception {
        mvc.perform(patch("/api/v1/platform/merchants/10/status")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(merchants, profiles);
    }

    @Test
    void exposesCasConflictWithoutSuccessRow() throws Exception {
        mvc.perform(patch("/api/v1/platform/merchants/10/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":2,\"expectedStatus\":1}"))
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("档口状态已变化，请刷新后重试"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectAndListExposeAuditReason() throws Exception {
        merchant.setStatus(0);
        when(merchants.casStatus(10L, 0, 4, "资料不全")).thenReturn(1);
        mvc.perform(post("/api/v1/platform/merchants/10/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" 资料不全 \"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.rejectReason").value("资料不全"));
        when(merchants.selectList(any())).thenReturn(List.of(merchant));
        mvc.perform(get("/api/v1/platform/merchants").param("status", "4"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].rejectReason").value("资料不全"));
    }

    @Test
    void approveClearsReasonInResponse() throws Exception {
        merchant.setStatus(4);
        merchant.setRejectReason("资料不全");
        when(merchants.casStatus(10L, 4, 1, "")).thenReturn(1);
        mvc.perform(post("/api/v1/platform/merchants/10/approve"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.rejectReason").value(""));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5})
    void refusesOutOfRangeListFilter(int status) throws Exception {
        mvc.perform(get("/api/v1/platform/merchants").param("status", String.valueOf(status)))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(merchants);
    }

    @ParameterizedTest
    @CsvSource({"ACCOUNT,MERCHANT_OWNER", "ACCOUNT,MERCHANT_STAFF", "USER,PLATFORM_ADMIN"})
    void existingAccessPolicyRestrictsPatchToPlatformAccounts(String subject, String role) {
        BizException ex = assertThrows(BizException.class, () -> ConsoleAccess.enforce(
                "/api/v1/platform/merchants/10/status", subject, role, "PATCH"));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertDoesNotThrow(() -> ConsoleAccess.enforce(
                "/api/v1/platform/merchants/10/status", "ACCOUNT", "PLATFORM_ADMIN", "PATCH"));
    }
}
