package com.ouroboros.enterprise.runtime.credential;

/** Persisted only in the protected backend configuration; never returned by management reads. */
public record EncryptedCredential(String connectionId, String provider, String credentialVersion,
                                  String keyId, String nonce, String ciphertext) {
  @Override
  public String toString() {
    return "EncryptedCredential[connectionId=" + connectionId + ", provider=" + provider + ", material=****]";
  }
}
