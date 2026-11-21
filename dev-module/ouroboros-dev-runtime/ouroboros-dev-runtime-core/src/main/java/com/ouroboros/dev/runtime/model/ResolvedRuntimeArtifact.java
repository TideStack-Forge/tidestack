package com.ouroboros.dev.runtime.model;

public class ResolvedRuntimeArtifact {
  private final String groupId;
  private final String artifactId;
  private final String version;
  private final String packaging;
  private final String classifier;
  private final String checksum;
  private final RepositoryRef repositoryRef;
  private final String effectiveScope;
  private final ResolvedArtifactSourceType sourceType;

  private ResolvedRuntimeArtifact(Builder builder) {
    this.groupId = builder.groupId;
    this.artifactId = builder.artifactId;
    this.version = builder.version;
    this.packaging = builder.packaging;
    this.classifier = builder.classifier;
    this.checksum = builder.checksum;
    this.repositoryRef = builder.repositoryRef;
    this.effectiveScope = builder.effectiveScope;
    this.sourceType = builder.sourceType;
  }

  public static Builder builder() {
    return new Builder();
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

  public String getChecksum() {
    return checksum;
  }

  public RepositoryRef getRepositoryRef() {
    return repositoryRef;
  }

  public String getEffectiveScope() {
    return effectiveScope;
  }

  public ResolvedArtifactSourceType getSourceType() {
    return sourceType;
  }

  public static class Builder {
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String classifier;
    private String checksum;
    private RepositoryRef repositoryRef;
    private String effectiveScope;
    private ResolvedArtifactSourceType sourceType;

    private Builder() {
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

    public Builder checksum(String checksum) {
      this.checksum = checksum;
      return this;
    }

    public Builder repositoryRef(RepositoryRef repositoryRef) {
      this.repositoryRef = repositoryRef;
      return this;
    }

    public Builder effectiveScope(String effectiveScope) {
      this.effectiveScope = effectiveScope;
      return this;
    }

    public Builder sourceType(ResolvedArtifactSourceType sourceType) {
      this.sourceType = sourceType;
      return this;
    }

    public ResolvedRuntimeArtifact build() {
      return new ResolvedRuntimeArtifact(this);
    }
  }
}
