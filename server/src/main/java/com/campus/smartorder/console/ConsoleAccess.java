package com.campus.smartorder.console;

import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

public final class ConsoleAccess {

    public static final String ACCOUNT_ID = "accountId";
    public static final String ROLE_CODE = "roleCode";
    public static final String MERCHANT_ID = "merchantId";
    public static final String SUBJECT_TYPE = "subjectType";

    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_MERCHANT_OWNER = "MERCHANT_OWNER";
    public static final String ROLE_MERCHANT_STAFF = "MERCHANT_STAFF";

    private static final String MERCHANT_PREFIX = "/api/v1/merchant/";
    private static final String PLATFORM_PREFIX = "/api/v1/platform/";
    private static final String MERCHANT_ROLE_PREFIX = "MERCHANT_";

    private ConsoleAccess() {
    }

    public static void enforce(String path, String subjectType, String roleCode, String method) {
        String role = roleCode == null ? "" : roleCode;
        if (path.startsWith("/api/v1/rider/") && !JwtTokenProvider.SUBJECT_USER.equals(subjectType)) {
            throw new BizException(ErrorCode.FORBIDDEN, "请使用已审核的配送员账号登录");
        }
        if (path.startsWith("/api/v1/refunds/") && path.endsWith("/audit")) {
            if (!JwtTokenProvider.SUBJECT_ACCOUNT.equals(subjectType) || !ROLE_MERCHANT_OWNER.equals(role)) {
                throw new BizException(ErrorCode.FORBIDDEN, "退款审核请联系档口负责人");
            }
        }
        if (path.startsWith(MERCHANT_PREFIX)) {
            if (!JwtTokenProvider.SUBJECT_ACCOUNT.equals(subjectType) || !role.startsWith(MERCHANT_ROLE_PREFIX)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if (ROLE_MERCHANT_STAFF.equals(role) && !"GET".equalsIgnoreCase(method)
                    && !path.matches(".*/orders/[^/]+/(accept|start|ready|verify|reject)")) {
                throw new BizException(ErrorCode.FORBIDDEN, "店员只能处理订单，其他设置请联系老板");
            }
        }
        if (path.startsWith(PLATFORM_PREFIX)) {
            if (!JwtTokenProvider.SUBJECT_ACCOUNT.equals(subjectType) || !ROLE_PLATFORM_ADMIN.equals(role)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
    }

    public static String subjectType(HttpServletRequest request) {
        Object raw = request.getAttribute(SUBJECT_TYPE);
        return raw == null ? JwtTokenProvider.SUBJECT_USER : String.valueOf(raw);
    }

    public static Long accountId(HttpServletRequest request) {
        Object raw = request.getAttribute(ACCOUNT_ID);
        if (!(raw instanceof Long id)) {
            throw new BizException(ErrorCode.FORBIDDEN, "请使用后台账号登录");
        }
        return id;
    }

    public static String roleCode(HttpServletRequest request) {
        Object raw = request.getAttribute(ROLE_CODE);
        return raw == null ? "" : String.valueOf(raw);
    }

    public static Long merchantId(HttpServletRequest request) {
        Object raw = request.getAttribute(MERCHANT_ID);
        if (!(raw instanceof Long id)) {
            throw new BizException(ErrorCode.FORBIDDEN, "该账号没有绑定档口");
        }
        return id;
    }

    public static Long accountIdOrNull(HttpServletRequest request) {
        Object raw = request.getAttribute(ACCOUNT_ID);
        return raw instanceof Long id ? id : null;
    }

    public static String roleName(String roleCode) {
        return switch (roleCode == null ? "" : roleCode) {
            case ROLE_PLATFORM_ADMIN -> "平台管理员";
            case ROLE_MERCHANT_OWNER -> "档口负责人";
            case ROLE_MERCHANT_STAFF -> "档口员工";
            default -> roleCode == null ? "" : roleCode;
        };
    }
}
