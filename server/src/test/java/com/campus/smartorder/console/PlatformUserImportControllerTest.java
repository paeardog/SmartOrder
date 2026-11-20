package com.campus.smartorder.console;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.user.api.UserImportDto;
import com.campus.smartorder.user.service.UserImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformUserImportControllerTest {

    private static final String BASE = "/api/v1/platform/users/import/";
    private static final String BODY = "{\"fileName\":\"users.xlsx\",\"contentBase64\":\"sensitive-content\"}";
    private UserImportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UserImportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformUserImportController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"preview", "commit"})
    void adminCanSubmitFileAndReceiveNumericCountsWithoutPassword(String action) throws Exception {
        var row = new UserImportDto.RowResult(2, "00001234", "张三", "", "", "", "", "", "CREATE", List.of());
        var result = new UserImportDto.Result(List.of(row), 1, 1, 0, 0, action.equals("commit"));
        when(service.preview(any())).thenReturn(result);
        when(service.commit(any())).thenReturn(result);
        mvc.perform(admin(post(BASE + action).contentType(MediaType.APPLICATION_JSON).content(BODY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data.rows[0].studentNo").value("00001234"))
                .andExpect(jsonPath("$.data.committed").value(action.equals("commit")))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("sensitive-content"))));
        var request = new UserImportDto.FileRequest("users.xlsx", "sensitive-content");
        if (action.equals("preview")) verify(service).preview(request);
        else verify(service).commit(request);
    }

    @Test
    void adminCanDownloadTemplateEnvelope() throws Exception {
        when(service.template()).thenReturn(new UserImportDto.Template("用户导入模板.xlsx", "xlsx-content"));
        mvc.perform(admin(get(BASE + "template")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("用户导入模板.xlsx"))
                .andExpect(jsonPath("$.data.contentBase64").value("xlsx-content"));
    }

    @ParameterizedTest
    @CsvSource({
            "USER,PLATFORM_ADMIN", "USER,MERCHANT_OWNER", "ACCOUNT,MERCHANT_OWNER",
            "ACCOUNT,MERCHANT_STAFF", "ACCOUNT,UNKNOWN"
    })
    void rejectsNonPlatformPrincipalsOnAllRoutes(String subject, String role) throws Exception {
        for (String route : List.of("template", "preview", "commit")) {
            var request = route.equals("template") ? get(BASE + route)
                    : post(BASE + route).contentType(MediaType.APPLICATION_JSON).content(BODY);
            mvc.perform(request.requestAttr(ConsoleAccess.SUBJECT_TYPE, subject).requestAttr(ConsoleAccess.ROLE_CODE, role))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingPrincipalOnAllRoutes() throws Exception {
        mvc.perform(get(BASE + "template")).andExpect(status().isForbidden());
        for (String route : List.of("preview", "commit")) {
            mvc.perform(post(BASE + route).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidWorkbookUsesStandardApiError() throws Exception {
        when(service.preview(any())).thenThrow(new BizException(ErrorCode.INVALID_PARAM, "请上传xlsx文件"));
        mvc.perform(admin(post(BASE + "preview").contentType(MediaType.APPLICATION_JSON).content(BODY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAM.code()))
                .andExpect(jsonPath("$.message").value("请上传xlsx文件"));
    }

    @Test
    void commitValidationErrorsReturnUncommittedResult() throws Exception {
        var row = new UserImportDto.RowResult(2, "00001234", "", "", "", "", "", "", "CREATE", List.of("新增用户姓名必填"));
        when(service.commit(any())).thenReturn(new UserImportDto.Result(List.of(row), 1, 1, 0, 1, false));
        mvc.perform(admin(post(BASE + "commit").contentType(MediaType.APPLICATION_JSON).content(BODY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.committed").value(false));
    }

    @Test
    void malformedJsonReturnsSanitizedError() throws Exception {
        mvc.perform(admin(post(BASE + "preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentBase64\":\"sensitive-content\",broken}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONTENT_TYPE_UNSUPPORTED.code()))
                .andExpect(content().string(not(containsString("sensitive-content"))));
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.requestAttr(ConsoleAccess.SUBJECT_TYPE, "ACCOUNT")
                .requestAttr(ConsoleAccess.ROLE_CODE, "PLATFORM_ADMIN");
    }
}
