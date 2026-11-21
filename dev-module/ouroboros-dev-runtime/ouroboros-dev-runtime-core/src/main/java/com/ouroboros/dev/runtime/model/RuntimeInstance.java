package com.ouroboros.dev.runtime.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeInstance {
  private final String runtimeId;
  private final String workloadId;
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final Long runtimeGeneration;
  private final RuntimeStatus status;
  private final String webEndpoint;
  private final String apiEndpoint;
  private final String debugEndpoint;
  private final WorkspaceRef workspaceRef;
  private final Map<String, String> accessRef;
  private final RuntimeCapability capabilities;
  private final Map<String, String> backendMetadata;

  private RuntimeInstance(Builder builder) {
    this.runtimeId = builder.runtimeId;
    this.workloadId = builder.workloadId;
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.runtimeGeneration = builder.runtimeGeneration;
    this.status = builder.status;
    this.webEndpoint = builder.webEndpoint;
    this.apiEndpoint = builder.apiEndpoint;
    this.debugEndpoint = builder.debugEndpoint;
    this.workspaceRef = builder.workspaceRef;
    this.accessRef = immutableCopy(builder.accessRef);
    this.capabilities = builder.capabilities == null ? RuntimeCapability.builder().build() : builder.capabilities;
    this.backendMetadata = immutableCopy(builder.backendMetadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getRuntimeId() {
    return runtimeId;
  }

  public String getWorkloadId() { return workloadId; }

  public String getAppName() {
    return appName;
  }

  public String getProfile() {
    return profile;
  }

  public RuntimeKind getRuntimeKind() {
    return runtimeKind;
  }

  public BackendType getBackendType() {
    return backendType;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public RuntimeStatus getStatus() {
    return status;
  }

  public String getWebEndpoint() {
    return webEndpoint;
  }

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public String getDebugEndpoint() {
    return debugEndpoint;
  }

  public WorkspaceRef getWorkspaceRef() {
    return workspaceRef;
  }

  public Map<String, String> getAccessRef() {
    return accessRef;
  }

  public RuntimeCapability getCapabilities() {
    return capabilities;
  }

  public Map<String, String> getBackendMetadata() {
    return backendMetadata;
  }

  private static Map<String, String> immutableCopy(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
  }

  public static class Builder {
    private String runtimeId;
    private String workloadId;
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private Long runtimeGeneration;
    private RuntimeStatus status;
    private String webEndpoint;
    private String apiEndpoint;
    private String debugEndpoint;
    private WorkspaceRef workspaceRef;
    private Map<String, String> accessRef;
    private RuntimeCapability capabilities;
    private Map<String, String> backendMetadata;

    private Builder() {
    }

    public Builder runtimeId(String runtimeId) {
      this.runtimeId = runtimeId;
      return this;
    }

    public Builder workloadId(String workloadId) { this.workloadId = workloadId; return this; }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder profile(String profile) {
      this.profile = profile;
      return this;
    }

    public Builder runtimeKind(RuntimeKind runtimeKind) {
      this.runtimeKind = runtimeKind;
      return this;
    }

    public Builder backendType(BackendType backendType) {
      this.backendType = backendType;
      return this;
    }

    public Builder runtimeGeneration(Long runtimeGeneration) {
      this.runtimeGeneration = runtimeGeneration;
      return this;
    }

    public Builder status(RuntimeStatus status) {
      this.status = status;
      return this;
    }

    public Builder webEndpoint(String webEndpoint) {
      this.webEndpoint = webEndpoint;
      return this;
    }

    public Builder apiEndpoint(String apiEndpoint) {
      this.apiEndpoint = apiEndpoint;
      return this;
    }

    public Builder debugEndpoint(String debugEndpoint) {
      this.debugEndpoint = debugEndpoint;
      return this;
    }

    public Builder workspaceRef(WorkspaceRef workspaceRef) {
      this.workspaceRef = workspaceRef;
      return this;
    }

    public Builder accessRef(Map<String, String> accessRef) {
      this.accessRef = accessRef;
      return this;
    }

    public Builder capabilities(RuntimeCapability capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    public Builder backendMetadata(Map<String, String> backendMetadata) {
      this.backendMetadata = backendMetadata;
      return this;
    }

    public RuntimeInstance build() {
      return new RuntimeInstance(this);
    }
  }
}
