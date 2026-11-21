package com.campus.smartorder.console;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.system.domain.OperationLog;
import com.campus.smartorder.system.mapper.OperationLogMapper;
import com.campus.smartorder.system.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationLogServiceTest {
    private final OperationLogMapper mapper = mock(OperationLogMapper.class);
    private final OperationLogService service = new OperationLogService(mapper, new ObjectMapper());

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(ConsoleAccess.ACCOUNT_ID, 1L);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void recordsActorActionAndCreatedTargetWithoutCredentials() {
        var result = service.execute(request(), "ACCOUNT_CREATE", "sys_account", null,
                () -> Map.of("accountId", "25", "password", "must-not-be-logged"));
        assertEquals("25", result.get("accountId"));
        var captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(mapper).insert(captor.capture());
        var log = captor.getValue();
        assertEquals(1L, log.getAccountId());
        assertEquals("25", log.getTargetId());
        assertEquals("ACCOUNT_CREATE", log.getAction());
        assertFalse(log.toString().contains("must-not-be-logged"));
    }

    @Test
    void doesNotRecordFailedOperations() {
        assertThrows(IllegalStateException.class, () -> service.execute(request(), "USER_CREATE", "u_user", null,
                () -> { throw new IllegalStateException(); }));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsMissingOperatorBeforeExecuting() {
        var executed = new AtomicBoolean();
        assertThrows(BizException.class, () -> service.execute(new MockHttpServletRequest(), "USER_CREATE", "u_user", null,
                () -> { executed.set(true); return null; }));
        assertFalse(executed.get());
        verifyNoInteractions(mapper);
    }

    @Test
    void propagatesLoggingFailureToTransactionalCaller() {
        doThrow(new IllegalStateException("write failed")).when(mapper).insert(any(OperationLog.class));
        assertThrows(IllegalStateException.class, () -> service.execute(request(), "USER_PASSWORD", "u_user", "2", () -> null));
    }

    @Test
    void validatesPaginationAndFilters() {
        assertThrows(BizException.class, () -> service.list(0, 20, null, null));
        assertThrows(BizException.class, () -> service.list(1, 101, null, null));
        assertThrows(BizException.class, () -> service.list(1, 20, "x".repeat(65), null));
        assertThrows(BizException.class, () -> service.list(1, 20, null, -1L));
        verifyNoInteractions(mapper);
    }
}
