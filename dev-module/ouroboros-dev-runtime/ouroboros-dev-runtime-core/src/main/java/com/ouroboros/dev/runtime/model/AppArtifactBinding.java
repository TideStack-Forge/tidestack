package com.ouroboros.dev.runtime.model;

public class AppArtifactBinding {
  private final String appId;
  private final String groupId;
  private final String artifactId;
  private final String version;
  private final String packaging;
  private final String classifier;
  private final boolean enabled;
  private final String usage;
  private final RepositoryRef repositoryRef;

  private AppArtifactBinding(Builder builder) {
    this.appId = builder.appId;
    this.groupId = builder.groupId;
    this.artifactId = builder.artifactId;
    this.version = builder.version;
    this.packaging = builder.packaging;
    this.classifier = builder.classifier;
    this.enabled = builder.enabled;
    this.usage = builder.usage;
    this.repositoryRef = builder.repositoryRef;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAppId() {
    return appId;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public String getPackaging() {
    return packaging;
  }

  public String getClassifier() {
    return classifier;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getUsage() {
    return usage;
  }

  public RepositoryRef getRepositoryRef() {
    return repositoryRef;
  }

  public static class Builder {
    private String appId;
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String classifier;
    private boolean enabled;
    private String usage;
    private RepositoryRef repositoryRef;

    private Builder() {
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder groupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder artifactId(String artifactId) {
      this.artifactId = artifactId;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public Builder packaging(String packaging) {
      this.packaging = packaging;
      return this;
    }

    public Builder classifier(String classifier) {
      this.classifier = classifier;
      return this;
    }

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder usage(String usage) {
      this.usage = usage;
      return this;
    }

    public Builder repositoryRef(RepositoryRef repositoryRef) {
      this.repositoryRef = repositoryRef;
      return this;
    }

    public AppArtifactBinding build() {
      return new AppArtifactBinding(this);
    }
  }
}
