package com.ouroboros.maven.extension.dependency;

import java.util.Objects;

final class ArtifactCoordinate {
  private final String groupId;
  private final String artifactId;
  private final String version;

  ArtifactCoordinate(String groupId, String artifactId, String version) {
    this.groupId = normalize(groupId);
    this.artifactId = normalize(artifactId);
    this.version = normalize(version);
  }

  String getGroupId() {
    return groupId;
  }

  String getArtifactId() {
    return artifactId;
  }

  String getVersion() {
    return version;
  }

  boolean hasGroupArtifactVersion() {
    return groupId != null && artifactId != null && version != null;
  }

  boolean hasGroupArtifact() {
    return groupId != null && artifactId != null;
  }

  String groupArtifactKey() {
    return groupId + ":" + artifactId;
  }

  String groupArtifactVersionKey() {
    return groupArtifactKey() + ":" + version;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ArtifactCoordinate)) {
      return false;
    }
    ArtifactCoordinate that = (ArtifactCoordinate) other;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(artifactId, that.artifactId)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version);
  }
}
