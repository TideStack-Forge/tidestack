package com.ouroboros.security.externalauth.cas;

import java.nio.charset.StandardCharsets;

import jakarta.annotation.Priority;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Default HMAC-SHA-256 implementation of {@link TicketDigestAlgorithm}.
 */
@Priority(100)
public class HmacSha256TicketDigestAlgorithm implements TicketDigestAlgorithm {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public String getName() {
        return "HMAC-SHA-256";
    }

    @Override
    public String digest(String providerKey, String ticket) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    providerKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(ticket.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 computation failed", e);
        }
    }
}
