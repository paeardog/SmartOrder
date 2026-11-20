package com.campus.smartorder.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        ErrorCode ec = ex.errorCode();
        HttpStatus status = switch (ec) {
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, ACCOUNT_DISABLED, CAMPUS_NOT_BOUND -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND, ORDER_NOT_FOUND, MERCHANT_NOT_FOUND, PAYMENT_NOT_FOUND, CONFIG_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
        if (status.is5xxServerError()) {
            log.error("业务异常兜底 path={} code={}", request.getRequestURI(), ec.code(), ex);
        } else {
            log.debug("业务拒绝 path={} code={} msg={}", request.getRequestURI(), ec.code(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(ec, ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<String> fields = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.add(fe.getField() + ": " + fe.getDefaultMessage());
        }
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.INVALID_PARAM, String.join("; ", fields)));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> messages = new ArrayList<>();
        ex.getAllValidationResults().forEach(r -> r.getResolvableErrors().forEach(e -> messages.add(e.getDefaultMessage())));
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.INVALID_PARAM,
                messages.isEmpty() ? ErrorCode.INVALID_PARAM.message() : String.join("; ", messages)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<String> messages = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.INVALID_PARAM, String.join("; ", messages)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.INVALID_PARAM, "上传文件超过大小限制"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("请求体解析失败: {}", ex.getMessage());
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.CONTENT_TYPE_UNSUPPORTED, "请求体不是合法 JSON 或字段类型不匹配"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.INVALID_PARAM, "缺少参数 " + ex.getParameterName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        String detail = supported == null || supported.isEmpty()
                ? ErrorCode.METHOD_NOT_ALLOWED.message()
                : "该接口只支持 " + supported.stream().map(HttpMethod::name).sorted().collect(Collectors.joining("/"));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED, detail));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException ex) {
        log.warn("唯一约束冲突: {}", rootMessage(ex));
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.SYSTEM_ERROR, "提交的内容与已有记录重复，请勿重复操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 path={} query={}", request.getRequestURI(), request.getQueryString(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR));
    }

    private String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }
}
