package com.ouroboros.dev.runtime.docker.event;

import java.util.Map;

import com.ouroboros.container.ContainerEventType;
import com.ouroboros.container.DockerEventListener;
import com.ouroboros.container.EventObject;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class DockerRuntimeEventBridge implements DockerEventListener {
  private final DevRuntimeEventPublisher eventPublisher;

  public DockerRuntimeEventBridge(DevRuntimeEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void onEvent(ContainerEventType type, EventObject event) {
    RuntimeKind runtimeKind = DockerRuntimeMapper.inferRuntimeKind(event.getAttributes());
    if (runtimeKind == null) {
      return;
    }
    eventPublisher.publish(DevRuntimeEvent.builder()
        .type(toEventType(type))
        .runtimeId(event.getContainerId())
        .appName(text(event.getAttributes(), DockerRuntimeMapper.APP_NAME_LABEL))
        .profile(DockerRuntimeMapper.profileOf(runtimeKind, event.getAttributes()))
        .runtimeKind(runtimeKind)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(resolveExposureStrategy(runtimeKind, event.getAttributes()))
        .status(DockerRuntimeMapper.toRuntimeStatus(event.getStatus()))
        .webEndpoint(DockerRuntimeMapper.webEndpoint(event.getAttributes()))
        .apiEndpoint(DockerRuntimeMapper.apiEndpoint(event.getAttributes()))
        .debugEndpoint(DockerRuntimeMapper.debugEndpoint(event.getAttributes()))
        .build());
  }

  private DevRuntimeEventType toEventType(ContainerEventType type) {
    if (type == ContainerEventType.CONTAINER_CREATE) {
      return DevRuntimeEventType.RUNTIME_CREATED;
    }
    if (type == ContainerEventType.CONTAINER_DELETE) {
      return DevRuntimeEventType.RUNTIME_DELETED;
    }
    return DevRuntimeEventType.RUNTIME_STATUS_CHANGED;
  }

  private String text(Map<String, ?> attributes, String key) {
    Object value = attributes == null ? null : attributes.get(key);
    return value == null ? null : value.toString();
  }

  private ExposureStrategy resolveExposureStrategy(RuntimeKind runtimeKind, Map<String, ?> attributes) {
    if (runtimeKind != RuntimeKind.TEST_APP) {
      return null;
    }
    if (hasText(attributes, DockerRuntimeMapper.WEB_PORT_LABEL) || hasText(attributes, DockerRuntimeMapper.API_PORT_LABEL)) {
      return ExposureStrategy.TEMP_PORT;
    }
    return ExposureStrategy.STABLE_DOMAIN;
  }

  private boolean hasText(Map<String, ?> attributes, String key) {
    String value = text(attributes, key);
    return value != null && !value.trim().isEmpty();
  }
}
