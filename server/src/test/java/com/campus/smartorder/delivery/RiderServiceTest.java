package com.campus.smartorder.delivery;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.delivery.domain.Rider;
import com.campus.smartorder.delivery.mapper.DeliveryMapper;
import com.campus.smartorder.delivery.mapper.RiderMapper;
import com.campus.smartorder.delivery.service.RiderService;
import com.campus.smartorder.merchant.service.OpenStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiderServiceTest {
    private final AuthService auth = mock(AuthService.class);
    private final RiderMapper riders = mock(RiderMapper.class);
    private final DeliveryMapper deliveries = mock(DeliveryMapper.class);
    private final OpenStateService clock = mock(OpenStateService.class);
    private final RiderService service = new RiderService(auth, riders, deliveries, clock);
    private Rider rider;

    @BeforeEach
    void setup() {
        rider = new Rider();
        rider.setId(7L);
        rider.setUserId(1L);
        rider.setRealName("测试配送员");
        rider.setStatus(1);
        rider.setIncomeAmount(0L);
        rider.setDeliveryCount(0L);
        rider.setRatingScore(500);
        when(riders.findByUser(1L)).thenReturn(rider);
        when(riders.lockByUser(1L)).thenReturn(rider);
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 11, 18, 12, 0));
    }

    @Test
    void ordinaryUserHasNoRiderAccess() {
        when(riders.findByUser(1L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.profile(1L));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    void rejectsUnapprovedOrDisabledRiders(int status) {
        rider.setStatus(status);
        assertThrows(BizException.class, () -> service.require(1L));
    }

    @Test
    void cannotSelfApprove() {
        rider.setStatus(0);
        assertThrows(BizException.class, () -> service.changeStatus(1L, 1));
        verify(riders, never()).changeStatus(any(), anyInt());
    }

    @Test
    void restAndResumeKeepSameIdentity() {
        assertEquals(2, service.changeStatus(1L, 2).status());
        assertEquals(1, service.changeStatus(1L, 1).status());
        verify(riders).changeStatus(7L, 2);
        verify(riders).changeStatus(7L, 1);
    }

    @Test
    void rejectsUnsupportedStatus() {
        assertThrows(BizException.class, () -> service.changeStatus(1L, 3));
        assertThrows(BizException.class, () -> service.changeStatus(1L, null));
    }

    @Test
    void returnsSessionOnlyWithRiderProfile() {
        when(auth.loginWithPassword("2022010101", "test-password")).thenReturn(Map.of(
                "accessToken", "test-access", "refreshToken", "test-refresh", "user", Map.of("userId", "1")));
        var session = service.login("2022010101", "test-password");
        assertNotNull(session.get("rider"));
        verify(auth).requireUser(1L);
    }

    @Test
    void countersRemainJsonNumbersDespiteGlobalLongIdSerialization() throws Exception {
        var properties = new com.campus.smartorder.common.config.SmartOrderProperties("Asia/Shanghai", null, null, null, null);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(
                new com.campus.smartorder.common.config.JacksonConfig().smartOrderModule(properties));
        var json = mapper.readTree(mapper.writeValueAsString(service.profile(1L)));
        assertTrue(json.get("riderId").isTextual());
        for (String field : java.util.List.of("deliveryCount", "incomeAmount", "todayCount", "todayIncome")) {
            assertTrue(json.get(field).isNumber(), field);
        }
    }

    @Test
    void accountTokensCannotAccessRiderRoutes() {
        assertThrows(BizException.class, () -> ConsoleAccess.enforce("/api/v1/rider/tasks",
                JwtTokenProvider.SUBJECT_ACCOUNT, ConsoleAccess.ROLE_PLATFORM_ADMIN, "GET"));
        assertDoesNotThrow(() -> ConsoleAccess.enforce("/api/v1/rider/profile",
                JwtTokenProvider.SUBJECT_USER, "", "GET"));
    }
}
