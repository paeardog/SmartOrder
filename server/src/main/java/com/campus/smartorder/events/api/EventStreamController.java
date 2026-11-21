package com.campus.smartorder.events.api;

import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.events.EventStreamService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventStreamController {

    private final JwtTokenProvider jwtTokenProvider;
    private final EventStreamService eventStreamService;

    private final com.campus.smartorder.console.ConsoleAccountService accounts;

    public EventStreamController(JwtTokenProvider jwtTokenProvider, EventStreamService eventStreamService,
                                 com.campus.smartorder.console.ConsoleAccountService accounts) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.eventStreamService = eventStreamService;
        this.accounts = accounts;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String token, HttpServletRequest request) {
        String raw = token;
        if (raw == null || raw.isBlank()) {
            String header = request.getHeader("Authorization");
            raw = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        }
        if (raw == null || raw.isBlank()) {
            return rejected("缺少登录凭证");
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(raw);
        } catch (RuntimeException ex) {
            return rejected("登录凭证无效");
        }
        if (!"access".equals(claims.get("type", String.class))) {
            return rejected("登录凭证无效");
        }

        try {
            if (JwtTokenProvider.SUBJECT_ACCOUNT.equals(jwtTokenProvider.getSubjectType(claims))) {
                Long accountId = Long.parseLong(claims.getSubject());
                return eventStreamService.subscribeAccount(() -> accounts.require(accountId));
            }
            return eventStreamService.subscribeUser(Long.parseLong(claims.getSubject()));
        } catch (BizException ex) {
            return rejected(ex.getMessage());
        }
    }

    private SseEmitter rejected(String reason) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("ping").data(Map.of("error", reason)));
            emitter.complete();
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

}
