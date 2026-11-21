package com.ouroboros.dev.app.model;

public class AppDevHelperRequest {
  private String appName;
  private String image;
  private String backendType;

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getBackendType() {
    return backendType;
  }

  public void setBackendType(String backendType) {
    this.backendType = backendType;
  }
}
