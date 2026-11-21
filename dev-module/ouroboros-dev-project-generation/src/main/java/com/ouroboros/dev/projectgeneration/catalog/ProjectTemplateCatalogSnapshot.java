package com.ouroboros.dev.projectgeneration.catalog;

import java.time.Instant;
import java.util.Arrays;

public record ProjectTemplateCatalogSnapshot(
    String revision,
    long sequence,
    Instant issuedAt,
    Instant expiresAt,
    String digest,
    byte[] catalogBytes,
    byte[] signatureEnvelopeBytes,
    String signatureKeyId
) {
  public ProjectTemplateCatalogSnapshot {
    if (revision == null || revision.isBlank()) {
      throw new IllegalArgumentException("Catalog revision is required");
    }
    if (sequence < 1) {
      throw new IllegalArgumentException("Catalog sequence must be positive");
    }
    if (issuedAt == null || expiresAt == null) {
      throw new IllegalArgumentException("Catalog timestamps are required");
    }
    if (digest == null || !digest.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("Catalog digest must be lowercase SHA-256 hex");
    }
    if (signatureKeyId == null || signatureKeyId.isBlank()) {
      throw new IllegalArgumentException("Catalog signature key id is required");
    }
    catalogBytes = catalogBytes == null ? new byte[0] : Arrays.copyOf(catalogBytes, catalogBytes.length);
    signatureEnvelopeBytes = signatureEnvelopeBytes == null
        ? new byte[0]
        : Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
  }

  public String catalogSha256() {
    return digest;
  }

  @Override
  public byte[] catalogBytes() {
    return Arrays.copyOf(catalogBytes, catalogBytes.length);
  }

  @Override
  public byte[] signatureEnvelopeBytes() {
    return Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
  }
}
