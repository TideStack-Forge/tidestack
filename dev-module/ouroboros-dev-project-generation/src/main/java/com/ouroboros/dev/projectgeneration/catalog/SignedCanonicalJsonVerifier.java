package com.ouroboros.dev.projectgeneration.catalog;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SignedCanonicalJsonVerifier {

  private static final String ALGORITHM = "Ed25519";

  private final Map<String, PublicKey> trustRoots;

  public SignedCanonicalJsonVerifier(Map<String, PublicKey> trustRoots) {
    this.trustRoots = Collections.unmodifiableMap(new LinkedHashMap<>(trustRoots == null ? Map.of() : trustRoots));
  }

  public VerificationResult verify(byte[] payload, byte[] envelope) {
    try {
      CanonicalJson.requireCanonical(payload);
      DetachedJsonSignature detachedSignature = DetachedJsonSignature.parse(envelope);
      if (!ALGORITHM.equals(detachedSignature.algorithm())) {
        return VerificationResult.rejected("Unsupported signature algorithm");
      }
      PublicKey publicKey = trustRoots.get(detachedSignature.keyId());
      if (publicKey == null) {
        return VerificationResult.rejected("Unknown signature key");
      }
      Signature signature = Signature.getInstance(ALGORITHM);
      signature.initVerify(publicKey);
      signature.update(payload);
      if (!signature.verify(Base64.getDecoder().decode(detachedSignature.signature()))) {
        return VerificationResult.rejected("Signature mismatch");
      }
      return VerificationResult.verified(detachedSignature.keyId());
    } catch (Exception e) {
      return VerificationResult.rejected(e.getMessage());
    }
  }

  public record VerificationResult(
      boolean verified,
      String keyId,
      String reason
  ) {
    static VerificationResult verified(String keyId) {
      return new VerificationResult(true, keyId, null);
    }

    static VerificationResult rejected(String reason) {
      return new VerificationResult(false, null, reason);
    }
  }
}
