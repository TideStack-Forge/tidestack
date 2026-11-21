package com.ouroboros.dev.projectgeneration;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;

import com.ouroboros.dev.projectgeneration.catalog.CanonicalJson;

final class CatalogTestSupport {

  private CatalogTestSupport() {
  }

  static KeyPair ed25519KeyPair() {
    try {
      return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Ed25519 is not available", e);
    }
  }

  static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  static String signatureEnvelope(String keyId, KeyPair keyPair, byte[] payload) {
    return "{\"algorithm\":\"Ed25519\",\"keyId\":\"" + keyId + "\",\"signature\":\""
        + sign(keyPair, payload) + "\"}";
  }

  static String catalogJson(long sequence, String revision, Instant issuedAt, Instant expiresAt) {
    return "{\"contractVersion\":\"1\",\"expiresAt\":\"" + expiresAt + "\",\"issuedAt\":\"" + issuedAt
        + "\",\"revision\":\"" + revision + "\",\"sequence\":" + sequence + ",\"templates\":[]}";
  }

  static byte[] canonicalCatalog(long sequence, String revision, Instant issuedAt, Instant expiresAt) {
    return CanonicalJson.canonicalize(utf8(catalogJson(sequence, revision, issuedAt, expiresAt)));
  }

  private static String sign(KeyPair keyPair, byte[] payload) {
    try {
      Signature signature = Signature.getInstance("Ed25519");
      signature.initSign(keyPair.getPrivate());
      signature.update(payload);
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (SignatureException e) {
      throw new IllegalStateException("Cannot sign test payload", e);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot sign test payload", e);
    }
  }
}
