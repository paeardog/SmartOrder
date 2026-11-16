package com.campus.smartorder.auth.service;

import com.campus.smartorder.common.config.SmartOrderProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final String issuer;

    public JwtTokenProvider(SmartOrderProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.security().issuer();
    }

    public String createAccessToken(Long userId, String phone) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(7200);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(86400 * 7);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
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
}
