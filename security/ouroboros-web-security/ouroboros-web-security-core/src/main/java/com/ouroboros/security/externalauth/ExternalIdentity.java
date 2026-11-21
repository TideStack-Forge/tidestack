package com.ouroboros.security.externalauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * Normalized external identity from any external auth provider.
 * Immutable value object — input to ExternalAuthService.
 */
public class ExternalIdentity implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String providerKey;
  private final String externalId;
  private final String displayName;
  private final String email;
  private final Map<String, Object> attributes;

  public ExternalIdentity(String providerKey, String externalId, String displayName, String email,
                           Map<String, Object> attributes) {
    this.providerKey = providerKey;
    this.externalId = externalId;
    this.displayName = displayName;
    this.email = email;
    this.attributes = attributes != null ? Collections.unmodifiableMap(new HashMap<>(attributes)) : Collections.emptyMap();
  }

  public static ExternalIdentity of(String providerKey, String externalId, String displayName, String email,
                                     Map<String, Object> attributes) {
    return new ExternalIdentity(providerKey, externalId, displayName, email, attributes);
  }

  public String getProviderKey() { return providerKey; }
  public String getExternalId() { return externalId; }
  public String getDisplayName() { return displayName; }
  public String getEmail() { return email; }
  public Map<String, Object> getAttributes() { return attributes; }
}
