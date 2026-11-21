package com.ouroboros.dev.runtime.gateway.command;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

public class SpringGatewayRouteSyncPublisher implements GatewayRouteSyncPublisher {
  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringGatewayRouteSyncPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher must not be null");
  }

  @Override
  public Try<Void> publish(GatewayRouteSyncCommand command) {
    applicationEventPublisher.publishEvent(command);
    return Try.success(null);
  }
}
