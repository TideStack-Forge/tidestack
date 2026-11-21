package com.ouroboros.dev.componentlib.dto;

public class ImportedPlatformComponentLibDTO {
  private String name;
  private String publicPath;
  private String version;

  public String getName() {
    return name;
  }

  public ImportedPlatformComponentLibDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getPublicPath() {
    return publicPath;
  }

  public ImportedPlatformComponentLibDTO setPublicPath(String publicPath) {
    this.publicPath = publicPath;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public ImportedPlatformComponentLibDTO setVersion(String version) {
    this.version = version;
    return this;
  }
}
