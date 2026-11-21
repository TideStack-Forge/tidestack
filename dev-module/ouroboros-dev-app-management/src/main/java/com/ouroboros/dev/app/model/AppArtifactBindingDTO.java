package com.ouroboros.dev.app.model;

public class AppArtifactBindingDTO {
  private String groupId;
  private String artifactId;
  private String version;
  private String packaging;
  private String classifier;
  private boolean enabled;
  private String usage;
  private RepositoryRefDTO repositoryRef;

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getPackaging() {
    return packaging;
  }

  public void setPackaging(String packaging) {
    this.packaging = packaging;
  }

  public String getClassifier() {
    return classifier;
  }

  public void setClassifier(String classifier) {
    this.classifier = classifier;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }

  public RepositoryRefDTO getRepositoryRef() {
    return repositoryRef;
  }

  public void setRepositoryRef(RepositoryRefDTO repositoryRef) {
    this.repositoryRef = repositoryRef;
  }
}
