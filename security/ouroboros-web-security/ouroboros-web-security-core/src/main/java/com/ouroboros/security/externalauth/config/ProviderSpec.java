package com.ouroboros.security.externalauth.config;

/**
 * Base configuration for an external auth provider.
 * Plain data object, no Spring dependency.
 */
public class ProviderSpec {

  private String key;
  private String type;
  private String displayName;
  private boolean enabled = true;
  /** True when the provider key is a connectionId rather than a static provider key. */
  private boolean connectionScoped = false;
  /** Immutable revision of the connection snapshot that derived this profile. */
  private String connectionProfileRevision;
  /** Revision of the unified channel configuration used to initiate a browser flow. */
  private String configurationRevision;

  public String getConfigurationRevision() { return configurationRevision; }
  public void setConfigurationRevision(String revision) { configurationRevision = revision; }

  public String getKey() { return key; }
  public void setKey(String key) { this.key = key; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public boolean isConnectionScoped() { return connectionScoped; }
  public void setConnectionScoped(boolean connectionScoped) { this.connectionScoped = connectionScoped; }

  public String getConnectionProfileRevision() { return connectionProfileRevision; }
  public void setConnectionProfileRevision(String connectionProfileRevision) {
    this.connectionProfileRevision = connectionProfileRevision;
  }
}
