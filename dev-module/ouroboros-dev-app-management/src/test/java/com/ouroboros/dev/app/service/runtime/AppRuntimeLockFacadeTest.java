package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppArtifactBindingDTO;
import com.ouroboros.dev.app.model.ProfileArtifactOverlayDTO;
import com.ouroboros.dev.app.model.RepositoryRefDTO;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.service.ArtifactBindingService;
import com.ouroboros.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AppRuntimeLockFacadeTest {

  @Mock
  private ArtifactBindingService artifactBindingService;

  private AppRuntimeLockFacade facade;

  @BeforeEach
  void setUp() {
    facade = new AppRuntimeLockFacade(artifactBindingService);
  }

  @Test
  void shouldReplaceRepositoryRefsThroughArtifactBindingService() {
    RepositoryRefDTO repositoryRef = repositoryRef("central");
    when(artifactBindingService.replaceRepositoryRefs(eq("crm"), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(Try.success(null));

    Try<Void> result = facade.replaceRepositoryRefs("crm", Collections.singletonList(repositoryRef));

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(artifactBindingService).replaceRepositoryRefs(eq("crm"), captor.capture());
    com.ouroboros.dev.runtime.model.RepositoryRef mapped =
        (com.ouroboros.dev.runtime.model.RepositoryRef) captor.getValue().get(0);
    assertEquals("central", mapped.getRepositoryId());
    assertEquals("https://repo.example.com/maven2", mapped.getRepositoryUrl());
  }

  @Test
  void shouldRejectDynamicVersionsForAppArtifactBindings() {
    AppArtifactBindingDTO binding = artifactBinding("[1.0,2.0)");

    Try<Void> result = facade.replaceAppArtifactBindings("crm", Collections.singletonList(binding));

    assertTrue(result.isFailure());
    assertEquals(BusinessException.class, result.getCause().getClass());
    verify(artifactBindingService, never()).replaceAppArtifactBindings(eq("crm"), org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void shouldReplaceProfileArtifactOverlaysAndPreserveRepositoryPrecedence() {
    ProfileArtifactOverlayDTO overlay = profileOverlay("REPLACE");
    when(artifactBindingService.replaceProfileArtifactOverlays(eq("crm"), eq("alice"), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(Try.success(null));

    Try<Void> result = facade.replaceProfileArtifactOverlays("crm", "alice", Collections.singletonList(overlay));

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(artifactBindingService).replaceProfileArtifactOverlays(eq("crm"), eq("alice"), captor.capture());
    ProfileArtifactOverlay mapped = (ProfileArtifactOverlay) captor.getValue().get(0);
    assertEquals("profile-default", mapped.getDefaultRepositoryRef().getRepositoryId());
    assertEquals("artifact-repo", mapped.getReplacementArtifact().getRepositoryRef().getRepositoryId());
    assertEquals("1.0.1", mapped.getReplacementArtifact().getVersion());
  }

  @Test
  void shouldRejectUnsupportedOverlayAction() {
    ProfileArtifactOverlayDTO overlay = profileOverlay("MERGE");

    Try<Void> result = facade.replaceProfileArtifactOverlays("crm", "alice", Collections.singletonList(overlay));

    assertTrue(result.isFailure());
    assertEquals(BusinessException.class, result.getCause().getClass());
    verify(artifactBindingService, never()).replaceProfileArtifactOverlays(eq("crm"), eq("alice"), org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void shouldTriggerResolveUsingNormalizedRuntimeKind() {
    when(artifactBindingService.requestResolve("crm", "alice", RuntimeKind.TEST_APP)).thenReturn(Try.success(null));

    Try<Void> result = facade.requestResolve("crm", "alice", "test_app");

    assertTrue(result.isSuccess());
    verify(artifactBindingService).requestResolve("crm", "alice", RuntimeKind.TEST_APP);
  }

  private static RepositoryRefDTO repositoryRef(String repositoryId) {
    RepositoryRefDTO repositoryRef = new RepositoryRefDTO();
    repositoryRef.setRepositoryId(repositoryId);
    repositoryRef.setRepositoryType("MAVEN");
    repositoryRef.setRepositoryUrl("https://repo.example.com/maven2");
    repositoryRef.setCredentialRef("cred-001");
    repositoryRef.setDefaultEnabled(true);
    return repositoryRef;
  }

  private static AppArtifactBindingDTO artifactBinding(String version) {
    AppArtifactBindingDTO binding = new AppArtifactBindingDTO();
    binding.setGroupId("com.example");
    binding.setArtifactId("crm-runtime");
    binding.setVersion(version);
    binding.setPackaging("jar");
    binding.setClassifier("exec");
    binding.setEnabled(true);
    binding.setUsage("runtime");
    binding.setRepositoryRef(repositoryRef("artifact-repo"));
    return binding;
  }

  private static ProfileArtifactOverlayDTO profileOverlay(String action) {
    ProfileArtifactOverlayDTO overlay = new ProfileArtifactOverlayDTO();
    overlay.setAction(action);
    overlay.setTargetArtifactKey("com.example:crm-runtime");
    overlay.setReplacementArtifact(artifactBinding("1.0.1"));
    overlay.setDefaultRepositoryRef(repositoryRef("profile-default"));
    return overlay;
  }
}
