package com.ouroboros.dev.app.model;

import java.util.ArrayList;
import java.util.List;

public class AppVcsPolicy {
  private String defaultRemote;
  private String trunkBranch;
  private List<AppVcsRemoteDefinition> remotes = new ArrayList<>();
  private String remoteName;
  private String remoteUri;
  private String defaultBranch;

  public String getDefaultRemote() {
    return defaultRemote;
  }

  public void setDefaultRemote(String defaultRemote) {
    this.defaultRemote = defaultRemote;
  }

  public String getTrunkBranch() {
    return trunkBranch;
  }

  public void setTrunkBranch(String trunkBranch) {
    this.trunkBranch = trunkBranch;
  }

  public List<AppVcsRemoteDefinition> getRemotes() {
    return remotes;
  }

  public void setRemotes(List<AppVcsRemoteDefinition> remotes) {
    this.remotes = remotes;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public void setRemoteName(String remoteName) {
    this.remoteName = remoteName;
  }

  public String getRemoteUri() {
    return remoteUri;
  }

  public void setRemoteUri(String remoteUri) {
    this.remoteUri = remoteUri;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public void setDefaultBranch(String defaultBranch) {
    this.defaultBranch = defaultBranch;
  }
}
