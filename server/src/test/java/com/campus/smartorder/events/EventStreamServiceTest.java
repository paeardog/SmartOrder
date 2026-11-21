package com.campus.smartorder.events;

import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.events.api.EventStreamController;
import com.campus.smartorder.events.mapper.OrderEventMapper;
import com.campus.smartorder.user.service.PlatformUserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventStreamServiceTest {

    private final PlatformUserService users = mock(PlatformUserService.class);
    private final SmartOrderProperties properties = new SmartOrderProperties("Asia/Shanghai", null, null,
            new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                    "platform-event-test-signing-key-at-least-32-bytes", "test", ""), null);
    private final EventStreamService service = new EventStreamService(mock(OrderEventMapper.class), properties, users);

    @Test
    void bannedUserCannotOpenStream() {
        ban(1L);
        assertEquals(ErrorCode.ACCOUNT_DISABLED,
                assertThrows(BizException.class, () -> service.subscribeUser(1L)).errorCode());
        assertEquals(0, service.listenerCount());
    }

    @Test
    void banClosesAllUserConnectionsBeforeSendingOrderData() {
        SseEmitter first = service.subscribeUser(1L);
        SseEmitter second = service.subscribeUser(1L);
        service.subscribeUser(2L);
        service.subscribeMerchant(10L);
        int firstEvents = pendingEvents(first);
        int secondEvents = pendingEvents(second);
        ban(1L);
        service.publishUserOrder(1L, "order-1", "READY", 10L);
        assertEquals(2, service.listenerCount());
        assertEquals(firstEvents, pendingEvents(first));
        assertEquals(secondEvents, pendingEvents(second));
        assertEquals(true, ReflectionTestUtils.getField(first, "complete"));
        assertEquals(true, ReflectionTestUtils.getField(second, "complete"));
    }

    @Test
    void heartbeatClosesIdleBannedConnectionsWithoutSendingAnotherEvent() {
        SseEmitter emitter = service.subscribeUser(1L);
        int events = pendingEvents(emitter);
        ban(1L);
        service.heartbeat();
        assertEquals(0, service.listenerCount());
        assertEquals(events, pendingEvents(emitter));
    }

    @Test
    void normalUserStillReceivesOrdersAndHeartbeat() {
        SseEmitter emitter = service.subscribeUser(1L);
        int before = pendingEvents(emitter);
        service.publishUserOrder(1L, "order-1", "READY", 10L);
        assertTrue(pendingEvents(emitter) > before);
        before = pendingEvents(emitter);
        service.heartbeat();
        assertTrue(pendingEvents(emitter) > before);
        assertEquals(1, service.listenerCount());
    }

    @Test
    void merchantStreamsDoNotLookUpStudentUsers() {
        service.subscribeMerchant(10L);
        service.publishMerchantOrder(10L, "order-1", "READY");
        service.heartbeat();
        verifyNoInteractions(users);
    }

    @Test
    void independentStreamEndpointRejectsPreviouslyIssuedUserToken() {
        JwtTokenProvider tokens = new JwtTokenProvider(properties);
        String access = tokens.createAccessToken(1L);
        ban(1L);
        EventStreamController controller = new EventStreamController(tokens, service, mock(com.campus.smartorder.console.ConsoleAccountService.class));
        SseEmitter emitter = controller.stream(access, new MockHttpServletRequest());
        assertEquals(0, service.listenerCount());
        assertEquals(true, ReflectionTestUtils.getField(emitter, "complete"));
        verify(users).requireUsable(1L);
    }

    @Test
    void accountStreamRevalidatesStatusAndScopeBeforeEveryEvent() {
        var current = new java.util.concurrent.atomic.AtomicReference<com.campus.smartorder.auth.domain.SysAccount>();
        var owner = new com.campus.smartorder.auth.domain.SysAccount();
        owner.setStatus(1);
        owner.setRoleCode("MERCHANT_OWNER");
        owner.setMerchantId(10L);
        current.set(owner);
        SseEmitter emitter = service.subscribeAccount(current::get);
        int before = pendingEvents(emitter);
        var moved = new com.campus.smartorder.auth.domain.SysAccount();
        moved.setStatus(1);
        moved.setRoleCode("MERCHANT_STAFF");
        moved.setMerchantId(20L);
        current.set(moved);
        service.publishMerchantOrder(10L, "order-1", "READY");
        assertEquals(before, pendingEvents(emitter));
        assertEquals(0, service.listenerCount());
        SseEmitter second = service.subscribeAccount(current::get);
        before = pendingEvents(second);
        moved.setStatus(0);
        service.heartbeat();
        assertEquals(before, pendingEvents(second));
        assertEquals(0, service.listenerCount());
    }

    private void ban(Long userId) {
        doThrow(new BizException(ErrorCode.ACCOUNT_DISABLED)).when(users).requireUsable(userId);
    }

    private int pendingEvents(SseEmitter emitter) {
        return ((Set<?>) ReflectionTestUtils.getField(emitter, "earlySendAttempts")).size();
    }
}
