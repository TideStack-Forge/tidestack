package com.ouroboros.enterprise.runtime.credential;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Static deployment key ring. Values are base64-encoded 256-bit AES keys. */
@ConfigurationProperties(prefix = "ouroboros.enterprise.credentials")
public record EnterpriseCredentialKeys(String activeKeyId, Map<String, String> keys) {
  public EnterpriseCredentialKeys { keys = keys == null ? Map.of() : Map.copyOf(keys); }
  @Override public String toString() { return "EnterpriseCredentialKeys[material=****]"; }
}
