package com.ouroboros.dev.app.service.runtime;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.model.RuntimeAccessViewDTO;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.model.AppInfo;

@Component
public class AppRuntimeViewAssembler {
  private final DebugSessionViewAssembler debugSessionViewAssembler;
  private final RuntimeImageViewService runtimeImageViewService;

  @Autowired
  public AppRuntimeViewAssembler(DebugSessionViewAssembler debugSessionViewAssembler,
                                 RuntimeImageViewService runtimeImageViewService) {
    this.debugSessionViewAssembler = debugSessionViewAssembler;
    this.runtimeImageViewService = runtimeImageViewService;
  }

  public AppRuntimeViewAssembler(DebugSessionViewAssembler debugSessionViewAssembler) {
    this(debugSessionViewAssembler, new RuntimeImageViewService());
  }

  public AppContainersDTO assemble(AppInfo appInfo, List<RuntimeView> runtimeViews) {
    return assemble(appInfo, runtimeViews, Collections.<String>emptyList());
  }

  public AppContainersDTO assemble(AppInfo appInfo, List<RuntimeView> runtimeViews, List<String> dockerImageNames) {
    AppContainersDTO dto = new AppContainersDTO();
    dto.setAppName(appInfo.getName());
    dto.setAppTitle(appInfo.getTitle());
    dto.setAppDescription(appInfo.getDescription());
    if (runtimeViews == null || runtimeViews.isEmpty()) {
      dto.setChildren(Collections.<AppContainersDTO.Container>emptyList());
      return dto;
    }
    dto.setChildren(runtimeViews.stream()
        .map(runtimeView -> toContainer(runtimeView, dockerImageNames))
        .collect(Collectors.toList()));
    return dto;
  }

  private AppContainersDTO.Container toContainer(RuntimeView runtimeView, List<String> dockerImageNames) {
    RuntimeSpec spec = runtimeView.getSpec();
    RuntimeInstance instance = runtimeView.getInstance();
    DebugSession activeDebugSession = runtimeView.getActiveDebugSession();
    DebugSession activeBackendApiSession = runtimeView.getActiveBackendApiSession();
    RuntimeAccessViewDTO runtimeAccess = toRuntimeAccess(spec, instance, activeDebugSession, activeBackendApiSession);
    Map<String, String> backendDetails = backendDetails(instance);
    RuntimeImageViewDTO imageState = spec == null
        ? null
        : runtimeImageViewService.describe(spec.getBackendType(), spec.getImage(), dockerImageNames);
    AppContainersDTO.Container container = new AppContainersDTO.Container();
    container.setId(instance == null ? null : instance.getRuntimeId());
    container.setName(resolveName(instance));
    container.setImage(spec == null ? null : spec.getImage());
    container.setImageState(imageState);
    container.setPortBindings(buildPortBindings(instance));
    container.setVolumeBindings(Collections.<String, String>emptyMap());
    container.setEnvironmentalVariables(spec == null || spec.getEnv() == null ? Collections.<String, String>emptyMap() : spec.getEnv());
    container.setLabels(spec == null || spec.getLabels() == null ? Collections.<String, String>emptyMap() : spec.getLabels());
    container.setStatus(toContainerStatus(instance == null ? null : instance.getStatus()));
    container.setProfile(spec == null ? null : spec.getProfile());
    container.setRuntimeKind(spec == null || spec.getRuntimeKind() == null ? null : spec.getRuntimeKind().name());
    container.setRuntimeGeneration(instance == null ? null : instance.getRuntimeGeneration());
    container.setBackendType(spec == null || spec.getBackendType() == null ? null : spec.getBackendType().name());
    container.setExposureStrategy(spec == null || spec.getExposureStrategy() == null ? null : spec.getExposureStrategy().name());
    container.setBackendSummary(backendSummary(instance));
    container.setBackendDetails(backendDetails);
    container.setWebEndpoint(instance == null ? null : instance.getWebEndpoint());
    container.setApiEndpoint(instance == null ? null : instance.getApiEndpoint());
    container.setDebugEndpoint(debugEndpoint(activeDebugSession));
    container.setRuntimeAccess(runtimeAccess);
    container.setDebugCapability(runtimeAccess == null ? null : runtimeAccess.getDebugCapability());
    container.setActiveDebugSessionId(activeDebugSession == null ? null : activeDebugSession.getSessionId());
    container.setCapabilities(capabilities(instance == null ? null : instance.getCapabilities()));
    return container;
  }

  public RuntimeAccessViewDTO toRuntimeAccess(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession, DebugSession activeBackendApiSession) {
    return debugSessionViewAssembler.toRuntimeAccess(spec, instance, activeDebugSession, activeBackendApiSession);
  }

  public RuntimeAccessViewDTO toRuntimeAccess(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession) {
    return toRuntimeAccess(spec, instance, activeDebugSession, null);
  }

