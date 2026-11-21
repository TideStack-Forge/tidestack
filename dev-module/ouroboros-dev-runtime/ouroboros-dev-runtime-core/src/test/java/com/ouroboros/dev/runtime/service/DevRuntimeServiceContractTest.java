package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

class DevRuntimeServiceContractTest {

  @Test
  void shouldExposeUnifiedLifecycleAndStateStoreContracts() throws Exception {
    assertEquals(Try.class, DevRuntimeService.class.getMethod("create", RuntimeSpec.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("start", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("stop", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("restart", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("delete", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("reloadWorkspace", RuntimeSpec.class).getReturnType());
    assertEquals(Try.class, DevRuntimeService.class.getMethod("getStatus", RuntimeIdentityKey.class).getReturnType());
    assertEquals(RuntimeOperationState.class, RuntimeOperationState.restart(11L).getClass());
    assertEquals(boolean.class, RuntimeBackend.class.getMethod("supports", RuntimeSpec.class).getReturnType());
    assertEquals(boolean.class, WorkspaceProvisioner.class.getMethod("supports", RuntimeSpec.class).getReturnType());
    assertEquals(Optional.class, RuntimeStateStore.class.getMethod("findSpec", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Optional.class, RuntimeStateStore.class.getMethod("findInstance", RuntimeIdentityKey.class).getReturnType());
    assertEquals(Optional.class, RuntimeStateStore.class.getMethod("findActiveOperation", RuntimeIdentityKey.class).getReturnType());
    assertEquals(
        0L,
        Arrays.stream(DevRuntimeService.class.getMethods())
            .filter(method -> "pause".equals(method.getName()) || "unpause".equals(method.getName()))
            .count()
    );
  }
}
