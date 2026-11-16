package com.campus.smartorder.auth.service;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.console.ConsoleAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/password/login",
            "/api/v1/auth/register",
            "/api/v1/auth/sms/",
            "/api/v1/auth/cas/",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/console/login",
            "/api/v1/system/",
            "/api/v1/merchants",
            "/api/v1/pickup-points",
            "/api/v1/dishes/",
            "/api/v1/search",
            "/api/v1/events/stream"
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isWhiteListed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            String tokenType = claims.get("type", String.class);

            if (!"access".equals(tokenType)) {
                writeError(response, ErrorCode.TOKEN_INVALID);
                return;
            }

            Long subjectId = Long.parseLong(claims.getSubject());
            String subjectType = jwtTokenProvider.getSubjectType(claims);
            String roleCode = claims.get("roleCode", String.class);

            if (JwtTokenProvider.SUBJECT_ACCOUNT.equals(subjectType)) {
                request.setAttribute(ConsoleAccess.ACCOUNT_ID, subjectId);
                request.setAttribute(ConsoleAccess.ROLE_CODE, roleCode == null ? "" : roleCode);
                request.setAttribute(ConsoleAccess.SUBJECT_TYPE, subjectType);
                Long merchantId = longClaim(claims, "merchantId");
                if (merchantId != null) {
                    request.setAttribute(ConsoleAccess.MERCHANT_ID, merchantId);
                }
            } else {
                request.setAttribute(ConsoleAccess.SUBJECT_TYPE, subjectType);
                request.setAttribute("userId", subjectId);
            }

            ConsoleAccess.enforce(path, subjectType, roleCode);
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            writeError(response, ex.errorCode());
        } catch (Exception e) {
            writeError(response, ErrorCode.TOKEN_INVALID);
        }
    }

    private Long longClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(statusOf(errorCode));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }

    private int statusOf(ErrorCode errorCode) {
        return switch (errorCode) {
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID -> HttpServletResponse.SC_UNAUTHORIZED;
            case FORBIDDEN, ACCOUNT_DISABLED, CAMPUS_NOT_BOUND -> HttpServletResponse.SC_FORBIDDEN;
            default -> HttpServletResponse.SC_OK;
        };
    }
}
