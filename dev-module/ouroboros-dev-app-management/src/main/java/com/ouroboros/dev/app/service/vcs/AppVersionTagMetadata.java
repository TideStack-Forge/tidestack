package com.ouroboros.dev.app.service.vcs;

public class AppVersionTagMetadata {
  private String logicalVersion;
  private String gitTag;
  private String releaseTrack;
  private String sourceBranch;
  private String editionCode;
  private String description;

  public String getLogicalVersion() {
    return logicalVersion;
  }

  public void setLogicalVersion(String logicalVersion) {
    this.logicalVersion = logicalVersion;
  }

  public String getGitTag() {
    return gitTag;
  }

  public void setGitTag(String gitTag) {
    this.gitTag = gitTag;
  }

  public String getReleaseTrack() {
    return releaseTrack;
  }

  public void setReleaseTrack(String releaseTrack) {
    this.releaseTrack = releaseTrack;
  }

  public String getSourceBranch() {
    return sourceBranch;
  }

  public void setSourceBranch(String sourceBranch) {
    this.sourceBranch = sourceBranch;
  }

  public String getEditionCode() {
    return editionCode;
  }

  public void setEditionCode(String editionCode) {
    this.editionCode = editionCode;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
