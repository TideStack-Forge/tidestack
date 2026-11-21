package com.ouroboros.dev.runtime.docker.mapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

public final class DockerRuntimeMapper {
  public static final String TEST_APP_LABEL = "ouroboros-test-app";
  public static final String DEV_HELPER_LABEL = "ouroboros-dev-helper";
  public static final String APP_NAME_LABEL = "OUROBOROS_APP_NAME";
  public static final String PROFILE_LABEL = "OUROBOROS_APP_PROFILE";
  public static final String WEB_PORT_LABEL = "WEB_PORT";
  public static final String API_PORT_LABEL = "API_PORT";
  public static final String API_DEBUG_PORT_LABEL = "API_DEBUG_PORT";
  public static final String DEV_HELPER_PROFILE = "dev-helper";
  private static final String LOOPBACK = "127.0.0.1";

  private DockerRuntimeMapper() {
  }

  public static RuntimeInstance toRuntime(Container container, RuntimeKind runtimeKind) {
    return toRuntime(container, runtimeKind, null, null);
  }

  public static RuntimeInstance toRuntime(Container container, RuntimeKind runtimeKind, Long runtimeGeneration, RuntimeStatus statusOverride) {
    Map<String, String> labels = labelsOf(container);
    Integer webPort = resolvePort(container, WEB_PORT_LABEL, 80);
    Integer apiPort = resolvePort(container, API_PORT_LABEL, 8080);
    Integer debugPort = resolvePort(container, API_DEBUG_PORT_LABEL, 5005);
    RuntimeStatus runtimeStatus = statusOverride == null ? toRuntimeStatus(container == null ? null : container.getStatus()) : statusOverride;
    return RuntimeInstance.builder()
        .runtimeId(runtimeId(container))
        .appName(text(labels.get(APP_NAME_LABEL)))
        .profile(profileOf(runtimeKind, labels))
        .runtimeKind(runtimeKind)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(runtimeGeneration)
        .status(runtimeStatus)
        .webEndpoint(httpEndpoint(webPort))
        .apiEndpoint(httpEndpoint(apiPort))
        .debugEndpoint(null)
        .accessRef(accessRef(webPort, apiPort, debugPort))
        .capabilities(capabilitiesFor(runtimeKind))
        .backendMetadata(backendMetadata(container))
        .build();
  }

  private static Map<String, String> backendMetadata(Container container) {
    var metadata = new LinkedHashMap<String, String>();
    metadata.put("imageReference", container == null ? null : container.getImage());
    metadata.put("containerName", container == null ? null : container.getName());
    if (container != null && container.getEnvironmentalVariables() != null) {
      for (String key : java.util.List.of("OUROBOROS_APP_INCARNATION_ID", "OUROBOROS_DEBUG_OWNER")) {
        String value = container.getEnvironmentalVariables().get(key);
        if (value != null) {
          metadata.put(key, value);
        }
      }
    }
    return metadata;
  }

  public static RuntimeStatus toRuntimeStatus(ContainerStatus status) {
    if (status == null) {
      return RuntimeStatus.UNKNOWN;
    }
    switch (status) {
      case NOT_FOUND:
        return RuntimeStatus.NOT_FOUND;
      case LAUNCHING:
        return RuntimeStatus.STARTING;
      case RUNNING:
        return RuntimeStatus.RUNNING;
      case STOPPED:
        return RuntimeStatus.STOPPED;
      case PAUSED:
        return RuntimeStatus.RUNNING;
      default:
        return RuntimeStatus.UNKNOWN;
    }
  }

  public static RuntimeCapability capabilitiesFor(RuntimeKind runtimeKind) {
    return RuntimeCapability.builder()
        .supportsPause(RuntimeKind.TEST_APP == runtimeKind)
        .supportsDebugPort(RuntimeKind.TEST_APP == runtimeKind)
        .supportsDebugSession(RuntimeKind.TEST_APP == runtimeKind)
        .supportsWorkspaceHotReload(true)
        .supportsNodePortExposure(false)
        .supportsIngressExposure(false)
        .build();
  }

  public static RuntimeKind inferRuntimeKind(Map<String, ?> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }
    if (attributes.containsKey(DEV_HELPER_LABEL)) {
      return RuntimeKind.DEV_HELPER;
    }
    if (attributes.containsKey(TEST_APP_LABEL) || attributes.containsKey(PROFILE_LABEL)) {
      return RuntimeKind.TEST_APP;
    }
    return null;
  }

  public static String profileOf(RuntimeKind runtimeKind, Map<String, ?> attributes) {
    Object profile = attributes == null ? null : attributes.get(PROFILE_LABEL);
    if (profile != null && !profile.toString().trim().isEmpty()) {
      return profile.toString().trim();
    }
    return RuntimeKind.DEV_HELPER == runtimeKind ? DEV_HELPER_PROFILE : null;
  }

  public static String webEndpoint(Map<String, ?> attributes) {
    return httpEndpoint(resolvePort(attributes, WEB_PORT_LABEL));
  }

  public static String apiEndpoint(Map<String, ?> attributes) {
    return httpEndpoint(resolvePort(attributes, API_PORT_LABEL));
  }

  public static String debugEndpoint(Map<String, ?> attributes) {
    return null;
  }

  private static String runtimeId(Container container) {
    if (container == null) {
      return null;
    }
    return text(container.getId()) == null ? container.getName() : container.getId();
  }

  private static Map<String, String> labelsOf(Container container) {
    return container == null || container.getLabels() == null ? Collections.<String, String>emptyMap() : container.getLabels();
  }

  private static Integer resolvePort(Container container, String labelKey, int containerPort) {
    Map<String, String> labels = labelsOf(container);
    Integer labelPort = resolvePort(labels, labelKey);
    if (labelPort != null) {
      return labelPort;
    }
    if (container == null || container.getPortBindings() == null) {
      return null;
    }
    Map<Integer, Integer> tcp = container.getPortBindings().get("tcp");
    return tcp == null ? null : tcp.get(containerPort);
  }

  private static Integer resolvePort(Map<String, ?> attributes, String key) {
    if (attributes == null) {
      return null;
    }
    Object value = attributes.get(key);
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value.toString());
    } catch (NumberFormatException parseFailure) {
      return null;
    }
  }

  private static String httpEndpoint(Integer port) {
    return port == null ? null : "http://" + LOOPBACK + ":" + port;
  }

  private static Map<String, String> accessRef(Integer webPort, Integer apiPort, Integer debugPort) {
    Map<String, String> accessRef = new LinkedHashMap<String, String>();
    if (webPort != null) {
      accessRef.put("webPort", String.valueOf(webPort));
    }
    if (apiPort != null) {
      accessRef.put("apiPort", String.valueOf(apiPort));
    }
    if (debugPort != null) {
      accessRef.put("debugPort", String.valueOf(debugPort));
    }
    return accessRef;
  }

  private static String text(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
