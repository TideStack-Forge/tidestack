package com.ouroboros.dev.runtime.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommandType;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncPublisher;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class RuntimeGatewaySyncListenerTest {

  @Test
  void shouldUpsertStableDomainRunningRuntimeOnCreateAndEndpointChange() {
    RecordingGatewayRouteSyncPublisher publisher = new RecordingGatewayRouteSyncPublisher();
    RuntimeGatewaySyncListener listener = new RuntimeGatewaySyncListener(publisher, fixedResolver("crm-alice.develop.example.com"));

    listener.onEvent(event(DevRuntimeEventType.RUNTIME_CREATED, RuntimeStatus.RUNNING, "https://crm.example.com", null));
    listener.onEvent(event(DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED, RuntimeStatus.RUNNING, null, "https://crm.example.com/api"));

    assertEquals(2, publisher.commands.size());
    assertEquals(GatewayRouteSyncCommandType.UPSERT, publisher.commands.get(0).getType());
    assertEquals("crm", publisher.commands.get(0).getAppName());
    assertEquals("alice", publisher.commands.get(0).getProfile());
    assertEquals("crm-alice.develop.example.com", publisher.commands.get(0).getStableDomainHost());
    assertEquals("https://crm.example.com", publisher.commands.get(0).getWebEndpoint());
    assertEquals("https://crm.example.com/api", publisher.commands.get(1).getApiEndpoint());
  }

  @Test
  void shouldDeleteStableDomainRuntimeWhenItLeavesRunningStateOrFailsOrIsDeleted() {
    RecordingGatewayRouteSyncPublisher publisher = new RecordingGatewayRouteSyncPublisher();
    RuntimeGatewaySyncListener listener = new RuntimeGatewaySyncListener(publisher, fixedResolver("crm-alice.develop.example.com"));

    listener.onEvent(event(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, RuntimeStatus.STOPPED, "https://crm.example.com", null));
    listener.onEvent(event(DevRuntimeEventType.RUNTIME_FAILED, RuntimeStatus.FAILED, "https://crm.example.com", null));
    listener.onEvent(event(DevRuntimeEventType.RUNTIME_DELETED, RuntimeStatus.STOPPED, "https://crm.example.com", null));

    assertEquals(3, publisher.commands.size());
    assertEquals(GatewayRouteSyncCommandType.DELETE, publisher.commands.get(0).getType());
    assertEquals("crm", publisher.commands.get(0).getAppName());
    assertEquals("alice", publisher.commands.get(0).getProfile());
  }

  @Test
  void shouldIgnoreIneligibleEvents() {
    RecordingGatewayRouteSyncPublisher publisher = new RecordingGatewayRouteSyncPublisher();
    RuntimeGatewaySyncListener listener = new RuntimeGatewaySyncListener(publisher, fixedResolver("crm-alice.develop.example.com"));
    RuntimeGatewaySyncListener unresolvedHostListener = new RuntimeGatewaySyncListener(publisher, fixedResolver(null));

    listener.onEvent(eventWithExposure(ExposureStrategy.TEMP_PORT));
    listener.onEvent(eventWithRuntimeKind(RuntimeKind.DEV_HELPER));
    listener.onEvent(eventWithBackend(BackendType.KUBERNETES));
    listener.onEvent(eventWithoutExposureStrategy());
    listener.onEvent(eventWithMissingAppName());
    unresolvedHostListener.onEvent(eventWithoutResolvedHost());

    assertEquals(0, publisher.commands.size());
  }

  @Test
  void shouldSwallowPublisherFailuresAfterRecordingAttempt() {
    RecordingGatewayRouteSyncPublisher publisher = new RecordingGatewayRouteSyncPublisher();
    publisher.failPublish = true;
    RuntimeGatewaySyncListener listener = new RuntimeGatewaySyncListener(publisher, fixedResolver("crm-alice.develop.example.com"));

    assertDoesNotThrow(() -> listener.onEvent(event(DevRuntimeEventType.RUNTIME_CREATED, RuntimeStatus.RUNNING, "https://crm.example.com", null)));
    assertEquals(1, publisher.commands.size());
  }

  private static DevRuntimeEvent event(DevRuntimeEventType type, RuntimeStatus status, String webEndpoint, String apiEndpoint) {
    return DevRuntimeEvent.builder()
        .type(type)
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(status)
        .webEndpoint(webEndpoint)
        .apiEndpoint(apiEndpoint)
        .build();
  }

  private static DevRuntimeEvent eventWithExposure(ExposureStrategy exposureStrategy) {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(exposureStrategy)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm.example.com")
        .build();
  }

  private static DevRuntimeEvent eventWithRuntimeKind(RuntimeKind runtimeKind) {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(runtimeKind)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm.example.com")
        .build();
  }

  private static DevRuntimeEvent eventWithBackend(BackendType backendType) {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(backendType)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm.example.com")
        .build();
  }

  private static DevRuntimeEvent eventWithoutExposureStrategy() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm.example.com")
        .build();
  }

  private static DevRuntimeEvent eventWithMissingAppName() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("rt-crm-alice")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm.example.com")
        .build();
  }

  private static DevRuntimeEvent eventWithoutResolvedHost() {
    return event(DevRuntimeEventType.RUNTIME_CREATED, RuntimeStatus.RUNNING, "https://crm.example.com", null);
  }

  private static StableDomainHostResolver fixedResolver(String host) {
    return new StableDomainHostResolver() {
      @Override
      public String resolve(String appName, String profile) {
        return host;
      }
    };
  }

  static class RecordingGatewayRouteSyncPublisher implements GatewayRouteSyncPublisher {
    private final List<GatewayRouteSyncCommand> commands = new ArrayList<GatewayRouteSyncCommand>();
    private boolean failPublish;

    @Override
    public Try<Void> publish(GatewayRouteSyncCommand command) {
      commands.add(command);
      return failPublish ? Try.failure(new IllegalStateException("gateway unavailable")) : Try.success(null);
    }
  }
}
