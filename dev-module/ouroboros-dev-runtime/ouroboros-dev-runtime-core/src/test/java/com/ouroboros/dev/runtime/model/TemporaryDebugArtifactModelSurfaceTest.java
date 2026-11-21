package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TemporaryDebugArtifactModelSurfaceTest {

  @Test
  void shouldExposeDebugBinaryUploadAndTemporaryArtifactFields() {
    byte[] content = new byte[] {1, 2, 3};
    DebugBinaryUpload upload = DebugBinaryUpload.builder()
        .fileName("agent.jar")
        .content(content)
        .build();
    TemporaryDebugArtifact artifact = TemporaryDebugArtifact.builder()
        .uploadBatchId("upl-001")
        .artifactId("artifact-001")
        .fileName("agent.jar")
        .relativePath("runtime-debug/uploads/upl-001/files/artifact-001-agent.jar")
        .sha256("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")
        .sizeBytes(Long.valueOf(3L))
        .createdAt(Instant.parse("2026-03-24T10:15:30Z"))
        .build();

    content[0] = 9;

    assertEquals("agent.jar", upload.getFileName());
    assertArrayEquals(new byte[] {1, 2, 3}, upload.getContent());
    assertEquals("upl-001", artifact.getUploadBatchId());
    assertEquals("artifact-001", artifact.getArtifactId());
    assertEquals("agent.jar", artifact.getFileName());
    assertEquals("runtime-debug/uploads/upl-001/files/artifact-001-agent.jar", artifact.getRelativePath());
    assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", artifact.getSha256());
    assertEquals(Long.valueOf(3L), artifact.getSizeBytes());
    assertEquals(Instant.parse("2026-03-24T10:15:30Z"), artifact.getCreatedAt());
  }
}
