package com.campus.smartorder.auth.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import com.campus.smartorder.user.service.PlatformUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformUserAuthenticationTest {

    private final UserMapper users = mock(UserMapper.class);
    private final OrderMapper orders = mock(OrderMapper.class);
    private final PlatformUserService service = new PlatformUserService(users, orders);
    private final ObjectMapper json = new ObjectMapper();
    private JwtTokenProvider tokens;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null,
                new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                        "platform-user-authentication-test-signing-key", "test", ""), null);
        tokens = new JwtTokenProvider(properties);
        filter = new JwtAuthenticationFilter(tokens, json, service);
    }

    @Test
    void rejectsPreviouslyIssuedAccessTokenImmediatelyAfterBan() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setStatus(User.STATUS_NORMAL);
        when(users.selectById(1L)).thenReturn(user);
        String oldAccessToken = tokens.createAccessToken(1L);
        FilterChain firstChain = mock(FilterChain.class);
        MockHttpServletRequest first = request(oldAccessToken);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, firstChain);
        verify(firstChain).doFilter(first, firstResponse);
        assertEquals(1L, first.getAttribute("userId"));

        when(users.compareAndSetStatus(1L, 1, 0, "违规")).thenReturn(1);
        service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "违规", 1));
        assertEquals("access", tokens.getTokenType(oldAccessToken));
        MockHttpServletRequest banned = request(oldAccessToken);
        MockHttpServletResponse bannedResponse = new MockHttpServletResponse();
        FilterChain blockedChain = mock(FilterChain.class);
        filter.doFilter(banned, bannedResponse, blockedChain);
        assertEquals(403, bannedResponse.getStatus());
        assertEquals(20004, json.readTree(bannedResponse.getContentAsString()).get("code").intValue());
        assertNull(banned.getAttribute("userId"));
        verifyNoInteractions(blockedChain);

        when(users.compareAndSetStatus(1L, 0, 1, "")).thenReturn(1);
        service.changeStatus(1L, new PlatformUserDto.StatusRequest(1, "", 0));
        MockHttpServletRequest unbanned = request(oldAccessToken);
        MockHttpServletResponse unbannedResponse = new MockHttpServletResponse();
        FilterChain allowedChain = mock(FilterChain.class);
        filter.doFilter(unbanned, unbannedResponse, allowedChain);
        verify(allowedChain).doFilter(unbanned, unbannedResponse);
    }

    @Test
    void rejectsTokenWhoseUserNoLongerExists() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request(tokens.createAccessToken(1L)), response, chain);
        assertEquals(401, response.getStatus());
        assertEquals(20000, json.readTree(response.getContentAsString()).get("code").intValue());
        verifyNoInteractions(chain);
    }

    @Test
    void doesNotLookUpUserForPlatformAccount() throws Exception {
        MockHttpServletRequest request = request(tokens.createAccountAccessToken(10L, "PLATFORM_ADMIN", null));
        request.setRequestURI("/api/v1/platform/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
        assertEquals(10L, request.getAttribute(ConsoleAccess.ACCOUNT_ID));
    }

    @Test
    void rejectsRefreshTokenOnProtectedEndpoint() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request(tokens.createRefreshToken(1L)), response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(users, chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/rider/login", "/api/v1/auth/password/login", "/api/v1/auth/refresh"})
    void retainsExistingPublicAuthenticationRoutes(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    void consoleAccessDoesNotAcceptPlatformRoleOnUserSubject(String method) {
        BizException error = assertThrows(BizException.class, () -> ConsoleAccess.enforce(
                "/api/v1/platform/users", "USER", "PLATFORM_ADMIN", method));
        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
