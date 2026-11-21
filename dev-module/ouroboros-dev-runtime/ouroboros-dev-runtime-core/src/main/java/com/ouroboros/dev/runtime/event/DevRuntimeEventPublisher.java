package com.ouroboros.dev.runtime.event;

import com.ouroboros.dev.runtime.model.RuntimeInstance;

public interface DevRuntimeEventPublisher {
  DevRuntimeEventPublisher NO_OP = new DevRuntimeEventPublisher() {
    @Override
    public void publish(DevRuntimeEvent event) {
    }
  };

  void publish(DevRuntimeEvent event);

  default void publish(DevRuntimeEventType type, RuntimeInstance runtime) {
    publish(DevRuntimeEvent.from(type, runtime));
  }

  static DevRuntimeEventPublisher noop() {
    return NO_OP;
  }
}
