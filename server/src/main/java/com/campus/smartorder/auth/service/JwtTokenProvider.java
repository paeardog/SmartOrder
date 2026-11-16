package com.campus.smartorder.auth.service;

import com.campus.smartorder.common.config.SmartOrderProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_ACCOUNT = "ACCOUNT";

    private static final Duration LEGACY_ACCESS_TTL = Duration.ofSeconds(7200);
    private static final Duration LEGACY_REFRESH_TTL = Duration.ofDays(7);

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(SmartOrderProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.security().issuer();
        this.accessTokenTtl = properties.security().accessTokenTtl() == null
                ? LEGACY_ACCESS_TTL
                : properties.security().accessTokenTtl();
        this.refreshTokenTtl = properties.security().refreshTokenTtl() == null
                ? LEGACY_REFRESH_TTL
                : properties.security().refreshTokenTtl();
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenTtl);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .claim("subjectType", SUBJECT_USER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenTtl);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .claim("subjectType", SUBJECT_USER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String createAccountAccessToken(Long accountId, String roleCode, Long merchantId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("type", "access");
        claims.put("subjectType", SUBJECT_ACCOUNT);
        claims.put("roleCode", roleCode == null ? "" : roleCode);
        if (merchantId != null) {
            claims.put("merchantId", merchantId);
        }
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(accountId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public String createAccountRefreshToken(Long accountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(accountId))
                .claim("type", "refresh")
                .claim("subjectType", SUBJECT_ACCOUNT)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    public String getSubjectType(Claims claims) {
        String subjectType = claims.get("subjectType", String.class);
        return subjectType == null || subjectType.isBlank() ? SUBJECT_USER : subjectType;
    }

    public int accessTokenTtlSeconds() {
        return (int) accessTokenTtl.toSeconds();
    }

    public int accountAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds();
    }
}
