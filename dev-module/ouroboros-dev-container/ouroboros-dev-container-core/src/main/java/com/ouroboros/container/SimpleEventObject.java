package com.ouroboros.container;

import java.util.Map;

public class SimpleEventObject implements EventObject {
  private final ContainerStatus status;
  private final String containerId;
  private final Map<String, Object> attributes;

  public SimpleEventObject(String containerId, ContainerStatus status, Map<String, Object> attributes) {
    this.status = status;
    this.containerId = containerId;
    this.attributes = attributes;
  }

  @Override
  public String getContainerId() {
    return containerId;
  }

  @Override
  public ContainerStatus getStatus() {
    return status;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }
}
