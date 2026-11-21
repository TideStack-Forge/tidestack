package com.ouroboros.upload.controller;

import java.beans.Transient;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import java.nio.file.Paths;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.control.Either;

import com.ouroboros.exception.InternalException;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.generator.FileAccessUrlGenerator;
import com.ouroboros.upload.generator.StorageFileNameGenerator;
import com.ouroboros.upload.generator.StorageFilePathGenerator;
import com.ouroboros.upload.model.ChunkResult;
import com.ouroboros.upload.model.StartChunkResult;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.upload.model.UploadResult;
import com.ouroboros.upload.model.request.FinishChunkRequest;
import com.ouroboros.upload.model.request.StartChunkRequest;
import com.ouroboros.upload.model.request.UploadChunkRequest;
import com.ouroboros.web.WebApiResult;

@SuppressWarnings("unused")
public abstract class AbstractUploadController {
  abstract protected UploadStorageService getUploadStorageService();

  abstract protected FileAccessUrlGenerator getFileAccessUrlGenerator(Map<String, String> context);

  abstract protected StorageFilePathGenerator getStorageFilePathGenerator(Map<String, String> context);

  abstract protected StorageFileNameGenerator getStorageFileNameGenerator(Map<String, String> context);

  abstract protected Map<String, String> buildContext(HttpServletRequest request);

  @PostMapping("/file")
  public WebApiResult<UploadResult> upload(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
    var rawName = file.getOriginalFilename();
    var context = buildContext(request);
    var filePath = getStorageFilePathGenerator(context).generatorPath(rawName, context);
    var fileName = getStorageFileNameGenerator(context).generatorName(rawName, filePath, context);
    var fullPath = Paths.get(filePath, fileName);
    var url = getFileAccessUrlGenerator(context).generateUrl(fullPath.toString(), context);

    var result = withContent(file, content -> getUploadStorageService().addFile(fullPath, content))
        .map(ignore -> {
          var uploadResult = new UploadResult();
          uploadResult.setName(rawName);
          uploadResult.setUrl(url);
          uploadResult.setValue(url);
          return uploadResult;
        });
    return WebApiResult.of(result);
  }

  @PostMapping({"/start-chunk", "/startChunk"})
  public WebApiResult<StartChunkResult> startChunk(@RequestBody StartChunkRequest startChunkRequest, HttpServletRequest request) {
    var context = buildContext(request);
    var rawName = startChunkRequest.getFileName();
    var filePath = getStorageFilePathGenerator(context).generatorPath(rawName, context);
    var fileName = getStorageFileNameGenerator(context).generatorName(rawName, filePath, context);
    var fullPath = Paths.get(filePath, fileName);

    startChunkRequest.setFileName(fullPath.toString());
    startChunkRequest.setName(fileName);
    var result = getUploadStorageService().startChunk(startChunkRequest);
    return WebApiResult.of(result);
  }

  @PostMapping({"/chunk", "/upload-chunk", "/uploadChunk"})
  public WebApiResult<ChunkResult> uploadChunk(UploadChunkRequestVO uploadChunkRequest, HttpServletRequest request) {
    var result = withChunk(uploadChunkRequest, chunk -> getUploadStorageService().addChunk(chunk));
    return WebApiResult.of(result);
  }

  @PostMapping({"/finish-chunk", "/finishChunk"})
  public WebApiResult<UploadResult> finishChunk(@RequestBody FinishChunkRequest finishChunkRequest, HttpServletRequest request) {
    var context = buildContext(request);
    var filePath = finishChunkRequest.getKey();
    var fullPath = Paths.get(filePath);
    var fileName = fullPath.getFileName().toString();
    var rawName = finishChunkRequest.getFileName();
    var url = getFileAccessUrlGenerator(context).generateUrl(fullPath.toString(), context);

    finishChunkRequest.setFileName(fullPath.toString());
    var result = getUploadStorageService()
        .finishChunk(finishChunkRequest)
        .peek(uploadResult -> {
          uploadResult.setName(rawName);
          uploadResult.setUrl(url);
          uploadResult.setValue(url);
        });
    return WebApiResult.of(result);
  }

  @PostMapping({"/upload-temp", "/uploadTemp"})
  public WebApiResult<TempUploadResult> uploadTemp(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
    var rawName = file.getOriginalFilename();

    var result = withContent(file, content -> getUploadStorageService().addTempFile(rawName, content));
    return WebApiResult.of(result);
  }

  @PostMapping({"/start-temp-chunk", "/startTempChunk"})
  public WebApiResult<StartChunkResult> startTempChunk(@RequestBody StartChunkRequest startChunkRequest, HttpServletRequest request) {
    var result = getUploadStorageService().startTempChunk(startChunkRequest);
    return WebApiResult.of(result);
  }

  @PostMapping({"/temp-chunk", "/tempChunk", "/upload-temp-chunk", "/uploadTempChunk"})
  public WebApiResult<ChunkResult> uploadTempChunk(UploadChunkRequestVO uploadChunkRequest) {
    var result = withChunk(uploadChunkRequest, chunk -> getUploadStorageService().addTempChunk(chunk));
    return WebApiResult.of(result);
  }

  @PostMapping({"/finish-temp-chunk", "/finishTempChunk"})
  public WebApiResult<TempUploadResult> finishTempChunk(@RequestBody FinishChunkRequest finishChunkRequest, HttpServletRequest request) {
    var result = getUploadStorageService()
        .finishTempChunk(finishChunkRequest);
    return WebApiResult.of(result);
  }

  private <T> Either<Throwable, T> withContent(MultipartFile file,
      Function<InputStream, Either<? extends Throwable, T>> operation) {
    try (var content = file.getInputStream()) { return operation.apply(content).mapLeft(error -> error); }
    catch (IOException e) { return Either.left(new InternalException("Cannot release upload request content", e)); }
  }

  private Either<Throwable, ChunkResult> withChunk(UploadChunkRequestVO request,
      Function<UploadChunkRequest, Either<? extends Throwable, ChunkResult>> operation) {
    try {
      var chunk = request.toUploadChunkRequest();
      try (var content = chunk.getPartContent()) { return operation.apply(chunk).mapLeft(error -> error); }
    } catch (IOException e) { return Either.left(new InternalException("Cannot read upload request content", e)); }
  }

  public static class UploadChunkRequestVO {
    @Schema(description = "分片上传会话 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uploadId;
    @Schema(description = "分片文件存储键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;
    @Schema(description = "当前分片序号，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer partNumber;
    @Schema(description = "当前分片大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long partSize;
    @Schema(description = "当前分片文件内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile file;

    public String getUploadId() {
      return uploadId;
    }

    public void setUploadId(String uploadId) {
      this.uploadId = uploadId;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public Integer getPartNumber() {
      return partNumber;
    }

    public void setPartNumber(Integer partNumber) {
      this.partNumber = partNumber;
    }

    public Long getPartSize() {
      return partSize;
    }

    public void setPartSize(Long partSize) {
      this.partSize = partSize;
    }

    public MultipartFile getFile() {
      return file;
    }

    public void setFile(MultipartFile file) {
      this.file = file;
    }

    @Transient
    public UploadChunkRequest toUploadChunkRequest() throws IOException {
      var uploadChunkRequest = new UploadChunkRequest();
      uploadChunkRequest.setKey(key);
      uploadChunkRequest.setPartNumber(partNumber);
      uploadChunkRequest.setPartSize(partSize);
      uploadChunkRequest.setUploadId(uploadId);
      uploadChunkRequest.setPartContent(file.getInputStream());
      return uploadChunkRequest;
    }
  }
}
