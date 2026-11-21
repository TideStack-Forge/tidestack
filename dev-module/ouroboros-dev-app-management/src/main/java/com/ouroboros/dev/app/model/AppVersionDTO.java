package com.ouroboros.dev.app.model;

public class AppVersionDTO {
  private String appName;
  private String version;
  private String logicalVersion;
  private String gitTag;
  private String releaseTrack;
  private String sourceBranch;
  private String editionCode;
  private String description;
  private String createdTime;
  private String author;

  public String getAppName() {
    return appName;
  }

  public AppVersionDTO setAppName(String appName) {
    this.appName = appName;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public AppVersionDTO setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getLogicalVersion() {
    return logicalVersion;
  }

  public AppVersionDTO setLogicalVersion(String logicalVersion) {
    this.logicalVersion = logicalVersion;
    return this;
  }

  public String getGitTag() {
    return gitTag;
  }

  public AppVersionDTO setGitTag(String gitTag) {
    this.gitTag = gitTag;
    return this;
  }

  public String getReleaseTrack() {
    return releaseTrack;
  }

  public AppVersionDTO setReleaseTrack(String releaseTrack) {
    this.releaseTrack = releaseTrack;
    return this;
  }

  public String getSourceBranch() {
    return sourceBranch;
  }

  public AppVersionDTO setSourceBranch(String sourceBranch) {
    this.sourceBranch = sourceBranch;
    return this;
  }

  public String getEditionCode() {
    return editionCode;
  }

  public AppVersionDTO setEditionCode(String editionCode) {
    this.editionCode = editionCode;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public AppVersionDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getCreatedTime() {
    return createdTime;
  }

  public AppVersionDTO setCreatedTime(String createdTime) {
    this.createdTime = createdTime;
    return this;
  }

  public String getAuthor() {
    return author;
  }

  public AppVersionDTO setAuthor(String author) {
    this.author = author;
    return this;
  }
}
