package com.ouroboros.dev.runtime.gateway;

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.event.EventListener;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventListener;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommandType;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncPublisher;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

public class RuntimeGatewaySyncListener implements DevRuntimeEventListener {
  private static final Log log = LogFactory.getLog(RuntimeGatewaySyncListener.class);

  private final GatewayRouteSyncPublisher gatewayRouteSyncPublisher;
  private final StableDomainHostResolver stableDomainHostResolver;

  public RuntimeGatewaySyncListener(
      GatewayRouteSyncPublisher gatewayRouteSyncPublisher,
      StableDomainHostResolver stableDomainHostResolver
  ) {
    this.gatewayRouteSyncPublisher = Objects.requireNonNull(gatewayRouteSyncPublisher, "gatewayRouteSyncPublisher must not be null");
    this.stableDomainHostResolver = Objects.requireNonNull(stableDomainHostResolver, "stableDomainHostResolver must not be null");
  }

  @Override
  public void onEvent(DevRuntimeEvent event) {
    if (!supports(event)) {
      return;
    }
    if (shouldDelete(event)) {
      delete(event);
      return;
    }
    if (shouldUpsert(event)) {
      upsert(event);
    }
  }

  @EventListener
  public void onApplicationEvent(DevRuntimeEvent event) {
    onEvent(event);
  }

  private boolean supports(DevRuntimeEvent event) {
    return event != null
        && hasText(event.getAppName())
        && hasText(event.getProfile())
        && event.getRuntimeKind() == RuntimeKind.TEST_APP
        && event.getBackendType() == BackendType.DOCKER
        && event.getExposureStrategy() == ExposureStrategy.STABLE_DOMAIN;
  }

  private boolean shouldDelete(DevRuntimeEvent event) {
    return event.getType() == DevRuntimeEventType.RUNTIME_DELETED
        || event.getType() == DevRuntimeEventType.RUNTIME_FAILED
        || (event.getType() == DevRuntimeEventType.RUNTIME_STATUS_CHANGED && event.getStatus() != RuntimeStatus.RUNNING);
  }

  private boolean shouldUpsert(DevRuntimeEvent event) {
    return (event.getType() == DevRuntimeEventType.RUNTIME_CREATED
        || event.getType() == DevRuntimeEventType.RUNTIME_STATUS_CHANGED
        || event.getType() == DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED)
        && event.getStatus() == RuntimeStatus.RUNNING;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private void upsert(DevRuntimeEvent event) {
    String stableDomainHost = stableDomainHostResolver.resolve(event.getAppName(), event.getProfile());
    if (!hasText(stableDomainHost)) {
      return;
    }
    GatewayRouteSyncCommand command = GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.UPSERT)
        .appName(event.getAppName())
        .profile(event.getProfile())
        .runtimeKind(event.getRuntimeKind())
        .backendType(event.getBackendType())
        .runtimeId(event.getRuntimeId())
        .stableDomainHost(stableDomainHost)
        .webEndpoint(event.getWebEndpoint())
        .apiEndpoint(event.getApiEndpoint())
        .build();
    handleResult(gatewayRouteSyncPublisher.publish(command), event);
  }

  private void delete(DevRuntimeEvent event) {
    GatewayRouteSyncCommand command = GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.DELETE)
        .appName(event.getAppName())
        .profile(event.getProfile())
        .runtimeKind(event.getRuntimeKind())
        .backendType(event.getBackendType())
        .runtimeId(event.getRuntimeId())
        .build();
    handleResult(gatewayRouteSyncPublisher.publish(command), event);
  }

  private void handleResult(Try<Void> result, DevRuntimeEvent event) {
    if (result != null && result.isFailure()) {
      log.warn("Failed to synchronize gateway route for event "
          + event.getType()
          + " appName=" + event.getAppName()
          + " profile=" + event.getProfile()
          + " runtimeKind=" + event.getRuntimeKind()
          + " backendType=" + event.getBackendType(), result.getCause());
    }
  }
}
