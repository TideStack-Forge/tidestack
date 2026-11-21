package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO;
import com.ouroboros.dev.app.model.AuthorizeDebugProfileRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO;
import com.ouroboros.dev.app.model.TemporaryDebugArtifactDTO;
import com.ouroboros.dev.app.service.runtime.DebugSessionWorkspaceStorage;
import com.ouroboros.dev.app.service.runtime.DebugProfileAuthorizationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class RuntimeDebugProfileControllerTest {

  @Mock
  private DebugProfileAuthorizationService debugProfileAuthorizationService;
  @Mock
  private DebugSessionWorkspaceStorage debugSessionWorkspaceStorage;

  @Test
  void shouldStillDelegateUploadThroughWorkspaceStorageContract() {
    RuntimeDebugProfileController controller = new RuntimeDebugProfileController(debugProfileAuthorizationService, debugSessionWorkspaceStorage);
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.setDevKey("dev-key-001");
    DebugArtifactUploadRequest.UploadFile file = new DebugArtifactUploadRequest.UploadFile();
    file.setFileName("agent.jar");
    file.setContentBase64("aGVsbG8=");
    request.getFiles().add(file);
    AuthorizeDebugProfileDTO authorization = new AuthorizeDebugProfileDTO();
    authorization.setProfileId("alice");
    when(debugProfileAuthorizationService.authorize("crm", "dev-key-001")).thenReturn(Try.success(authorization));
    when(debugSessionWorkspaceStorage.store("alice", request)).thenReturn(Try.success(new DebugArtifactUploadResponseDTO()));

    controller.upload("crm", request);

    verify(debugSessionWorkspaceStorage).store("alice", request);
  }

  @Test
  void shouldAuthorizeDevelopAppThroughService() {
    RuntimeDebugProfileController controller = new RuntimeDebugProfileController(debugProfileAuthorizationService, debugSessionWorkspaceStorage);
    AuthorizeDebugProfileRequest request = new AuthorizeDebugProfileRequest();
    request.setDevKey("dev-key-001");
    AuthorizeDebugProfileDTO dto = new AuthorizeDebugProfileDTO();
    dto.setProfileId("alice");
    dto.setAuthorized(true);
    dto.setRuntimeStrategy("SESSION_TUNNEL");
    dto.setExpiresAt(Instant.parse("2026-03-24T09:30:00Z"));
    when(debugProfileAuthorizationService.authorize("crm", "dev-key-001")).thenReturn(Try.success(dto));

    WebApiResult<AuthorizeDebugProfileDTO> result = controller.authorize("crm", request);

    assertEquals(0, result.getCode());
    assertEquals("alice", result.getData().getProfileId());
    assertEquals(Boolean.TRUE, result.getData().getAuthorized());
    assertEquals("SESSION_TUNNEL", result.getData().getRuntimeStrategy());
    assertEquals(Instant.parse("2026-03-24T09:30:00Z"), result.getData().getExpiresAt());
    verify(debugProfileAuthorizationService).authorize("crm", "dev-key-001");
  }

  @Test
  void shouldRejectBlankDebugKey() {
    RuntimeDebugProfileController controller = new RuntimeDebugProfileController(debugProfileAuthorizationService, debugSessionWorkspaceStorage);
    AuthorizeDebugProfileRequest request = new AuthorizeDebugProfileRequest();
    request.setDevKey("   ");

    assertThrows(BusinessException.class, () -> controller.authorize("crm", request));
  }

  @Test
  void shouldUploadDebugArtifactsThroughWorkspaceStorage() {
    RuntimeDebugProfileController controller = new RuntimeDebugProfileController(debugProfileAuthorizationService, debugSessionWorkspaceStorage);
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.setDevKey("dev-key-001");
    DebugArtifactUploadRequest.UploadFile file = new DebugArtifactUploadRequest.UploadFile();
    file.setFileName("agent.jar");
    file.setContentBase64("aGVsbG8=");
    request.getFiles().add(file);
    DebugArtifactUploadResponseDTO dto = new DebugArtifactUploadResponseDTO();
    dto.setUploadBatchId("upl-001");
    TemporaryDebugArtifactDTO artifact = new TemporaryDebugArtifactDTO();
    artifact.setArtifactId("artifact-001");
    artifact.setFileName("agent.jar");
    artifact.setSha256("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    dto.getArtifacts().add(artifact);
    AuthorizeDebugProfileDTO authorization = new AuthorizeDebugProfileDTO();
    authorization.setProfileId("alice");
    when(debugProfileAuthorizationService.authorize("crm", "dev-key-001")).thenReturn(Try.success(authorization));
    when(debugSessionWorkspaceStorage.store("alice", request)).thenReturn(Try.success(dto));

    WebApiResult<DebugArtifactUploadResponseDTO> result = controller.upload("crm", request);

    assertEquals(0, result.getCode());
    assertEquals("upl-001", result.getData().getUploadBatchId());
    assertEquals("artifact-001", result.getData().getArtifacts().get(0).getArtifactId());
    verify(debugProfileAuthorizationService).authorize("crm", "dev-key-001");
    verify(debugSessionWorkspaceStorage).store("alice", request);
  }

  @Test
  void shouldRejectUploadWithoutAuthorizedDebugKey() {
    RuntimeDebugProfileController controller = new RuntimeDebugProfileController(debugProfileAuthorizationService, debugSessionWorkspaceStorage);
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.setDevKey("wrong-key");
    DebugArtifactUploadRequest.UploadFile file = new DebugArtifactUploadRequest.UploadFile();
    file.setFileName("agent.jar");
    file.setContentBase64("aGVsbG8=");
    request.getFiles().add(file);
    when(debugProfileAuthorizationService.authorize("crm", "wrong-key"))
        .thenReturn(Try.failure(new BusinessException("开发密钥不匹配")));

    WebApiResult<DebugArtifactUploadResponseDTO> result = controller.upload("crm", request);

    assertEquals(false, Integer.valueOf(0).equals(result.getCode()));
    verify(debugProfileAuthorizationService).authorize("crm", "wrong-key");
    verify(debugSessionWorkspaceStorage, never()).store("alice", request);
  }
}
