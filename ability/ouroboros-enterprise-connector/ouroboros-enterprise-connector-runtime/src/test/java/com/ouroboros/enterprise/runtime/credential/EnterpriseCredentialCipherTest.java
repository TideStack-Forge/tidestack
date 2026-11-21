package com.ouroboros.enterprise.runtime.credential;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnterpriseCredentialCipherTest {
  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private final EnterpriseCredentialCipher cipher = new EnterpriseCredentialCipher("primary", Map.of("primary", KEY));

  @Test
  void roundTripUsesFreshNoncesAndGenerationsAndKeepsSecretsOutOfDiagnostics() {
    var first = cipher.encrypt("feishu", "main", "sensitive-material");
    var second = cipher.encrypt("feishu", "main", "sensitive-material");
    assertThat(cipher.decrypt(first)).isEqualTo("sensitive-material");
    assertThat(second.nonce()).isNotEqualTo(first.nonce());
    assertThat(second.ciphertext()).isNotEqualTo(first.ciphertext());
    assertThat(second.credentialVersion()).isNotEqualTo(first.credentialVersion());
    assertThat(first.toString()).doesNotContain(first.nonce(), first.ciphertext(), "sensitive-material");
    assertThat(new EnterpriseCredentialKeys("primary", Map.of("primary", KEY)).toString()).doesNotContain(KEY);
  }

  @Test
  void identityVersionAndEncryptedBytesAreAuthenticated() {
    var encrypted = cipher.encrypt("feishu", "main", "private");
    var changes = java.util.List.of(
        new EncryptedCredential("other", encrypted.provider(), encrypted.credentialVersion(), encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext()),
        new EncryptedCredential(encrypted.connectionId(), "wecom", encrypted.credentialVersion(), encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext()),
        new EncryptedCredential(encrypted.connectionId(), encrypted.provider(), "other-version", encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext()));
    changes.forEach(record -> assertThatThrownBy(() -> cipher.decrypt(record)).hasMessageContaining("密文校验失败"));
    byte[] bytes = Base64.getDecoder().decode(encrypted.ciphertext()); bytes[0] ^= 1;
    var tampered = new EncryptedCredential(encrypted.connectionId(), encrypted.provider(), encrypted.credentialVersion(),
        encrypted.keyId(), encrypted.nonce(), Base64.getEncoder().encodeToString(bytes));
    assertThatThrownBy(() -> cipher.decrypt(tampered)).hasMessageContaining("密文校验失败").hasNoCause();
  }

  @Test
  void missingInvalidAndRetiredKeysFailWithoutFallbackOrSecretDetails() {
    var encrypted = cipher.encrypt("feishu", "main", "private");
    assertThatThrownBy(() -> new EnterpriseCredentialCipher(null, Map.of()).encrypt("feishu", "main", "private"))
        .hasMessageContaining("密钥缺失").hasMessageNotContaining("private");
    assertThatThrownBy(() -> new EnterpriseCredentialCipher("new", Map.of("new", KEY)).decrypt(encrypted))
        .hasMessageContaining("密钥缺失");
    assertThatThrownBy(() -> new EnterpriseCredentialCipher("broken", Map.of("broken", "not-base64")).encrypt("feishu", "main", "private"))
        .hasMessageContaining("密钥缺失").hasMessageNotContaining("not-base64");
    assertThatThrownBy(() -> new EnterpriseCredentialCipher("short", Map.of("short", Base64.getEncoder().encodeToString(new byte[16])))
        .encrypt("feishu", "main", "private")).hasMessageContaining("密钥缺失");
  }

  @Test
  void keyRingCanReadOldRecordsWhileWritingWithTheNewKey() {
    var original = cipher.encrypt("feishu", "main", "private");
    var rotated = new EnterpriseCredentialCipher("next", Map.of("primary", KEY, "next", KEY));
    assertThat(rotated.decrypt(original)).isEqualTo("private");
    assertThat(rotated.encrypt("feishu", "main", "private").keyId()).isEqualTo("next");
  }

  @Test
  void reencryptsWithTheNewDeploymentKeyWithoutChangingTheApplicationGeneration() {
    var original = cipher.encrypt("feishu", "main", "private");
    byte[] nextBytes = new byte[32]; nextBytes[0] = 1;
    String nextKey = Base64.getEncoder().encodeToString(nextBytes);
    var rotating = new EnterpriseCredentialCipher("next", Map.of("primary", KEY, "next", nextKey));
    var updated = rotating.reencrypt(original);
    assertThat(updated.keyId()).isEqualTo("next");
    assertThat(updated.credentialVersion()).isEqualTo(original.credentialVersion());
    assertThat(updated.nonce()).isNotEqualTo(original.nonce());
    assertThat(new EnterpriseCredentialCipher("next", Map.of("next", nextKey)).decrypt(updated)).isEqualTo("private");
    assertThat(rotating.reencrypt(updated)).isEqualTo(updated);
    assertThatThrownBy(() -> cipher.decrypt(updated)).hasMessageContaining("密钥缺失");
    assertThatThrownBy(() -> new EnterpriseCredentialCipher(null, Map.of("primary", KEY)).reencrypt(original))
        .hasMessageContaining("密钥缺失");
  }
}
