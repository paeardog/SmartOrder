package com.campus.smartorder.console;

import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.system.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformOperationLogControllerTest {
    private OperationLogService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(OperationLogService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformOperationLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void platformCanReadPaginatedLogs() throws Exception {
        when(service.list(1, 20, null, null)).thenReturn(new OperationLogService.Result(List.of(), 0, 1, 20));
        mvc.perform(get("/api/v1/platform/operation-logs")
                        .requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                        .requestAttr(ConsoleAccess.ROLE_CODE, "PLATFORM_ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(service).list(1, 20, null, null);
    }

    @ParameterizedTest
    @CsvSource({"USER,PLATFORM_ADMIN", "ACCOUNT,MERCHANT_OWNER", "ACCOUNT,MERCHANT_STAFF"})
    void nonPlatformPrincipalsCannotReadLogs(String subject, String role) throws Exception {
        mvc.perform(get("/api/v1/platform/operation-logs")
                        .requestAttr(ConsoleAccess.SUBJECT_TYPE, subject)
                        .requestAttr(ConsoleAccess.ROLE_CODE, role))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
