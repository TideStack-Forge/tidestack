package com.ouroboros.security.externalauth;

import java.security.SecureRandom;
import java.util.Base64;

/** Secure random tokens shared by authentication providers. */
public final class SecurityTokens {
    private SecurityTokens() {
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateSecureToken() {
        byte[] bytes = new byte[16]; // 128 bits
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static String generatePkceCodeVerifier() {
        byte[] bytes = new byte[32]; // 256 bits -> 43 char base64url without padding
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate a short correlationId for tracing an auth flow through log lines.
     * 64 bits of entropy — sufficient for logging traceability (not a security token).
     */
    public static String generateCorrelationId() {
        byte[] bytes = new byte[8];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

}
