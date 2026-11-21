package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.event.SpringDevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeRuntimeBackend;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeWorkspaceProvisioner;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.RecordingApplicationEventPublisher;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport;

import io.vavr.control.Try;

class DefaultDevRuntimeServiceEventTest {

  @Test
  void shouldPublishCreatedAndDeletedEventsFromService() {
    RecordingApplicationEventPublisher springPublisher = new RecordingApplicationEventPublisher();
    DevRuntimeEventPublisher publisher = new SpringDevRuntimeEventPublisher(springPublisher);
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(new FakeRuntimeBackend()),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        new InMemoryRuntimeStateStore(),
        publisher
    );

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .build();

    service.create(spec);
    service.delete(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER));

    assertEquals(
        Arrays.asList(DevRuntimeEventType.RUNTIME_CREATED, DevRuntimeEventType.RUNTIME_DELETED),
        springPublisher.eventTypes()
    );
    List<DevRuntimeEvent> events = springPublisher.events().stream()
        .filter(event -> event instanceof DevRuntimeEvent)
        .map(event -> (DevRuntimeEvent) event)
        .collect(Collectors.toList());
    assertEquals(
        Arrays.asList(ExposureStrategy.STABLE_DOMAIN, ExposureStrategy.STABLE_DOMAIN),
        events.stream().map(DevRuntimeEvent::getExposureStrategy).collect(Collectors.toList())
    );
  }

  @Test
  void shouldPublishStatusChangedEventWhenStatusPollAdvancesRuntimeState() {
    RecordingApplicationEventPublisher springPublisher = new RecordingApplicationEventPublisher();
    DevRuntimeEventPublisher publisher = new SpringDevRuntimeEventPublisher(springPublisher);
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    stateStore.saveInstance(key, RuntimeContractTestSupport.runtime(spec, RuntimeStatus.STARTING, 1L));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(RuntimeContractTestSupport.runtime(spec, RuntimeStatus.RUNNING, 2L));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore,
        publisher
    );

    RuntimeInstance refreshed = service.getStatus(key).get();

    assertEquals(RuntimeStatus.RUNNING, refreshed.getStatus());
    assertTrue(springPublisher.eventTypes().contains(DevRuntimeEventType.RUNTIME_STATUS_CHANGED));
  }

  @Test
  void shouldNotPublishDuplicateStatusChangedEventWhenStatusPollKeepsSameState() {
    RecordingApplicationEventPublisher springPublisher = new RecordingApplicationEventPublisher();
    DevRuntimeEventPublisher publisher = new SpringDevRuntimeEventPublisher(springPublisher);
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    stateStore.saveInstance(key, RuntimeContractTestSupport.runtime(spec, RuntimeStatus.RUNNING, 1L));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(RuntimeContractTestSupport.runtime(spec, RuntimeStatus.RUNNING, 2L));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore,
        publisher
    );

    service.getStatus(key).get();

    assertEquals(Collections.emptyList(), springPublisher.eventTypes());
  }
}
