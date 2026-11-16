package com.campus.smartorder.auth.service;

import com.campus.smartorder.common.config.SmartOrderProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "$pbkdf2-sha256$";
    private static final String PLACEHOLDER_PREFIX = "$argon2id$dev-placeholder";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;

    private final SmartOrderProperties properties;

    public PasswordHasher(SmartOrderProperties properties) {
        this.properties = properties;
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isEmpty() || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith(PREFIX)) {
            return matchesPbkdf2(rawPassword, storedHash);
        }
        if (storedHash.startsWith(PLACEHOLDER_PREFIX)) {
            return matchesDevPlaceholder(rawPassword);
        }
        return false;
    }

    public boolean isPlaceholder(String storedHash) {
        return storedHash != null && storedHash.startsWith(PLACEHOLDER_PREFIX);
    }

    public String encode(String rawPassword) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] digest = pbkdf2(rawPassword, salt, DEFAULT_ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + DEFAULT_ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(digest);
    }

    private boolean matchesDevPlaceholder(String rawPassword) {
        String devPassword = properties.security().devAccountPassword();
        return devPassword != null && !devPassword.isBlank() && MessageDigest.isEqual(
                devPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean matchesPbkdf2(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 5) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            return MessageDigest.isEqual(pbkdf2(rawPassword, salt, iterations), expected);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("口令校验不可用", ex);
        }
    }
}
