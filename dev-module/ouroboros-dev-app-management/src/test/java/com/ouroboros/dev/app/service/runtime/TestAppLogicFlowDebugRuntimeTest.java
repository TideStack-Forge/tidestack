package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import io.vavr.control.Try;

class TestAppLogicFlowDebugRuntimeTest {
  private final DevRuntimeService runtimes = mock(DevRuntimeService.class);
  private final DevHelperCommandService commands = mock(DevHelperCommandService.class);
  private final AuthenticationPrincipalTestAppProfileResolver profiles = new AuthenticationPrincipalTestAppProfileResolver();
  private final TestAppLogicFlowDebugRuntime adapter = new TestAppLogicFlowDebugRuntime(runtimes, commands, profiles);

  @BeforeEach
  void noBackendAssumedAvailable() {
    when(runtimes.inspect(any())).thenReturn(Try.failure(new IllegalStateException("backend offline")));
  }

  @Test
  void backendInspectionFailureNeverFallsBackToLocalCachedState() {
    assertTrue(adapter.candidates("crm", "alice").isEmpty());
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
    verify(runtimes, never()).getStatus(any());
    verifyNoInteractions(commands);
  }

  @Test
  void normalizedProfileCollisionDoesNotGrantAnotherCreatorAccess() {
    assertEquals(profiles.resolveProfileId("alice@example.com"), profiles.resolveProfileId("alice_example.com"));
    inspect(instance(BackendType.DOCKER, profiles.resolveProfileId("alice_example.com"), "alice@example.com", true, false));
    assertTrue(adapter.candidates("crm", "alice_example.com").isEmpty());
    assertThrows(AuthorizationDeniedException.class,
        () -> adapter.probe("crm", "alice_example.com", "container", "capture"));
    verifyNoInteractions(commands);
  }

  @Test
  void kubernetesWithoutOneReadyPodIsShownAsUnsupportedAndCannotBeProbed() {
    inspect(instance(BackendType.KUBERNETES, "alice", "alice", true, false));
    assertFalse(adapter.candidates("crm", "alice").getFirst().supported());
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
    verifyNoInteractions(commands);
  }

  @Test
  void legacyContainerMustBeRebuiltAndFailedDiscoveryCannotUsePreviousSuccessfulProbe() {
    inspect(instance(BackendType.DOCKER, "alice", "alice", false, false));
    assertFalse(adapter.candidates("crm", "alice").getFirst().supported());
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
    inspect(instance(BackendType.DOCKER, "alice", "alice", true, false));
    when(commands.exec(eq("crm"), eq("probeLogicFlowCapture"), anyMap(), anyLong())).thenReturn(CompletableFuture.completedFuture(reply()));
    var target = adapter.probe("crm", "alice", "container", "capture");
    assertEquals("helper", target.helperInstanceId());
    assertEquals("incarnation", target.appIncarnationId());
    when(runtimes.inspect(any())).thenReturn(Try.failure(new IllegalStateException("backend offline")));
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
    verify(commands, times(1)).exec(anyString(), anyString(), anyMap(), anyLong());
  }

  @Test
  void probeRejectsWrongProcessEnvelopeAndChangedPod() {
    inspect(instance(BackendType.KUBERNETES, "alice", "alice", true, true));
    when(commands.exec(anyString(), anyString(), anyMap(), anyLong())).thenReturn(CompletableFuture.completedFuture(reply().setData(Map.of("protocol", "2.0", "podUid", "old-pod"))));
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
    when(commands.exec(anyString(), anyString(), anyMap(), anyLong())).thenReturn(CompletableFuture.completedFuture(reply().setProfile("bob")));
    assertThrows(BusinessException.class, () -> adapter.probe("crm", "alice", "container", "capture"));
  }

  @Test
  void accessRevocationAndOtherProfileAreRejectedForSameOwner() {
    var authorization = mock(AppEditAccessEvaluator.class);
    when(authorization.canEdit("crm", "alice")).thenReturn(true, false);
    var policy = new TestAppLogicFlowDebugAccess(profiles, authorization);
    assertFalse(policy.canDebug("crm", "bob", "alice"));
    assertTrue(policy.canDebug("crm", "alice", "alice"));
    assertFalse(policy.canDebug("crm", "alice", "alice"));
    assertFalse(policy.canDebug("crm", null, null));
  }

  private void inspect(RuntimeInstance instance) {
    when(runtimes.inspect(RuntimeIdentityKey.from(instance))).thenReturn(Try.success(instance));
  }

  private RuntimeInstance instance(BackendType backend, String profile, String owner, boolean incarnation, boolean pod) {
    var metadata = new java.util.LinkedHashMap<String, String>();
    metadata.put("OUROBOROS_DEBUG_OWNER", owner);
    if (incarnation) metadata.put("OUROBOROS_APP_INCARNATION_ID", "incarnation");
    if (pod) metadata.put("debugPodUid", "pod");
    return RuntimeInstance.builder().runtimeId("container").appName("crm").profile(profile)
        .backendType(backend).runtimeKind(RuntimeKind.TEST_APP).status(RuntimeStatus.RUNNING)
        .runtimeGeneration(42L).backendMetadata(metadata).build();
  }

  private CommandResult reply() {
    return new CommandResult().setSuccess(true).setAppName("crm").setProfile("alice")
        .setAppIncarnationId("incarnation").setHelperInstanceId("helper").setHelperStartedAtEpochMillis(100L)
        .setData(Map.of("protocol", "2.0"));
  }
}
