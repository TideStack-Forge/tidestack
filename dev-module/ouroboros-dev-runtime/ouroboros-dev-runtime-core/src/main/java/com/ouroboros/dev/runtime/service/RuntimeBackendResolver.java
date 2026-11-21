package com.ouroboros.dev.runtime.service;

import java.util.List;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;

public class RuntimeBackendResolver {
  private final List<RuntimeBackend> backends;
  private final DevRuntimeProperties properties;

  public RuntimeBackendResolver(List<RuntimeBackend> backends, DevRuntimeProperties properties) {
    this.backends = backends;
    this.properties = properties;
  }

  public RuntimeBackend resolve(RuntimeSpec spec) {
    RuntimeSpec resolvedSpec = normalizeBackend(spec);
    return backends.stream()
        .filter(backend -> backend.supports(resolvedSpec))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No runtime backend supports " + resolvedSpec.getRuntimeKind() + " on " + resolvedSpec.getBackendType()));
  }

  public RuntimeSpec normalizeBackend(RuntimeSpec spec) {
    BackendType backendType = spec.getBackendType() == null ? properties.getDefaultBackend() : spec.getBackendType();
    WorkspaceMode workspaceMode = spec.getWorkspaceMode() == null ? defaultWorkspaceMode(backendType) : spec.getWorkspaceMode();
    if (backendType == spec.getBackendType() && workspaceMode == spec.getWorkspaceMode()) {
      return spec;
    }
    return RuntimeSpec.builder()
        .appName(spec.getAppName())
        .workloadId(spec.getWorkloadId())
        .profile(spec.getProfile())
        .runtimeKind(spec.getRuntimeKind())
        .backendType(backendType)
        .workspaceMode(workspaceMode)
        .exposureStrategy(spec.getExposureStrategy())
        .image(spec.getImage())
        .env(spec.getEnv())
        .labels(spec.getLabels())
        .resourceLimits(spec.getResourceLimits())
        .requestedPorts(spec.getRequestedPorts())
        .network(spec.getNetwork())
        .ttl(spec.getTtl())
        .workspaceRef(spec.getWorkspaceRef())
        .build();
  }

  private WorkspaceMode defaultWorkspaceMode(BackendType backendType) {
    return backendType == BackendType.KUBERNETES ? WorkspaceMode.PVC : WorkspaceMode.HOST_PATH;
  }
}
