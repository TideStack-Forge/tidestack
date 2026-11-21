package com.ouroboros.dev.projectgeneration.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class SignedProjectTemplateCatalogVerifier {

  private final SignedCanonicalJsonVerifier signatureVerifier;
  private final Clock clock;

  public SignedProjectTemplateCatalogVerifier(Map<String, PublicKey> trustRoots, Clock clock) {
    this.signatureVerifier = new SignedCanonicalJsonVerifier(trustRoots);
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public ProjectTemplateCatalogSnapshot verify(byte[] catalogBytes, byte[] signatureEnvelopeBytes) {
    SignedCanonicalJsonVerifier.VerificationResult verificationResult = verifySignatureOnly(
        catalogBytes,
        signatureEnvelopeBytes
    );
    if (!verificationResult.verified()) {
      throw new IllegalArgumentException("Catalog signature rejected: " + verificationResult.reason());
    }
    JsonNode catalog = CanonicalJson.parseCanonical(catalogBytes);
    Instant expiresAt = Instant.parse(requiredText(catalog, "expiresAt"));
    if (!expiresAt.isAfter(clock.instant())) {
      throw new IllegalArgumentException("Catalog is expired");
    }
    long sequence = catalog.path("sequence").asLong(-1);
    if (sequence < 1) {
      throw new IllegalArgumentException("Catalog sequence must be positive");
    }
    return new ProjectTemplateCatalogSnapshot(
        requiredText(catalog, "revision"),
        sequence,
        Instant.parse(requiredText(catalog, "issuedAt")),
        expiresAt,
        sha256Hex(catalogBytes),
        catalogBytes.clone(),
        signatureEnvelopeBytes.clone(),
        verificationResult.keyId()
    );
  }

  public SignedCanonicalJsonVerifier.VerificationResult verifySignatureOnly(byte[] catalogBytes, byte[] signatureEnvelopeBytes) {
    return signatureVerifier.verify(catalogBytes, signatureEnvelopeBytes);
  }

  private static String requiredText(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("Catalog field is missing: " + fieldName);
    }
    return value.textValue();
  }

  public static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
