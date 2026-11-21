package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeRuntimeBackend;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeWorkspaceProvisioner;

class WorkspaceProvisionerSelectionTest {

  @Test
  void shouldResolveProvisionerByWorkspaceMode() {
    FakeWorkspaceProvisioner hostPathProvisioner = FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH);
    FakeWorkspaceProvisioner pvcProvisioner = FakeWorkspaceProvisioner.forMode(WorkspaceMode.PVC);
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(hostPathProvisioner, pvcProvisioner),
        new InMemoryRuntimeStateStore()
    );

    service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.PVC)
        .build());

    assertEquals(0, hostPathProvisioner.invocationCount());
    assertEquals(1, pvcProvisioner.invocationCount());
  }

  @Test
  void shouldFailWhenNoProvisionerMatches() {
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(new FakeRuntimeBackend()),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        new InMemoryRuntimeStateStore()
    );

    Try<RuntimeInstance> result = service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.PVC)
        .build());

    assertTrue(result.isFailure());
  }
}
