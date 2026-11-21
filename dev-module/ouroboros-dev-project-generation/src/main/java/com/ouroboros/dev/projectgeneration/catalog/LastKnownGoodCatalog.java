package com.ouroboros.dev.projectgeneration.catalog;

import java.time.Instant;
import java.util.Arrays;

public record LastKnownGoodCatalog(
    byte[] catalogBytes,
    byte[] signatureEnvelopeBytes,
    Instant verifiedAt,
    Instant expiresAt,
    long sequence,
    String digest,
    String keyId
) {
  public LastKnownGoodCatalog {
    catalogBytes = catalogBytes == null ? new byte[0] : Arrays.copyOf(catalogBytes, catalogBytes.length);
    signatureEnvelopeBytes = signatureEnvelopeBytes == null
        ? new byte[0]
        : Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
    if (verifiedAt == null || expiresAt == null) {
      throw new IllegalArgumentException("LKG timestamps are required");
    }
    if (sequence < 1) {
      throw new IllegalArgumentException("LKG sequence must be positive");
    }
    if (digest == null || !digest.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("LKG digest must be lowercase SHA-256 hex");
    }
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException("LKG key id is required");
    }
  }

  @Override
  public byte[] catalogBytes() {
    return Arrays.copyOf(catalogBytes, catalogBytes.length);
  }

  @Override
  public byte[] signatureEnvelopeBytes() {
    return Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
  }

  public boolean isUsable(ProjectTemplateCatalogReplayState replayState, Instant now) {
    return replayState != null
        && now != null
        && expiresAt.isAfter(now)
        && sequence == replayState.sequence()
        && digest.equals(replayState.digest());
  }
}
