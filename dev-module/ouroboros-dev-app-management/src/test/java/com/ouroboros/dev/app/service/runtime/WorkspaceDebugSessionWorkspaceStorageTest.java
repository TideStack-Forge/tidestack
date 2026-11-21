package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.DebugArtifactUploadRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO;

class WorkspaceDebugSessionWorkspaceStorageTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldPersistTemporaryDebugArtifactsUnderUploadBatchPathWithoutFormalRuntimeLockMutation() throws Exception {
    Path debugSupportDir = Files.createDirectories(tempDir.resolve("debug-support"));
    WorkspaceDebugSessionWorkspaceStorage storage = new WorkspaceDebugSessionWorkspaceStorage(workspaceContext(debugSupportDir));
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.getFiles().add(file("agent.jar", "aGVsbG8="));
    request.getFiles().add(file("patch.jar", "d29ybGQ="));

    Try<DebugArtifactUploadResponseDTO> result = storage.store("alice/dev", request);

    assertTrue(result.isSuccess());
    assertTrue(result.get().getUploadBatchId() != null && !result.get().getUploadBatchId().trim().isEmpty());
    assertEquals(2, result.get().getArtifacts().size());
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result.get().getArtifacts().get(0).getSha256());
    assertEquals("486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7", result.get().getArtifacts().get(1).getSha256());
    Path uploadRoot = debugSupportDir.resolve(new ProfileStorageKeyEncoder().encode("alice/dev"))
        .resolve("runtime-debug")
        .resolve("uploads")
        .resolve(result.get().getUploadBatchId());
    assertTrue(Files.exists(uploadRoot.resolve("manifest.json")));
    assertTrue(Files.exists(uploadRoot.resolve("files").resolve(result.get().getArtifacts().get(0).getArtifactId() + "-agent.jar")));
    assertFalse(Files.exists(debugSupportDir.resolve("runtime-lock").resolve("latest")));
  }

  @Test
  void shouldRejectMalformedBase64UploadPayloadWithBusinessError() throws Exception {
    Path debugSupportDir = Files.createDirectories(tempDir.resolve("debug-support"));
    WorkspaceDebugSessionWorkspaceStorage storage = new WorkspaceDebugSessionWorkspaceStorage(workspaceContext(debugSupportDir));
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.setDevKey("dev-key-001");
    request.getFiles().add(file("agent.jar", "%%%not-base64%%%"));

    Try<DebugArtifactUploadResponseDTO> result = storage.store("alice/dev", request);

    assertTrue(result.isFailure());
    assertTrue(String.valueOf(result.getCause().getMessage()).contains("上传文件内容不是合法Base64"));
  }

  @Test
  void shouldNotLeavePartialBatchRootWhenLaterFileIsMalformed() throws Exception {
    Path debugSupportDir = Files.createDirectories(tempDir.resolve("debug-support"));
    WorkspaceDebugSessionWorkspaceStorage storage = new WorkspaceDebugSessionWorkspaceStorage(workspaceContext(debugSupportDir));
    DebugArtifactUploadRequest request = new DebugArtifactUploadRequest();
    request.setDevKey("dev-key-001");
    request.getFiles().add(file("agent.jar", "aGVsbG8="));
    request.getFiles().add(file("broken.jar", "%%%not-base64%%%"));

    Try<DebugArtifactUploadResponseDTO> result = storage.store("alice/dev", request);

    assertTrue(result.isFailure());
    Path uploadsRoot = debugSupportDir.resolve(new ProfileStorageKeyEncoder().encode("alice/dev"))
        .resolve("runtime-debug")
        .resolve("uploads");
    assertFalse(Files.exists(uploadsRoot));
  }

  private static DebugArtifactUploadRequest.UploadFile file(String fileName, String contentBase64) {
    DebugArtifactUploadRequest.UploadFile file = new DebugArtifactUploadRequest.UploadFile();
    file.setFileName(fileName);
    file.setContentBase64(contentBase64);
    return file;
  }

  private static DevelopWorkspaceContext workspaceContext(Path debugSupportDir) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return debugSupportDir.getParent().toString();
      }

      @Override
      public String getDevRootDir() {
        return debugSupportDir.getParent().toString();
      }

      @Override
      public String getRunRootDir() {
        return debugSupportDir.getParent().toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupportDir.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return debugSupportDir.getParent().toString();
      }
    };
  }
}
