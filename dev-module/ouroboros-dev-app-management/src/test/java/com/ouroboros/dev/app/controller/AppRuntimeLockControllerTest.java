package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppArtifactBindingDTO;
import com.ouroboros.dev.app.model.ProfileArtifactOverlayDTO;
import com.ouroboros.dev.app.model.RepositoryRefDTO;
import com.ouroboros.dev.app.service.runtime.AppRuntimeLockFacade;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class AppRuntimeLockControllerTest {

  @Mock
  private AppRuntimeLockFacade appRuntimeLockFacade;

  private AppRuntimeLockController controller;

  @BeforeEach
  void setUp() {
    controller = new AppRuntimeLockController(appRuntimeLockFacade);
  }

  @Test
  void shouldReplaceRepositoryRefsThroughFacade() {
    RepositoryRefDTO repositoryRef = repositoryRef("central");
    when(appRuntimeLockFacade.replaceRepositoryRefs("crm", Collections.singletonList(repositoryRef)))
        .thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.replaceRepositoryRefs("crm", Collections.singletonList(repositoryRef));

    assertEquals(0, result.getCode());
    verify(appRuntimeLockFacade).replaceRepositoryRefs("crm", Collections.singletonList(repositoryRef));
  }

  @Test
  void shouldReplaceAppArtifactBindingsThroughFacade() {
    AppArtifactBindingDTO binding = artifactBinding("1.0.0");
    when(appRuntimeLockFacade.replaceAppArtifactBindings("crm", Collections.singletonList(binding)))
        .thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.replaceAppArtifactBindings("crm", Collections.singletonList(binding));

    assertEquals(0, result.getCode());
    verify(appRuntimeLockFacade).replaceAppArtifactBindings("crm", Collections.singletonList(binding));
  }

  @Test
  void shouldReplaceProfileArtifactOverlaysThroughFacade() {
    ProfileArtifactOverlayDTO overlay = profileOverlay("REPLACE");
    when(appRuntimeLockFacade.replaceProfileArtifactOverlays("crm", "alice", Collections.singletonList(overlay)))
        .thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.replaceProfileArtifactOverlays("crm", "alice", Collections.singletonList(overlay));

    assertEquals(0, result.getCode());
    verify(appRuntimeLockFacade).replaceProfileArtifactOverlays("crm", "alice", Collections.singletonList(overlay));
  }

  @Test
  void shouldTriggerResolveThroughFacade() {
    when(appRuntimeLockFacade.requestResolve("crm", "alice", "test_app")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.requestResolve("crm", "alice", "test_app");

    assertEquals(0, result.getCode());
    verify(appRuntimeLockFacade).requestResolve("crm", "alice", "test_app");
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
