package com.ouroboros.maven.extension.dependency;

import java.util.ArrayList;
import java.util.List;

final class RuntimeManifest {
  private int schemaVersion;
  private String platform;
  private String runtime;
  private String platformVersion;
  private String generatedAt;
  private List<RuntimeManifestEntry> entries = new ArrayList<>();

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getRuntime() {
    return runtime;
  }

  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  public String getPlatformVersion() {
    return platformVersion;
  }

  public void setPlatformVersion(String platformVersion) {
    this.platformVersion = platformVersion;
  }

  public String getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(String generatedAt) {
    this.generatedAt = generatedAt;
  }

  public List<RuntimeManifestEntry> getEntries() {
    return entries;
  }

  public void setEntries(List<RuntimeManifestEntry> entries) {
    this.entries = entries;
  }
}
