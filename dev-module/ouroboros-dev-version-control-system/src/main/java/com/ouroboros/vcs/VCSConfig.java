package com.ouroboros.vcs;

@SuppressWarnings("unused")
public class VCSConfig {
  public enum AuthType {
    NONE,
    HTTP_BASIC,
    SSH_KEY
  }

  public static class AuthConfig {
    private AuthType authType = AuthType.NONE;
    private String username;
    private String password;
    private String sshKeyPath;

    public AuthType getAuthType() {
      return authType;
    }

    public void setAuthType(AuthType authType) {
      this.authType = authType == null ? AuthType.NONE : authType;
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

    public String getSshKeyPath() {
      return sshKeyPath;
    }

    public void setSshKeyPath(String sshKeyPath) {
      this.sshKeyPath = sshKeyPath;
    }
  }

  public static class RemoteConfig {
    private String remoteName = "origin";
    private String remoteUri;
    private String remoteBranch;

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

    public String getRemoteBranch() {
      return remoteBranch;
    }

    public void setRemoteBranch(String remoteBranch) {
      this.remoteBranch = remoteBranch;
    }
  }

  private final String repositoryPath;
  private String remoteUri;
  private String remoteName = "origin";
  private String defaultBranch = "master";
  private String sshKeyPath;
  private String username;
  private String password;
  private AuthType authType = AuthType.NONE;

  public VCSConfig(String path) {
    this.repositoryPath = path;
  }

  public String getRepositoryPath() {
    return repositoryPath;
  }

  public String getRemoteUri() {
    return remoteUri;
  }

  public void setRemoteUri(String remoteUri) {
    this.remoteUri = remoteUri;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public void setRemoteName(String remoteName) {
    this.remoteName = remoteName == null || remoteName.isEmpty() ? "origin" : remoteName;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public void setDefaultBranch(String defaultBranch) {
    this.defaultBranch = defaultBranch;
  }

  public String getSshKeyPath() {
    return sshKeyPath;
  }

  public void setSshKeyPath(String sshKeyPath) {
    this.sshKeyPath = sshKeyPath;
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

  public AuthType getAuthType() {
    return authType;
  }

  public void setAuthType(AuthType authType) {
    this.authType = authType == null ? AuthType.NONE : authType;
  }

  public AuthConfig getAuthConfig() {
    AuthConfig authConfig = new AuthConfig();
    authConfig.setAuthType(authType);
    authConfig.setUsername(username);
    authConfig.setPassword(password);
    authConfig.setSshKeyPath(sshKeyPath);
    return authConfig;
  }

  public void setAuthConfig(AuthConfig authConfig) {
    if (authConfig == null) {
      this.authType = AuthType.NONE;
      this.username = null;
      this.password = null;
      this.sshKeyPath = null;
      return;
    }
    this.authType = authConfig.getAuthType() == null ? AuthType.NONE : authConfig.getAuthType();
    this.username = authConfig.getUsername();
    this.password = authConfig.getPassword();
    this.sshKeyPath = authConfig.getSshKeyPath();
  }

  public RemoteConfig getRemoteConfig() {
    RemoteConfig config = new RemoteConfig();
    config.setRemoteName(remoteName);
    config.setRemoteUri(remoteUri);
    config.setRemoteBranch(defaultBranch);
    return config;
  }

  public void setRemoteConfig(RemoteConfig remoteConfig) {
    if (remoteConfig == null) {
      return;
    }
    this.remoteName = remoteConfig.getRemoteName() == null || remoteConfig.getRemoteName().isEmpty()
        ? "origin"
        : remoteConfig.getRemoteName();
    this.remoteUri = remoteConfig.getRemoteUri();
    if (remoteConfig.getRemoteBranch() != null) {
      this.defaultBranch = remoteConfig.getRemoteBranch();
    }
  }

}
