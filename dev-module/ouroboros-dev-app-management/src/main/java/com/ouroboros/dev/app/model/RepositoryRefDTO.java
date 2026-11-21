package com.ouroboros.dev.app.model;

public class RepositoryRefDTO {
  private String repositoryId;
  private String repositoryType;
  private String repositoryUrl;
  private String credentialRef;
  private boolean defaultEnabled;

  public String getRepositoryId() {
    return repositoryId;
  }

  public void setRepositoryId(String repositoryId) {
    this.repositoryId = repositoryId;
  }

  public String getRepositoryType() {
    return repositoryType;
  }

  public void setRepositoryType(String repositoryType) {
    this.repositoryType = repositoryType;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public void setRepositoryUrl(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl;
  }

  public String getCredentialRef() {
    return credentialRef;
  }

  public void setCredentialRef(String credentialRef) {
    this.credentialRef = credentialRef;
  }

  public boolean isDefaultEnabled() {
    return defaultEnabled;
  }

  public void setDefaultEnabled(boolean defaultEnabled) {
    this.defaultEnabled = defaultEnabled;
  }
}
