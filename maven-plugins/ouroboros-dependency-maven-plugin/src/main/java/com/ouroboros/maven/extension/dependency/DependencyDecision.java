package com.ouroboros.maven.extension.dependency;

final class DependencyDecision {
  private final String fileName;
  private final String groupId;
  private final String artifactId;
  private final String version;
  private final String action;
  private final String reason;
  private final String matchedVersion;
  private final String matchedCategory;

  DependencyDecision(String fileName, ArtifactCoordinate coordinate, String action, String reason,
      RuntimeManifestEntry matchedEntry) {
    this.fileName = fileName;
    this.groupId = coordinate == null ? null : coordinate.getGroupId();
    this.artifactId = coordinate == null ? null : coordinate.getArtifactId();
    this.version = coordinate == null ? null : coordinate.getVersion();
    this.action = action;
    this.reason = reason;
    this.matchedVersion = matchedEntry == null ? null : matchedEntry.getVersion();
    this.matchedCategory = matchedEntry == null ? null : matchedEntry.getCategory();
  }

  public String getFileName() {
    return fileName;
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

  public String getAction() {
    return action;
  }

  public String getReason() {
    return reason;
  }

  public String getMatchedVersion() {
    return matchedVersion;
  }

  public String getMatchedCategory() {
    return matchedCategory;
  }
}
