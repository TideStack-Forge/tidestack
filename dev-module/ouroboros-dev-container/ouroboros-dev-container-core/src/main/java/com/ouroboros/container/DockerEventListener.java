package com.ouroboros.container;

/**
 * @author liansz
 **/
public interface DockerEventListener {
  void onEvent(ContainerEventType type, EventObject event);
}
