package com.ouroboros.dev.app.service.runtime;

import java.lang.IllegalArgumentException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.DebugArtifactUploadRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO;
import com.ouroboros.dev.app.model.TemporaryDebugArtifactDTO;
import com.ouroboros.dev.runtime.model.DebugBinaryUpload;
import com.ouroboros.dev.runtime.model.TemporaryDebugArtifact;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;

@Service
public class WorkspaceDebugSessionWorkspaceStorage implements DebugSessionWorkspaceStorage {
  private final DevelopWorkspaceContext developWorkspaceContext;
  private final ProfileStorageKeyEncoder profileStorageKeyEncoder;

  @Autowired
  public WorkspaceDebugSessionWorkspaceStorage(DevelopWorkspaceContext developWorkspaceContext) {
    this(developWorkspaceContext, new ProfileStorageKeyEncoder());
  }

  WorkspaceDebugSessionWorkspaceStorage(DevelopWorkspaceContext developWorkspaceContext,
                                        ProfileStorageKeyEncoder profileStorageKeyEncoder) {
    this.developWorkspaceContext = developWorkspaceContext;
    this.profileStorageKeyEncoder = profileStorageKeyEncoder;
  }

  @Override
  public Try<DebugArtifactUploadResponseDTO> store(String profileId, DebugArtifactUploadRequest request) {
    return Try.of(() -> doStore(profileId, request));
  }

  private DebugArtifactUploadResponseDTO doStore(String profileId, DebugArtifactUploadRequest request) throws Exception {
    if (StringUtils.isBlank(profileId)) {
      throw new BusinessException("profileId 不能为空");
    }
    if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
      throw new BusinessException("files 不能为空");
    }

    List<DebugBinaryUpload> uploads = new ArrayList<DebugBinaryUpload>();
    for (DebugArtifactUploadRequest.UploadFile file : request.getFiles()) {
      uploads.add(toUpload(file));
    }

    String uploadBatchId = buildUploadBatchId();
    Path batchRoot = Paths.get(developWorkspaceContext.getDebugSupportFilesDir(),
        profileStorageKeyEncoder.encode(profileId),
        "runtime-debug",
        "uploads",
        uploadBatchId);
    Path filesRoot = batchRoot.resolve("files");
    Files.createDirectories(filesRoot);

    try {
      DebugArtifactUploadResponseDTO response = new DebugArtifactUploadResponseDTO();
      response.setUploadBatchId(uploadBatchId);
      Map<String, Object> manifest = new LinkedHashMap<String, Object>();
      manifest.put("profileId", profileId);
      manifest.put("uploadBatchId", uploadBatchId);
      manifest.put("artifacts", response.getArtifacts());

      for (DebugBinaryUpload upload : uploads) {
        TemporaryDebugArtifact artifact = writeArtifact(uploadBatchId, filesRoot, upload);
        response.getArtifacts().add(toDto(artifact));
      }
      Files.write(batchRoot.resolve("manifest.json"), Json.toPrettyJsonString(manifest).getBytes(StandardCharsets.UTF_8));
      return response;
    } catch (Exception exception) {
      deleteRecursively(batchRoot);
      throw exception;
    }
  }

  private DebugBinaryUpload toUpload(DebugArtifactUploadRequest.UploadFile file) {
    if (file == null || StringUtils.isBlank(file.getFileName()) || StringUtils.isBlank(file.getContentBase64())) {
      throw new BusinessException("上传文件不能为空");
    }
    try {
      return DebugBinaryUpload.builder()
          .fileName(file.getFileName())
          .content(Base64.getDecoder().decode(file.getContentBase64()))
          .build();
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("上传文件内容不是合法Base64");
    }
  }

  private TemporaryDebugArtifact writeArtifact(String uploadBatchId, Path filesRoot, DebugBinaryUpload upload) throws Exception {
    String artifactId = UUID.randomUUID().toString();
    String sanitizedFileName = sanitizeFileName(upload.getFileName());
    Path filePath = filesRoot.resolve(artifactId + "-" + sanitizedFileName);
    Files.write(filePath, upload.getContent());
    return TemporaryDebugArtifact.builder()
        .uploadBatchId(uploadBatchId)
        .artifactId(artifactId)
        .fileName(upload.getFileName())
        .relativePath("runtime-debug/uploads/" + uploadBatchId + "/files/" + artifactId + "-" + sanitizedFileName)
        .sha256(sha256(upload.getContent()))
        .sizeBytes(Long.valueOf(upload.getContent().length))
        .createdAt(Instant.now())
        .build();
  }

  private TemporaryDebugArtifactDTO toDto(TemporaryDebugArtifact artifact) {
    TemporaryDebugArtifactDTO dto = new TemporaryDebugArtifactDTO();
    dto.setArtifactId(artifact.getArtifactId());
    dto.setFileName(artifact.getFileName());
    dto.setRelativePath(artifact.getRelativePath());
    dto.setSha256(artifact.getSha256());
    dto.setSizeBytes(artifact.getSizeBytes());
    dto.setCreatedAt(artifact.getCreatedAt());
    return dto;
  }

  private void deleteRecursively(Path path) throws Exception {
    if (path == null || !Files.exists(path)) {
      return;
    }
    Files.walk(path)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(current -> {
          try {
            Files.deleteIfExists(current);
          } catch (Exception exception) {
            throw new InternalException("删除调试会话工作区文件失败", exception);
          }
        });
  }

  private String buildUploadBatchId() {
    return "upl-" + UUID.randomUUID().toString();
  }

  private String sanitizeFileName(String fileName) {
    return fileName.replace("/", "-").replace("\\", "-");
  }

  private String sha256(byte[] bytes) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(bytes);
    StringBuilder builder = new StringBuilder();
    for (byte value : hash) {
      builder.append(String.format("%02x", value));
    }
    return builder.toString();
  }
}
