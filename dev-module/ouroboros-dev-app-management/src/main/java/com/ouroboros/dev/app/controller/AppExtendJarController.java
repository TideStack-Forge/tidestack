package com.ouroboros.dev.app.controller;

import java.beans.Transient;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.dev.app.service.ExtendJarService;
import com.ouroboros.exception.InternalException;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.model.ChunkResult;
import com.ouroboros.upload.model.StartChunkResult;
import com.ouroboros.upload.model.UploadResult;
import com.ouroboros.upload.model.request.FinishChunkRequest;
import com.ouroboros.upload.model.request.StartChunkRequest;
import com.ouroboros.upload.model.request.UploadChunkRequest;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/extend-jar")
@SuppressWarnings({"unused"})
public class AppExtendJarController {
  @Autowired
  private ExtendJarService extendJarService;

  @Autowired
  @Qualifier("devAppUploadStorageService")
  private UploadStorageService uploadStorageService;

  @GetMapping("")
  public ItemsWebApiResult<ExtendJarService.ExtendJarVO> getList(@PathVariable("appName") String appName) {
    var list = extendJarService.listExtendJars(appName);
    return ItemsWebApiResult.ok(list);
  }

  @PostMapping("")
  public WebApiResult<Map<String, String>> add(@PathVariable("appName") String appName, MultipartFile file) {
    var result = Try.of(file::getInputStream)
        .toEither()
        .flatMap(inputStream -> withContent(inputStream,
            content -> extendJarService.addExtendJar(appName, file.getOriginalFilename(), content)))
        .map(ignore -> Collections.singletonMap("value", file.getOriginalFilename()));
    return WebApiResult.of(result);
  }

  @DeleteMapping("/{jarName}")
  public WebApiResult<Void> delete(@PathVariable("appName") String appName, @PathVariable("jarName") String jarName) {
    var result = extendJarService.deleteExtendJar(appName, jarName);
    return WebApiResult.of(result);
  }

  @PutMapping("/{jarName}")
  public WebApiResult<Map<String, String>> update(@PathVariable("appName") String appName, @PathVariable("jarName") String jarName, MultipartFile file) {
    var result = Try.of(file::getInputStream)
        .toEither()
        .flatMap(inputStream -> withContent(inputStream,
            content -> extendJarService.updateExtendJar(appName, jarName, content)))
        .map(ignore -> Collections.singletonMap("value", file.getOriginalFilename()));
    return WebApiResult.of(result);
  }

  @PostMapping("/start-chunk")
  public WebApiResult<StartChunkResult> startChunk(@RequestBody StartChunkRequest startChunkRequest) {
    var fileName = startChunkRequest.getFileName();

    var result = uploadStorageService.startTempChunk(fileName);
    return WebApiResult.of(result);
  }

  @PostMapping("/chunk")
  public WebApiResult<ChunkResult> uploadChunk(UploadChunkRequestVO uploadChunkRequest) {
    return WebApiResult.of(uploadPart(uploadChunkRequest));
  }

  @PostMapping("/finish-chunk")
  public WebApiResult<UploadResult> finishChunk(@PathVariable String appName, @RequestBody FinishChunkRequest finishChunkRequest) {
    var fileName = finishChunkRequest.getFileName();
    var result = uploadStorageService.finishTempChunk(finishChunkRequest)
        .mapLeft(e -> (Throwable) e)
        .flatMap(completed -> uploadStorageService.getTempFileContent(completed.getTempFile()).mapLeft(e -> (Throwable) e))
        .flatMap(inputStream -> withContent(inputStream, content -> extendJarService.addExtendJar(appName, fileName, content)))
        .map(ignore -> {
          var uploadResult = new UploadResult();
          uploadResult.setValue(fileName);
          return uploadResult;
        });
    return WebApiResult.of(result);
  }

  @PostMapping("/{jarName}/start-chunk")
  public WebApiResult<StartChunkResult> startChunk(@PathVariable("appName") String appName, @PathVariable("jarName") String jarName, @RequestBody StartChunkRequest startChunkRequest) {
    var result = uploadStorageService.startTempChunk(startChunkRequest);
    return WebApiResult.of(result);
  }

  @PostMapping("/{jarName}/chunk")
  public WebApiResult<ChunkResult> uploadChunk(@PathVariable("appName") String appName, @PathVariable("jarName") String jarName, UploadChunkRequestVO uploadChunkRequest) {
    return WebApiResult.of(uploadPart(uploadChunkRequest));
  }

  @PostMapping("/{jarName}/finish-chunk")
  public WebApiResult<UploadResult> finishChunk(@PathVariable("appName") String appName, @PathVariable("jarName") String jarName, @RequestBody FinishChunkRequest finishChunkRequest) {
    var uploadId = finishChunkRequest.getUploadId();
    var fileName = finishChunkRequest.getFileName();
    var partList = finishChunkRequest.getPartList();

    var result = uploadStorageService.finishTempChunk(fileName, uploadId, partList)
        .mapLeft(e -> (Throwable) e)
        .flatMap(completed -> uploadStorageService.getTempFileContent(completed.getTempFile()).mapLeft(e -> (Throwable) e))
        .flatMap(inputStream -> withContent(inputStream, content -> extendJarService.updateExtendJar(appName, jarName, content)))
        .map(ignore -> {
          var uploadResult = new UploadResult();
          uploadResult.setValue(fileName);
          return uploadResult;
        });
    return WebApiResult.of(result);
  }

  private Either<Throwable, ChunkResult> uploadPart(UploadChunkRequestVO request) {
    return Try.of(request::toUploadChunkRequest).toEither()
        .flatMap(chunk -> withContent(chunk.getPartContent(), content -> uploadStorageService.addTempChunk(chunk)));
  }

  private <T> Either<Throwable, T> withContent(InputStream content,
      Function<InputStream, Either<? extends Throwable, T>> operation) {
    try (content) { return operation.apply(content).mapLeft(error -> error); }
    catch (IOException e) { return Either.left(new InternalException("Cannot release development upload content", e)); }
  }


  public static class UploadChunkRequestVO {
    private String uploadId;
    private String key;
    private Integer partNumber;
    private Long partSize;
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
