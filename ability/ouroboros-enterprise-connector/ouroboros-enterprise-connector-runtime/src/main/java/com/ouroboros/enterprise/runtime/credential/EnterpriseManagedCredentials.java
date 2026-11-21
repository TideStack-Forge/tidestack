package com.ouroboros.enterprise.runtime.credential;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Authoritative database observations. Secret and generation always come from the same encrypted record. */
public final class EnterpriseManagedCredentials {
  public static final String KEY = "enterprise.credentials";
  public static final String PREFIX = "managed:";
  private final ConfigurationService configuration;
  private final EnterpriseCredentialCipher cipher;

  public EnterpriseManagedCredentials(ConfigurationService configuration, EnterpriseCredentialCipher cipher) {
    this.configuration = configuration;
    this.cipher = cipher;
  }

  public ConfigurationSnapshot snapshot() {
    var snapshot = configuration.getSnapshot(KEY).orElseThrow(() -> invalid("托管凭据配置未装配"));
    records(snapshot);
    return snapshot;
  }

  public static Map<String, EncryptedCredential> records(ConfigurationSnapshot snapshot) {
    if (snapshot.storedRawValue() != null && !snapshot.storedRawValue().isBlank()) {
      var parsed = Json.tryToMap(snapshot.storedRawValue());
      if (parsed.isFailure() || parsed.get() == null) { throw invalid("托管凭据配置损坏"); }
    }
    return records(snapshot.value());
  }

  public static Map<String, EncryptedCredential> records(Object value) {
    if (!(value instanceof Map<?, ?> input)) { throw invalid("托管凭据配置损坏"); }
    Map<String, EncryptedCredential> result = new LinkedHashMap<>();
    for (var entry : input.entrySet()) {
      if (!(entry.getKey() instanceof String id)) { throw invalid("托管凭据标识损坏"); }
      EncryptedCredential record;
      try {
        record = Json.toBean(EncryptedCredential.class, Json.toJsonString(entry.getValue()));
      } catch (RuntimeException ex) {
        throw invalid("托管凭据记录损坏");
      }
      if (record == null || !id.equals(record.connectionId()) || blank(record.provider())
          || blank(record.credentialVersion()) || blank(record.keyId()) || blank(record.nonce()) || blank(record.ciphertext())) {
        throw invalid("托管凭据记录损坏");
      }
      result.put(id, record);
    }
    return Map.copyOf(result);
  }

  public EncryptedCredential require(ConfigurationSnapshot snapshot, String connectionId, String provider) {
    EncryptedCredential record = records(snapshot).get(connectionId);
    if (record == null || !provider.equals(record.provider())) { throw invalid("托管凭据与连接不匹配"); }
    return record;
  }

  public String decrypt(EncryptedCredential record) { return cipher.decrypt(record); }

  public EncryptedCredential reencrypt(EncryptedCredential record) { return cipher.reencrypt(record); }

  public Optional<String> resolve(String reference) {
    if (reference == null || !reference.startsWith(PREFIX)) { return Optional.empty(); }
    String id = reference.substring(PREFIX.length());
    if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) { throw invalid("托管凭据引用无效"); }
    EncryptedCredential record = records(snapshot().value()).get(id);
    if (record == null) { return Optional.empty(); }
    return Optional.of(cipher.decrypt(record));
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static BusinessException invalid(String reason) {
    return new BusinessException("[ENTERPRISE_CREDENTIAL_UNAVAILABLE] " + reason);
  }
}
