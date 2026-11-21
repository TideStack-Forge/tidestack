package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

class SignedProjectTemplateCatalogTest {

  @Test
  void shouldVerifyCanonicalSignedCatalogAndExposeSnapshotIdentity() {
    var keyPair = CatalogTestSupport.ed25519KeyPair();
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    byte[] catalog = CatalogTestSupport.canonicalCatalog(
        42,
        "cat_42_abcd",
        now.minusSeconds(60),
        now.plusSeconds(3600)
    );
    String envelope = CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, catalog);

    var verifier = new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        Clock.fixed(now, ZoneOffset.UTC)
    );
    var snapshot = verifier.verify(catalog, CatalogTestSupport.utf8(envelope));

    assertEquals("cat_42_abcd", snapshot.revision());
    assertEquals(42, snapshot.sequence());
    assertEquals(snapshot.digest(), snapshot.catalogSha256());
  }

  @Test
  void shouldRejectExpiredCatalogAndTamperedSignature() {
    var keyPair = CatalogTestSupport.ed25519KeyPair();
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    byte[] expired = CatalogTestSupport.canonicalCatalog(
        42,
        "cat_42_abcd",
        now.minusSeconds(3600),
        now.minusSeconds(60)
    );
    String envelope = CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, expired);
    var verifier = new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        Clock.fixed(now, ZoneOffset.UTC)
    );

    assertThrows(IllegalArgumentException.class, () -> verifier.verify(expired, CatalogTestSupport.utf8(envelope)));
    assertFalse(verifier.verifySignatureOnly(
        CatalogTestSupport.utf8("{\"contractVersion\":\"1\",\"expiresAt\":\"2026-07-30T01:00:00Z\",\"issuedAt\":\"2026-07-29T23:59:00Z\",\"revision\":\"cat_42_abcd\",\"sequence\":43,\"templates\":[]}"),
        CatalogTestSupport.utf8(envelope)
    ).verified());
  }

  @Test
  void shouldRejectNonCanonicalCatalogBeforeParsing() {
    var keyPair = CatalogTestSupport.ed25519KeyPair();
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    byte[] nonCanonical = CatalogTestSupport.utf8(CatalogTestSupport.catalogJson(
        42,
        "cat_42_abcd",
        now.minusSeconds(60),
        now.plusSeconds(3600)
    ).replace("\"contractVersion\"", "\"z\""));
    String envelope = CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, nonCanonical);
    var verifier = new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        Clock.fixed(now, ZoneOffset.UTC)
    );

    assertThrows(IllegalArgumentException.class, () -> verifier.verify(nonCanonical, CatalogTestSupport.utf8(envelope)));
  }
}
