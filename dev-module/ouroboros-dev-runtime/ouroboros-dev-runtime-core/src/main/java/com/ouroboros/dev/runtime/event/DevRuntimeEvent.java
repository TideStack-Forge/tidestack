package com.ouroboros.dev.runtime.event;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceRef;

public class DevRuntimeEvent {
  private final DevRuntimeEventType type;
  private final String runtimeId;
  private final String workloadId;
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final ExposureStrategy exposureStrategy;
  private final Long runtimeGeneration;
  private final RuntimeStatus status;
  private final String webEndpoint;
  private final String apiEndpoint;
  private final String debugEndpoint;
  private final WorkspaceRef workspaceRef;
  private final Map<String, String> accessRef;
  private final RuntimeCapability capabilities;
  private final Map<String, String> backendMetadata;
  private final List<String> logLines;
  private final String failureCode;
  private final String failureMessage;

  private DevRuntimeEvent(Builder builder) {
    this.type = Objects.requireNonNull(builder.type, "type must not be null");
    this.runtimeId = builder.runtimeId;
    this.workloadId = builder.workloadId;
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.exposureStrategy = builder.exposureStrategy;
    this.runtimeGeneration = builder.runtimeGeneration;
    this.status = builder.status;
    this.webEndpoint = builder.webEndpoint;
    this.apiEndpoint = builder.apiEndpoint;
    this.debugEndpoint = builder.debugEndpoint;
    this.workspaceRef = builder.workspaceRef;
    this.accessRef = immutableCopy(builder.accessRef);
    this.capabilities = builder.capabilities == null ? RuntimeCapability.builder().build() : builder.capabilities;
    this.backendMetadata = immutableCopy(builder.backendMetadata);
    this.logLines = immutableCopyList(builder.logLines);
    this.failureCode = builder.failureCode;
    this.failureMessage = builder.failureMessage;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static DevRuntimeEvent from(DevRuntimeEventType type, RuntimeInstance runtime) {
    Objects.requireNonNull(runtime, "runtime must not be null");
    return builder()
        .type(type)
        .runtimeId(runtime.getRuntimeId())
        .appName(runtime.getAppName())
        .workloadId(runtime.getWorkloadId())
        .profile(runtime.getProfile())
        .runtimeKind(runtime.getRuntimeKind())
        .backendType(runtime.getBackendType())
        .runtimeGeneration(runtime.getRuntimeGeneration())
        .status(runtime.getStatus())
        .webEndpoint(runtime.getWebEndpoint())
        .apiEndpoint(runtime.getApiEndpoint())
        .debugEndpoint(runtime.getDebugEndpoint())
        .workspaceRef(runtime.getWorkspaceRef())
        .accessRef(runtime.getAccessRef())
        .capabilities(runtime.getCapabilities())
        .backendMetadata(runtime.getBackendMetadata())
        .build();
  }

  public DevRuntimeEventType getType() {
    return type;
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

  public ExposureStrategy getExposureStrategy() {
    return exposureStrategy;
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

  public List<String> getLogLines() {
    return logLines;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  private static Map<String, String> immutableCopy(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
  }

  private static List<String> immutableCopyList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  public static class Builder {
    private DevRuntimeEventType type;
    private String runtimeId;
    private String workloadId;
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private ExposureStrategy exposureStrategy;
    private Long runtimeGeneration;
    private RuntimeStatus status;
    private String webEndpoint;
    private String apiEndpoint;
    private String debugEndpoint;
    private WorkspaceRef workspaceRef;
    private Map<String, String> accessRef;
    private RuntimeCapability capabilities;
    private Map<String, String> backendMetadata;
    private List<String> logLines;
    private String failureCode;
    private String failureMessage;

    private Builder() {
    }

    public Builder type(DevRuntimeEventType type) {
      this.type = type;
      return this;
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

    public Builder exposureStrategy(ExposureStrategy exposureStrategy) {
      this.exposureStrategy = exposureStrategy;
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

    public Builder logLines(List<String> logLines) {
      this.logLines = logLines;
      return this;
    }

    public Builder failureCode(String failureCode) {
      this.failureCode = failureCode;
      return this;
    }

    public Builder failureMessage(String failureMessage) {
      this.failureMessage = failureMessage;
      return this;
    }

    public DevRuntimeEvent build() {
      return new DevRuntimeEvent(this);
    }
  }
}
