package com.ouroboros.dev.app.model;

import com.ouroboros.vcs.VCSConfig;

public class AppVcsRemoteBinding {
  private String remoteName;
  private VCSConfig.AuthType authType = VCSConfig.AuthType.NONE;
  private String username;
  private String password;
  private String sshKeyName;
  private String sshPrivateKey;
  private String sshKeyPath;

  public String getRemoteName() {
    return remoteName;
  }

  public void setRemoteName(String remoteName) {
    this.remoteName = remoteName;
  }

  public VCSConfig.AuthType getAuthType() {
    return authType;
  }

  public void setAuthType(VCSConfig.AuthType authType) {
    this.authType = authType == null ? VCSConfig.AuthType.NONE : authType;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getSshKeyName() {
    return sshKeyName;
  }

  public void setSshKeyName(String sshKeyName) {
    this.sshKeyName = sshKeyName;
  }

  public String getSshPrivateKey() {
    return sshPrivateKey;
  }

  public void setSshPrivateKey(String sshPrivateKey) {
    this.sshPrivateKey = sshPrivateKey;
  }

  public String getSshKeyPath() {
    return sshKeyPath;
  }

  public void setSshKeyPath(String sshKeyPath) {
    this.sshKeyPath = sshKeyPath;
  }
}
