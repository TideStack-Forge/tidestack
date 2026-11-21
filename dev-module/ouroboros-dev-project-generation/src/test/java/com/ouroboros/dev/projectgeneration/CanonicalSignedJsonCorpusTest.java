package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.CanonicalJson;
import com.ouroboros.dev.projectgeneration.catalog.SignedCanonicalJsonVerifier;

class CanonicalSignedJsonCorpusTest {

  @Test
  void shouldCanonicalizeObjectKeysAndRejectDuplicateKeys() {
    assertArrayEquals(
        "{\"a\":1,\"b\":true}".getBytes(StandardCharsets.UTF_8),
        CanonicalJson.canonicalize(CatalogTestSupport.utf8("{\"b\":true,\"a\":1}"))
    );

    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.canonicalize(
        CatalogTestSupport.utf8("{\"a\":1,\"a\":2}")
    ));
  }

  @Test
  void shouldRejectNonCanonicalPayloadBeforeSignatureVerification() {
    var keyPair = CatalogTestSupport.ed25519KeyPair();
    byte[] nonCanonical = CatalogTestSupport.utf8("{\"b\":2,\"a\":1}");
    String envelope = CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, nonCanonical);
    SignedCanonicalJsonVerifier verifier = new SignedCanonicalJsonVerifier(Map.of(
        "catalog-key-1", keyPair.getPublic()
    ));

    assertFalse(verifier.verify(nonCanonical, CatalogTestSupport.utf8(envelope)).verified());
  }

  @Test
  void shouldVerifyEd25519AndRejectTamperAlgorithmDowngradeAndUnknownKey() {
    var keyPair = CatalogTestSupport.ed25519KeyPair();
    byte[] payload = CatalogTestSupport.utf8("{\"a\":1,\"b\":2}");
    String envelope = CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, payload);
    SignedCanonicalJsonVerifier verifier = new SignedCanonicalJsonVerifier(Map.of(
        "catalog-key-1", keyPair.getPublic()
    ));

    assertTrue(verifier.verify(payload, CatalogTestSupport.utf8(envelope)).verified());
    assertFalse(verifier.verify(CatalogTestSupport.utf8("{\"a\":1,\"b\":3}"), CatalogTestSupport.utf8(envelope)).verified());
    assertFalse(verifier.verify(payload, CatalogTestSupport.utf8(envelope.replace("Ed25519", "RSA"))).verified());
    assertFalse(verifier.verify(payload, CatalogTestSupport.utf8(envelope.replace("catalog-key-1", "missing-key"))).verified());
  }

  @Test
  void shouldAllowTrustRootRotationWhenBothPublicKeysAreConfigured() {
    var oldKeyPair = CatalogTestSupport.ed25519KeyPair();
    var newKeyPair = CatalogTestSupport.ed25519KeyPair();
    byte[] payload = CatalogTestSupport.utf8("{\"a\":1}");
    SignedCanonicalJsonVerifier verifier = new SignedCanonicalJsonVerifier(Map.<String, PublicKey>of(
        "catalog-key-old", oldKeyPair.getPublic(),
        "catalog-key-new", newKeyPair.getPublic()
    ));

    assertTrue(verifier.verify(payload, CatalogTestSupport.utf8(
        CatalogTestSupport.signatureEnvelope("catalog-key-old", oldKeyPair, payload)
    )).verified());
    assertTrue(verifier.verify(payload, CatalogTestSupport.utf8(
        CatalogTestSupport.signatureEnvelope("catalog-key-new", newKeyPair, payload)
    )).verified());
  }

  @Test
  void shouldRejectFloatingPointAndNonNfcStringsForSignedPayloads() {
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.canonicalize(CatalogTestSupport.utf8("{\"a\":1.2}")));
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.canonicalize(CatalogTestSupport.utf8("{\"a\":\"e\u0301\"}")));
  }
}
