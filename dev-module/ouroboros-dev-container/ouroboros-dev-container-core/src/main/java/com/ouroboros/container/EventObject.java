package com.ouroboros.container;

import java.util.Map;

public interface EventObject {
  String getContainerId();

  ContainerStatus getStatus();

  Map<String, Object> getAttributes();
}
