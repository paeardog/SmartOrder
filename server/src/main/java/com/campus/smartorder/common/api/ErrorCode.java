package com.campus.smartorder.common.api;

public enum ErrorCode {

    SUCCESS(0, "ok"),

    SYSTEM_ERROR(10000, "服务开小差了，请稍后再试"),
    INVALID_PARAM(10001, "参数不正确"),
    RESOURCE_NOT_FOUND(10002, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(10003, "请求方式不支持"),
    CONTENT_TYPE_UNSUPPORTED(10004, "请求体格式不支持"),
    RATE_LIMITED(10005, "操作太频繁，请稍后再试"),

    UNAUTHORIZED(20000, "请先登录"),
    TOKEN_EXPIRED(20001, "登录已过期，请重新登录"),
    TOKEN_INVALID(20002, "登录凭证无效"),
    FORBIDDEN(20003, "没有权限执行该操作"),
    ACCOUNT_DISABLED(20004, "账号已被停用"),
    CAMPUS_NOT_BOUND(20005, "请先完成学生身份认证"),
    SMS_RATE_LIMITED(20006, "验证码发送太频繁"),
    SMS_CODE_INVALID(20007, "验证码不正确或已过期"),
    ACCOUNT_PASSWORD_INVALID(20020, "账号或密码不正确"),
    LOGIN_FAILED(20008, "学号或密码不正确"),
    STUDENT_EXISTS(20009, "该学号已注册，请直接登录"),
    STUDENT_NO_INVALID(20010, "学号格式不正确，应为 8 至 12 位数字"),
    PASSWORD_TOO_SHORT(20011, "密码至少 6 位"),
    STUDENT_NOT_VERIFIED(20012, "该学号尚未完成学籍审核，暂时无法登录"),

    MERCHANT_NOT_FOUND(30001, "档口不存在"),
    MERCHANT_CLOSED(30002, "档口当前不营业"),
    MERCHANT_NOT_APPROVED(30003, "档口未通过审核"),
    DISH_NOT_FOUND(30010, "菜品不存在或已下架"),
    DISH_OPTION_INVALID(30011, "所选规格不符合要求"),
    DISH_QUANTITY_EXCEEDED(30012, "单个菜品数量超过上限"),
    ORDER_TOO_MANY_ITEMS(30013, "一张订单的菜品数超过上限"),
    STOCK_NOT_ENOUGH(30020, "库存不够了"),
    CART_EMPTY(30030, "购物车是空的"),
    CATEGORY_NOT_EMPTY(30041, "这个分类下还有菜品，先把菜品移走"),
    DISH_NAME_DUPLICATE(30042, "菜名在本档口已经用过了，换一个"),
    ORDER_NOT_FOUND(30100, "订单不存在"),
    ORDER_NOT_OWNER(30101, "只能操作自己的订单"),
    ORDER_STATUS_ILLEGAL(30102, "订单当前状态不支持该操作"),
    ORDER_CONCURRENT_MODIFIED(30103, "订单已被其他操作更新，请刷新后重试"),
    ORDER_EXPIRED(30104, "订单已超时关闭"),
    ORDER_REFUND_NOT_ALLOWED(30105, "该订单不支持退款"),
    PICKUP_CODE_UNAVAILABLE(30106, "取餐码已用尽，请联系商家"),
    PICKUP_CODE_INVALID(30107, "取餐码不正确或已被核销"),
    IDEMPOTENCY_KEY_MISSING(30108, "缺少幂等键"),

    PAYMENT_CHANNEL_DISABLED(40001, "该支付方式未开放"),
    PAYMENT_NOT_FOUND(40002, "支付单不存在"),
    PAYMENT_AMOUNT_MISMATCH(40003, "支付金额与订单不一致"),
    PAYMENT_SIGN_INVALID(40004, "支付回调签名校验失败"),
    PAYMENT_ALREADY_PAID(40005, "订单已支付"),

    CONFIG_NOT_FOUND(50001, "配置项不存在"),
    CONFIG_INVALID(50002, "配置项取值不合法");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