  public String debugEndpoint(DebugSession activeDebugSession) {
    return debugSessionViewAssembler.debugEndpoint(activeDebugSession);
  }

  private String resolveName(RuntimeInstance instance) {
    if (instance == null) {
      return null;
    }
    return Optional.ofNullable(instance.getBackendMetadata())
        .map(metadata -> metadata.get("containerName"))
        .orElse(instance.getRuntimeId());
  }

  public String backendSummary(RuntimeInstance instance) {
    Map<String, String> metadata = backendDetails(instance);
    if (metadata.containsKey("service")) {
      return "service=" + metadata.get("service");
    }
    if (metadata.containsKey("deployment")) {
      return "deployment=" + metadata.get("deployment");
    }
    if (metadata.containsKey("ingress")) {
      return "ingress=" + metadata.get("ingress");
    }
    if (metadata.containsKey("containerName")) {
      return "container=" + metadata.get("containerName");
    }
    return instance == null ? null : instance.getRuntimeId();
  }

  public Map<String, String> backendDetails(RuntimeInstance instance) {
    if (instance == null || instance.getBackendMetadata() == null || instance.getBackendMetadata().isEmpty()) {
      return Collections.emptyMap();
    }
    var visible = new LinkedHashMap<>(instance.getBackendMetadata());
    visible.remove("OUROBOROS_APP_INCARNATION_ID");
    visible.remove("OUROBOROS_DEBUG_OWNER");
    visible.remove("debugPodUid");
    return visible;
  }

  private Map<String, Integer> buildPortBindings(RuntimeInstance instance) {
    Map<String, Integer> ports = new LinkedHashMap<String, Integer>();
    putPort(ports, "web", instance == null ? null : instance.getWebEndpoint());
    putPort(ports, "api", instance == null ? null : instance.getApiEndpoint());
    return ports;
  }

  private void putPort(Map<String, Integer> ports, String key, String endpoint) {
    Integer port = extractPort(endpoint);
    if (port != null) {
      ports.put(key, port);
    }
  }

  private Integer extractPort(String endpoint) {
    if (endpoint == null || endpoint.trim().isEmpty()) {
      return null;
    }
    try {
      URI uri = URI.create(endpoint);
      return uri.getPort() > 0 ? uri.getPort() : null;
    } catch (Exception exception) {
      return null;
    }
  }

  private ContainerStatus toContainerStatus(RuntimeStatus runtimeStatus) {
    if (runtimeStatus == null) {
      return ContainerStatus.UNKNOWN;
    }
    switch (runtimeStatus) {
      case NOT_FOUND:
        return ContainerStatus.NOT_FOUND;
      case CREATING:
      case STARTING:
      case RELOADING:
        return ContainerStatus.LAUNCHING;
      case RUNNING:
        return ContainerStatus.RUNNING;
      case STOPPED:
        return ContainerStatus.STOPPED;
      default:
        return ContainerStatus.UNKNOWN;
    }
  }

  private Map<String, Boolean> capabilities(RuntimeCapability capability) {
    RuntimeCapability resolved = capability == null ? RuntimeCapability.builder().build() : capability;
    Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
    values.put("supportsPause", resolved.isSupportsPause());
    values.put("supportsDebugPort", resolved.isSupportsDebugPort());
    values.put("supportsDebugSession", resolved.isSupportsDebugSession());
    values.put("supportsWorkspaceHotReload", resolved.isSupportsWorkspaceHotReload());
    values.put("supportsNodePortExposure", resolved.isSupportsNodePortExposure());
    values.put("supportsIngressExposure", resolved.isSupportsIngressExposure());
    return values;
  }

  public static class RuntimeView {
    private final RuntimeSpec spec;
    private final RuntimeInstance instance;
    private final DebugSession activeDebugSession;
    private final DebugSession activeBackendApiSession;

    private RuntimeView(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession, DebugSession activeBackendApiSession) {
      this.spec = spec;
      this.instance = instance;
      this.activeDebugSession = activeDebugSession;
      this.activeBackendApiSession = activeBackendApiSession;
    }

    public static RuntimeView of(RuntimeSpec spec, RuntimeInstance instance) {
      return new RuntimeView(spec, instance, null, null);
    }

    public static RuntimeView of(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession) {
      return new RuntimeView(spec, instance, activeDebugSession, null);
    }

    public static RuntimeView of(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession, DebugSession activeBackendApiSession) {
      return new RuntimeView(spec, instance, activeDebugSession, activeBackendApiSession);
    }

    public RuntimeSpec getSpec() {
      return spec;
    }

    public RuntimeInstance getInstance() {
      return instance;
    }

    public DebugSession getActiveDebugSession() {
      return activeDebugSession;
    }

    public DebugSession getActiveBackendApiSession() {
      return activeBackendApiSession;
    }
  }
}
