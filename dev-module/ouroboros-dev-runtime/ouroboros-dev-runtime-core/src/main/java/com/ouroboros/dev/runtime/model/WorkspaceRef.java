package com.ouroboros.dev.runtime.model;

import java.util.Objects;

public class WorkspaceRef {
  private final WorkspaceMode workspaceMode;
  private final String hostPath;
  private final String pvcName;
  private final String namespace;
  private final String subPath;

  private WorkspaceRef(Builder builder) {
    this.workspaceMode = builder.workspaceMode;
    this.hostPath = builder.hostPath;
    this.pvcName = builder.pvcName;
    this.namespace = builder.namespace;
    this.subPath = builder.subPath;
    validate();
  }

  public static Builder builder() {
    return new Builder();
  }

  public WorkspaceMode getWorkspaceMode() {
    return workspaceMode;
  }

  public String getHostPath() {
    return hostPath;
  }

  public String getPvcName() {
    return pvcName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getSubPath() {
    return subPath;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof WorkspaceRef)) {
      return false;
    }
    WorkspaceRef that = (WorkspaceRef) other;
    return workspaceMode == that.workspaceMode
        && Objects.equals(hostPath, that.hostPath)
        && Objects.equals(pvcName, that.pvcName)
        && Objects.equals(namespace, that.namespace)
        && Objects.equals(subPath, that.subPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workspaceMode, hostPath, pvcName, namespace, subPath);
  }

  public static class Builder {
    private WorkspaceMode workspaceMode;
    private String hostPath;
    private String pvcName;
    private String namespace;
    private String subPath;

    private Builder() {
    }

    public Builder workspaceMode(WorkspaceMode workspaceMode) {
      this.workspaceMode = workspaceMode;
      return this;
    }

    public Builder hostPath(String hostPath) {
      this.hostPath = hostPath;
      return this;
    }

    public Builder pvcName(String pvcName) {
      this.pvcName = pvcName;
      return this;
    }

    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public Builder subPath(String subPath) {
      this.subPath = subPath;
      return this;
    }

    public WorkspaceRef build() {
      return new WorkspaceRef(this);
    }
  }

  private void validate() {
    if (workspaceMode == null) {
      throw new IllegalArgumentException("workspaceMode is required");
    }
    switch (workspaceMode) {
      case HOST_PATH:
        if (!hasText(hostPath)) {
          throw new IllegalArgumentException("hostPath workspace requires hostPath");
        }
        if (hasText(pvcName) || hasText(namespace) || hasText(subPath)) {
          throw new IllegalArgumentException("hostPath workspace cannot declare pvc fields");
        }
        return;
      case PVC:
        if (!hasText(pvcName) || !hasText(namespace) || !hasText(subPath)) {
          throw new IllegalArgumentException("pvc workspace requires pvcName, namespace, and subPath");
        }
        if (hasText(hostPath)) {
          throw new IllegalArgumentException("pvc workspace cannot declare hostPath");
        }
        return;
      default:
        throw new IllegalArgumentException("unsupported workspace mode: " + workspaceMode);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
