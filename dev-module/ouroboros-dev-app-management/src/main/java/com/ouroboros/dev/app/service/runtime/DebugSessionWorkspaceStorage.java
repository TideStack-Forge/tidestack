package com.ouroboros.dev.app.service.runtime;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.DebugArtifactUploadRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO;

public interface DebugSessionWorkspaceStorage {

  Try<DebugArtifactUploadResponseDTO> store(String profileId, DebugArtifactUploadRequest request);
}
