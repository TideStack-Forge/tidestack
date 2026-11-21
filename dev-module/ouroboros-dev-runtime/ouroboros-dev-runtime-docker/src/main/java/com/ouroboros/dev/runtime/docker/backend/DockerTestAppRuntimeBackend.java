package com.ouroboros.dev.runtime.docker.backend;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.messaging.support.MessageBuilder;

import io.vavr.control.Try;

import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.util.Json;

public class DockerTestAppRuntimeBackend implements RuntimeBackend {
  private final ContainerService containerService;
  private final MQProducer appControlProducer;

  public DockerTestAppRuntimeBackend(ContainerService containerService) {
    this(containerService, null);
  }

  public DockerTestAppRuntimeBackend(ContainerService containerService, MQProducer appControlProducer) {
    this.containerService = containerService;
    this.appControlProducer = appControlProducer;
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.DOCKER;
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.TEST_APP;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.DOCKER && spec.getRuntimeKind() == RuntimeKind.TEST_APP;
  }

  @Override
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    Container container = buildContainer(spec);
    return containerService.create(container)
        .fold(Try::failure, id -> {
          container.setId(id);
          return Try.success(DockerRuntimeMapper.toRuntime(container, RuntimeKind.TEST_APP, null, RuntimeStatus.STOPPED));
        });
  }

  @Override
  public Try<RuntimeInstance> start(RuntimeSpec spec) {
    return containerService.start(containerName(spec))
        .fold(Try::failure, value -> inspect(containerName(spec), null, null));
  }

  @Override
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
    return containerService.stop(containerName(key))
        .fold(Try::failure, value -> inspect(containerName(key), null, RuntimeStatus.STOPPED));
  }

  @Override
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration) {
    return containerService.restart(containerName(spec))
        .fold(Try::failure, value -> inspect(containerName(spec), runtimeGeneration, null));
  }

  @Override
  public Try<Void> delete(RuntimeIdentityKey key) {
    return containerService.delete(containerName(key)).fold(Try::failure, Try::success);
  }

  @Override
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration) {
    return inspect(containerName(spec), runtimeGeneration, null)
        .map(instance -> {
          sendRefreshCommand(spec);
          return instance;
        });
  }

  @Override
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return inspect(containerName(key), null, null);
  }

  private Try<RuntimeInstance> inspect(String containerName, Long runtimeGeneration, RuntimeStatus overrideStatus) {
    return containerService.getContainerInfoByName(containerName)
        .fold(Try::failure, container -> Try.success(DockerRuntimeMapper.toRuntime(container, RuntimeKind.TEST_APP, runtimeGeneration, overrideStatus)));
  }

  private Container buildContainer(RuntimeSpec spec) {
    Container container = new Container();
    container.setName(containerName(spec));
    container.setImage(spec.getImage());
    container.setEnvironmentalVariables(spec.getEnv());
    container.setLabels(labels(spec));
    container.setPortBindings(portBindings(spec));
    container.setVolumeBindings(volumeBindings(spec));
    container.setNetwork(spec.getNetwork());
    return container;
  }

  private Map<String, String> labels(RuntimeSpec spec) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    if (spec.getLabels() != null) {
      labels.putAll(spec.getLabels());
    }
    labels.put(DockerRuntimeMapper.TEST_APP_LABEL, spec.getAppName());
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, spec.getAppName());
    labels.put(DockerRuntimeMapper.PROFILE_LABEL, spec.getProfile());
    if (spec.getExposureStrategy() == ExposureStrategy.TEMP_PORT) {
      putPortLabel(labels, DockerRuntimeMapper.WEB_PORT_LABEL, requestedPort(spec, "web"));
      putPortLabel(labels, DockerRuntimeMapper.API_DEBUG_PORT_LABEL, requestedPort(spec, "apiDebug"));
    }
    return labels;
  }

  private Map<String, Map<Integer, Integer>> portBindings(RuntimeSpec spec) {
    if (spec.getExposureStrategy() != ExposureStrategy.TEMP_PORT) {
      return null;
    }
    Integer webPort = requestedPort(spec, "web");
    if (webPort == null) {
      return null;
    }
    Map<Integer, Integer> tcp = new LinkedHashMap<Integer, Integer>();
    putPortBinding(tcp, Integer.valueOf(80), webPort);
    putPortBinding(tcp, Integer.valueOf(5005), requestedPort(spec, "apiDebug"));
    if (tcp.isEmpty()) {
      return null;
    }
    return Collections.singletonMap("tcp", tcp);
  }

  private Map<String, String> volumeBindings(RuntimeSpec spec) {
    if (spec.getWorkspaceRef() == null || spec.getWorkspaceRef().getHostPath() == null) {
      return null;
    }
    Map<String, String> bindings = new LinkedHashMap<String, String>();
    bindings.put("/app/deploy", spec.getWorkspaceRef().getHostPath());
    return bindings;
  }

  private String containerName(RuntimeSpec spec) {
    return "ouroboros-test-app-" + spec.getAppName() + "-" + spec.getProfile();
  }

  private String containerName(RuntimeIdentityKey key) {
    return "ouroboros-test-app-" + key.getAppName() + "-" + key.getProfile();
  }

  private Integer requestedPort(RuntimeSpec spec, String key) {
    return spec.getRequestedPorts() == null ? null : spec.getRequestedPorts().get(key);
  }

  private void sendRefreshCommand(RuntimeSpec spec) {
    if (appControlProducer == null) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("command", "refresh");
    payload.put("appAndProfile", spec.getAppName() + "-" + spec.getProfile());
    String body = Json.toJsonString(payload);
    appControlProducer.send(MessageBuilder.withPayload(body.getBytes(StandardCharsets.UTF_8)).build());
  }

  private void putPortLabel(Map<String, String> labels, String labelKey, Integer port) {
    if (port != null && port.intValue() > 0) {
      labels.put(labelKey, String.valueOf(port));
    }
  }

  private void putPortBinding(Map<Integer, Integer> portBindings, Integer containerPort, Integer hostPort) {
    if (hostPort != null && hostPort.intValue() > 0) {
      portBindings.put(containerPort, hostPort);
    }
  }
}
