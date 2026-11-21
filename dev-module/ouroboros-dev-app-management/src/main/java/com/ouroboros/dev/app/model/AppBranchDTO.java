package com.ouroboros.dev.app.model;

public class AppBranchDTO {
  private String branchName;
  private String displayName;
  private String releaseTrack;
  private String editionCode;
  private boolean current;
  private boolean managed;

  public String getBranchName() {
    return branchName;
  }

  public AppBranchDTO setBranchName(String branchName) {
    this.branchName = branchName;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public AppBranchDTO setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getReleaseTrack() {
    return releaseTrack;
  }

  public AppBranchDTO setReleaseTrack(String releaseTrack) {
    this.releaseTrack = releaseTrack;
    return this;
  }

  public String getEditionCode() {
    return editionCode;
  }

  public AppBranchDTO setEditionCode(String editionCode) {
    this.editionCode = editionCode;
    return this;
  }

  public boolean isCurrent() {
    return current;
  }

  public AppBranchDTO setCurrent(boolean current) {
    this.current = current;
    return this;
  }

  public boolean isManaged() {
    return managed;
  }

  public AppBranchDTO setManaged(boolean managed) {
    this.managed = managed;
    return this;
  }
}
