package com.ouroboros.security.externalauth.cas;

/**
 * SPI for CAS ticket digest algorithms.
 * <p>
 * Implementations are loaded via {@code SPI.getCachedPrimaryService(TicketDigestAlgorithm.class)},
 * with the highest {@code @Priority} value winning.
 * <p>
 * Priority convention:
 * <ul>
 *   <li>100: system built-in (e.g. HMAC-SHA-256)</li>
 *   <li>200+: custom override (e.g. GM algorithm)</li>
 * </ul>
 */
public interface TicketDigestAlgorithm {

    /**
     * Algorithm name used in cache keys (e.g. "HMAC-SHA-256").
     * Must be stable — changing this invalidates existing cache entries.
     */
    String getName();

    /**
     * Compute digest of a CAS ticket.
     *
     * @param providerKey the provider key (used as keying material)
     * @param ticket      the raw CAS ticket
     * @return hex-encoded lowercase digest string
     */
    String digest(String providerKey, String ticket);
}
