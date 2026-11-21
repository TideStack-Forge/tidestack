package com.ouroboros.dev.runtime.model;

public class RepositoryRef {
  private final String repositoryId;
  private final RepositoryType repositoryType;
  private final String repositoryUrl;
  private final String credentialRef;
  private final boolean defaultEnabled;

  private RepositoryRef(Builder builder) {
    this.repositoryId = builder.repositoryId;
    this.repositoryType = builder.repositoryType;
    this.repositoryUrl = builder.repositoryUrl;
    this.credentialRef = builder.credentialRef;
    this.defaultEnabled = builder.defaultEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public RepositoryType getRepositoryType() {
    return repositoryType;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public String getCredentialRef() {
    return credentialRef;
  }

  public boolean isDefaultEnabled() {
    return defaultEnabled;
  }

  public static class Builder {
    private String repositoryId;
    private RepositoryType repositoryType;
    private String repositoryUrl;
    private String credentialRef;
    private boolean defaultEnabled;

    private Builder() {
    }

    public Builder repositoryId(String repositoryId) {
      this.repositoryId = repositoryId;
      return this;
    }

    public Builder repositoryType(RepositoryType repositoryType) {
      this.repositoryType = repositoryType;
      return this;
    }

    public Builder repositoryUrl(String repositoryUrl) {
      this.repositoryUrl = repositoryUrl;
      return this;
    }

    public Builder credentialRef(String credentialRef) {
      this.credentialRef = credentialRef;
      return this;
    }

    public Builder defaultEnabled(boolean defaultEnabled) {
      this.defaultEnabled = defaultEnabled;
      return this;
    }

    public RepositoryRef build() {
      return new RepositoryRef(this);
    }
  }
}
