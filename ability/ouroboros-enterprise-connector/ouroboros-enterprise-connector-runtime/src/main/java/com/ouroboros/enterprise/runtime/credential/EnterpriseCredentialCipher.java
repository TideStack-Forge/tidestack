package com.ouroboros.enterprise.runtime.credential;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.ouroboros.exception.BusinessException;

/** Deployment keys stay outside the configuration database. Each replacement gets a fresh nonce and version. */
public final class EnterpriseCredentialCipher {
  private final String activeKeyId;
  private final Map<String, String> keys;
  private final SecureRandom random = new SecureRandom();

  public EnterpriseCredentialCipher(String activeKeyId, Map<String, String> keys) {
    this.activeKeyId = activeKeyId;
    this.keys = Map.copyOf(keys);
  }

  public EncryptedCredential encrypt(String provider, String connectionId, String payload) {
    return encrypt(provider, connectionId, payload, UUID.randomUUID().toString());
  }

  public EncryptedCredential reencrypt(EncryptedCredential record) {
    String payload = decrypt(record);
    return record.keyId().equals(activeKeyId) ? record
        : encrypt(record.provider(), record.connectionId(), payload, record.credentialVersion());
  }

  private EncryptedCredential encrypt(String provider, String connectionId, String payload, String version) {
    byte[] nonce = new byte[12];
    random.nextBytes(nonce);
    var record = new EncryptedCredential(connectionId, provider, version, activeKeyId,
        Base64.getEncoder().encodeToString(nonce), "");
    byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, record, payload.getBytes(StandardCharsets.UTF_8));
    return new EncryptedCredential(connectionId, provider, version, activeKeyId, record.nonce(),
        Base64.getEncoder().encodeToString(encrypted));
  }

  public String decrypt(EncryptedCredential record) {
    try {
      return new String(crypt(Cipher.DECRYPT_MODE, record,
          Base64.getDecoder().decode(record.ciphertext())), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw unavailable();
    }
  }

  private byte[] crypt(int mode, EncryptedCredential record, byte[] input) {
    try {
      String encodedKey = record.keyId() == null ? null : keys.get(record.keyId());
      if (encodedKey == null) { throw unavailable(); }
      byte[] key = Base64.getDecoder().decode(encodedKey);
      byte[] nonce = Base64.getDecoder().decode(record.nonce());
      if (key.length != 32 || nonce.length != 12) { throw unavailable(); }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      cipher.updateAAD((record.provider() + "\n" + record.connectionId() + "\n" + record.credentialVersion())
          .getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw unavailable();
    }
  }

  private static BusinessException unavailable() {
    return new BusinessException("[ENTERPRISE_CREDENTIAL_UNAVAILABLE] 企业凭据密钥缺失或密文校验失败");
  }
}
