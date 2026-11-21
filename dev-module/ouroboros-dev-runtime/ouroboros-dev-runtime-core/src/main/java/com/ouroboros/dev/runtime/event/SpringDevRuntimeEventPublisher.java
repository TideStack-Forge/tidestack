package com.ouroboros.dev.runtime.event;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

public class SpringDevRuntimeEventPublisher implements DevRuntimeEventPublisher {
  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringDevRuntimeEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher must not be null");
  }

  @Override
  public void publish(DevRuntimeEvent event) {
    if (event != null) {
      applicationEventPublisher.publishEvent(event);
    }
  }
}
