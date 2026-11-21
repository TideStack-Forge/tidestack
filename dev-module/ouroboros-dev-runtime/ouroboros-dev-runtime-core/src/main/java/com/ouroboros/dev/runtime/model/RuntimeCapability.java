package com.ouroboros.dev.runtime.model;

public class RuntimeCapability {
  private final boolean supportsPause;
  private final boolean supportsDebugPort;
  private final boolean supportsDebugSession;
  private final boolean supportsWorkspaceHotReload;
  private final boolean supportsNodePortExposure;
  private final boolean supportsIngressExposure;

  private RuntimeCapability(Builder builder) {
    this.supportsPause = builder.supportsPause;
    this.supportsDebugPort = builder.supportsDebugPort;
    this.supportsDebugSession = builder.supportsDebugSession;
    this.supportsWorkspaceHotReload = builder.supportsWorkspaceHotReload;
    this.supportsNodePortExposure = builder.supportsNodePortExposure;
    this.supportsIngressExposure = builder.supportsIngressExposure;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isSupportsPause() {
    return supportsPause;
  }

  public boolean isSupportsDebugPort() {
    return supportsDebugPort;
  }

  public boolean isSupportsDebugSession() {
    return supportsDebugSession;
  }

  public boolean isSupportsWorkspaceHotReload() {
    return supportsWorkspaceHotReload;
  }

  public boolean isSupportsNodePortExposure() {
    return supportsNodePortExposure;
  }

  public boolean isSupportsIngressExposure() {
    return supportsIngressExposure;
  }

  public static class Builder {
    private boolean supportsPause;
    private boolean supportsDebugPort;
    private boolean supportsDebugSession;
    private boolean supportsWorkspaceHotReload;
    private boolean supportsNodePortExposure;
    private boolean supportsIngressExposure;

    private Builder() {
    }

    public Builder supportsPause(boolean supportsPause) {
      this.supportsPause = supportsPause;
      return this;
    }

    public Builder supportsDebugPort(boolean supportsDebugPort) {
      this.supportsDebugPort = supportsDebugPort;
      return this;
    }

    public Builder supportsDebugSession(boolean supportsDebugSession) {
      this.supportsDebugSession = supportsDebugSession;
      return this;
    }

    public Builder supportsWorkspaceHotReload(boolean supportsWorkspaceHotReload) {
      this.supportsWorkspaceHotReload = supportsWorkspaceHotReload;
      return this;
    }

    public Builder supportsNodePortExposure(boolean supportsNodePortExposure) {
      this.supportsNodePortExposure = supportsNodePortExposure;
      return this;
    }

    public Builder supportsIngressExposure(boolean supportsIngressExposure) {
      this.supportsIngressExposure = supportsIngressExposure;
      return this;
    }

    public RuntimeCapability build() {
      return new RuntimeCapability(this);
    }
  }
}
