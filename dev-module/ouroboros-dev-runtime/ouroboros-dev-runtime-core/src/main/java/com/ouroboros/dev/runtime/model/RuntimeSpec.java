package com.ouroboros.dev.runtime.model;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeSpec {
  private final String workloadId;
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final WorkspaceMode workspaceMode;
  private final ExposureStrategy exposureStrategy;
  private final Map<String, Integer> requestedPorts;
  private final String image;
  private final Map<String, String> env;
  private final Map<String, String> labels;
  private final Map<String, String> resourceLimits;
  private final Duration ttl;
  private final WorkspaceRef workspaceRef;
  private final String network;

  private RuntimeSpec(Builder builder) {
    this.workloadId = builder.workloadId;
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.workspaceMode = builder.workspaceMode;
    this.exposureStrategy = builder.exposureStrategy;
    this.requestedPorts = immutableCopy(builder.requestedPorts);
    this.image = builder.image;
    this.env = immutableCopy(builder.env);
    this.labels = immutableCopy(builder.labels);
    this.resourceLimits = immutableCopy(builder.resourceLimits);
    this.ttl = builder.ttl;
    this.workspaceRef = builder.workspaceRef;
    this.network = builder.network;
    validateWorkspaceRef(workspaceMode, workspaceRef);
    if (workspaceMode == WorkspaceMode.NONE && runtimeKind != RuntimeKind.LANGUAGE_SERVICE)
      throw new IllegalArgumentException("Only language services can omit the application workspace");
    if (runtimeKind == RuntimeKind.LANGUAGE_SERVICE && (appName != null || workloadId == null || !workloadId.matches("[a-z][a-z0-9-]{0,159}")))
      throw new IllegalArgumentException("Language services require a workload identity without appName");
  }

  public static Builder builder() {
    return new Builder();
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

  public WorkspaceMode getWorkspaceMode() {
    return workspaceMode;
  }

  public ExposureStrategy getExposureStrategy() {
    return exposureStrategy;
  }

  public Map<String, Integer> getRequestedPorts() {
    return requestedPorts;
  }

  public String getImage() {
    return image;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public Map<String, String> getResourceLimits() {
    return resourceLimits;
  }

  public Duration getTtl() {
    return ttl;
  }

  public WorkspaceRef getWorkspaceRef() {
    return workspaceRef;
  }

  public String getNetwork() {
    return network;
  }

  public RuntimeSpec withWorkspaceRef(WorkspaceRef workspaceRef) {
    return builder()
        .appName(appName)
        .workloadId(workloadId)
        .profile(profile)
        .runtimeKind(runtimeKind)
        .backendType(backendType)
        .workspaceMode(workspaceMode)
        .exposureStrategy(exposureStrategy)
        .requestedPorts(requestedPorts)
        .image(image)
        .env(env)
        .labels(labels)
        .resourceLimits(resourceLimits)
        .ttl(ttl)
        .workspaceRef(workspaceRef)
        .network(network)
        .build();
  }

  public RuntimeSpec withEnv(Map<String, String> env) {
    return builder()
        .appName(appName)
        .workloadId(workloadId)
        .profile(profile)
        .runtimeKind(runtimeKind)
        .backendType(backendType)
        .workspaceMode(workspaceMode)
        .exposureStrategy(exposureStrategy)
        .requestedPorts(requestedPorts)
        .image(image)
        .env(env)
        .labels(labels)
        .resourceLimits(resourceLimits)
        .ttl(ttl)
        .workspaceRef(workspaceRef)
        .network(network)
        .build();
  }

  private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
  }

  public static class Builder {
    private String workloadId;
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private WorkspaceMode workspaceMode;
    private ExposureStrategy exposureStrategy;
    private Map<String, Integer> requestedPorts;
    private String image;
    private Map<String, String> env;
    private Map<String, String> labels;
    private Map<String, String> resourceLimits;
    private Duration ttl;
    private WorkspaceRef workspaceRef;
    private String network;

    private Builder() {
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

    public Builder workspaceMode(WorkspaceMode workspaceMode) {
      this.workspaceMode = workspaceMode;
      return this;
    }

    public Builder exposureStrategy(ExposureStrategy exposureStrategy) {
      this.exposureStrategy = exposureStrategy;
      return this;
    }

    public Builder requestedPorts(Map<String, Integer> requestedPorts) {
      this.requestedPorts = requestedPorts;
      return this;
    }

    public Builder image(String image) {
      this.image = image;
      return this;
    }

    public Builder env(Map<String, String> env) {
      this.env = env;
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public Builder resourceLimits(Map<String, String> resourceLimits) {
      this.resourceLimits = resourceLimits;
      return this;
    }

    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder workspaceRef(WorkspaceRef workspaceRef) {
      this.workspaceRef = workspaceRef;
      return this;
    }

    public Builder network(String network) {
      this.network = network;
      return this;
    }

    public RuntimeSpec build() {
      return new RuntimeSpec(this);
    }
  }

  private static void validateWorkspaceRef(WorkspaceMode workspaceMode, WorkspaceRef workspaceRef) {
    if (workspaceRef == null) {
      return;
    }
    if (workspaceMode == null) {
      throw new IllegalArgumentException("workspaceMode is required when workspaceRef is set");
    }
    if (workspaceMode != workspaceRef.getWorkspaceMode()) {
      throw new IllegalArgumentException("workspaceRef mode must match runtime spec workspaceMode");
    }
  }
}
