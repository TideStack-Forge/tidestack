package com.ouroboros.dev.app.model;

public class AppVcsStateDTO {
  private String currentBranch;
  private String releaseTrack;
  private String sourceBranch;
  private String editionCode;
  private String remoteName;
  private String remoteUri;
  private String remoteBranch;
  private boolean workspaceClean;
  private boolean branchManaged;
  private String currentRevision;
  private String lastCommitMessage;

  public String getCurrentRevision() { return currentRevision; }

  public AppVcsStateDTO setCurrentRevision(String currentRevision) {
    this.currentRevision = currentRevision;
    return this;
  }

  public String getLastCommitMessage() { return lastCommitMessage; }

  public AppVcsStateDTO setLastCommitMessage(String lastCommitMessage) {
    this.lastCommitMessage = lastCommitMessage;
    return this;
  }

  public String getCurrentBranch() {
    return currentBranch;
  }

  public AppVcsStateDTO setCurrentBranch(String currentBranch) {
    this.currentBranch = currentBranch;
    return this;
  }

  public String getReleaseTrack() {
    return releaseTrack;
  }

  public AppVcsStateDTO setReleaseTrack(String releaseTrack) {
    this.releaseTrack = releaseTrack;
    return this;
  }

  public String getSourceBranch() {
    return sourceBranch;
  }

  public AppVcsStateDTO setSourceBranch(String sourceBranch) {
    this.sourceBranch = sourceBranch;
    return this;
  }

  public String getEditionCode() {
    return editionCode;
  }

  public AppVcsStateDTO setEditionCode(String editionCode) {
    this.editionCode = editionCode;
    return this;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public AppVcsStateDTO setRemoteName(String remoteName) {
    this.remoteName = remoteName;
    return this;
  }

  public String getRemoteUri() {
    return remoteUri;
  }

  public AppVcsStateDTO setRemoteUri(String remoteUri) {
    this.remoteUri = remoteUri;
    return this;
  }

  public String getRemoteBranch() {
    return remoteBranch;
  }

  public AppVcsStateDTO setRemoteBranch(String remoteBranch) {
    this.remoteBranch = remoteBranch;
    return this;
  }

  public boolean isWorkspaceClean() {
    return workspaceClean;
  }

  public AppVcsStateDTO setWorkspaceClean(boolean workspaceClean) {
    this.workspaceClean = workspaceClean;
    return this;
  }

  public boolean isBranchManaged() {
    return branchManaged;
  }

  public AppVcsStateDTO setBranchManaged(boolean branchManaged) {
    this.branchManaged = branchManaged;
    return this;
  }
}
