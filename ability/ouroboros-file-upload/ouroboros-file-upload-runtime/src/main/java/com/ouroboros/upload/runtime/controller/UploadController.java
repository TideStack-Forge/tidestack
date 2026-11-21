package com.ouroboros.upload.runtime.controller;

import java.util.*;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.controller.AbstractUploadController;
import com.ouroboros.upload.controller.UploadRequestContext;

import com.ouroboros.upload.generator.*;
import com.ouroboros.upload.model.ChunkResult;
import com.ouroboros.upload.model.StartChunkResult;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.upload.model.UploadResult;
import com.ouroboros.upload.model.request.FinishChunkRequest;
import com.ouroboros.upload.model.request.StartChunkRequest;
import com.ouroboros.upload.runtime.config.UploadProperties;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/upload")
public class UploadController extends AbstractUploadController {

  private final UploadProperties uploadProperties;

  private final UploadStorageService uploadStorageService;

  private final FileAccessUrlGenerator fileAccessUrlGenerator;

  private final StorageFileNameGenerator storageFileNameGenerator;

  private final StorageFilePathGenerator storageFilePathGenerator;

  private final Map<String, String> context;

  public UploadController(UploadProperties uploadProperties, @org.springframework.beans.factory.annotation.Qualifier("uploadStorageService") UploadStorageService uploadStorageService) {
    this.uploadProperties = uploadProperties;
    this.uploadStorageService = uploadStorageService;
    this.fileAccessUrlGenerator = buildFileAccessUrlGenerator();
    this.storageFileNameGenerator = buildStorageFileNameGenerator();
    this.storageFilePathGenerator = buildStorageFilePathGenerator();
    this.context = buildContext();
  }

  private FileAccessUrlGenerator buildFileAccessUrlGenerator() {
    var urlStrategy = uploadProperties.getFileUrlStrategy();
    return FileAccessUrlGeneratorBuilder.build(urlStrategy).orElse(null);
  }

  private StorageFileNameGenerator buildStorageFileNameGenerator() {
    var fileNameStrategy = uploadProperties.getFileNameStrategy();
    return StorageFileNameGeneratorBuilder.build(fileNameStrategy).orElse(null);
  }

  private StorageFilePathGenerator buildStorageFilePathGenerator() {
    var filePathStrategy = uploadProperties.getFilePathStrategy();
    return StorageFilePathGeneratorBuilder.build(filePathStrategy).orElse(null);
  }

  private Map<String, String> buildContext() {
    var map = new TreeMap<String, String>(String::compareToIgnoreCase);
    map.put("fileUrlPrefix", uploadProperties.getFileUrlPrefix());
    map.put("filePathPrefix", uploadProperties.getFilePathPrefix());
    map.put("filePathTemplate", uploadProperties.getFilePathTemplate());
    map.put("fileNameTemplate", uploadProperties.getFileNameTemplate());
    return map;
  }

  @Override
  protected UploadStorageService getUploadStorageService() {
    return uploadStorageService;
  }

  @Override
  protected FileAccessUrlGenerator getFileAccessUrlGenerator(Map<String, String> context) {
    var customUrlStrategy = context.get("fileUrlStrategy");
    var generator = FileAccessUrlGeneratorBuilder.build(customUrlStrategy).orElse(fileAccessUrlGenerator);
    return generator == null ? (url, ctx) -> url : generator;
  }

  @Override
  protected StorageFilePathGenerator getStorageFilePathGenerator(Map<String, String> context) {
    var customFilePathStrategy = context.get("filePathStrategy");
    var generator = StorageFilePathGeneratorBuilder.build(customFilePathStrategy).orElse(storageFilePathGenerator);
    return generator == null ? (rawName, ctx) -> "" : generator;
  }

  @Override
  protected StorageFileNameGenerator getStorageFileNameGenerator(Map<String, String> context) {
    var customFileNameStrategy = context.get("fileNameStrategy");
    var generator = StorageFileNameGeneratorBuilder.build(customFileNameStrategy).orElse(storageFileNameGenerator);
    return generator == null ? (rawName, filePath, ctx) -> rawName : generator;
  }

  @Override
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  protected Map<String, String> buildContext(HttpServletRequest request) {
    final var requestContext = new UploadRequestContext(request);
    return new Map<>() {
      @Override
      public int size() {
        return 0;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public boolean containsKey(Object key) {
        return requestContext.containsKey(key) || context.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
        return false;
      }

      @Override
      public String get(Object key) {
        return Optional.ofNullable(requestContext.get(key)).orElse(context.get(key));
      }

      @Override
      public String put(String key, String value) {
        return null;
      }

      @Override
      public String remove(Object key) {
        return null;
      }

      @Override
      public void putAll(Map<? extends String, ? extends String> m) {

      }

      @Override
      public void clear() {

      }

      @Override

      public Set<String> keySet() {
        return Collections.emptySet();
      }

      @Override

      public Collection<String> values() {
        return Collections.emptyList();
      }

      @Override

      public Set<Entry<String, String>> entrySet() {
        return Collections.emptySet();
      }
    };
  }

  @Override
  @PermitAll
  @Operation(
      summary = "上传文件",
      description = "上传单个正式文件并返回访问地址。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件上传成功"),
      @ApiResponse(responseCode = "400", description = "上传文件参数不合法")
  })
  public WebApiResult<UploadResult> upload(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "正式文件上传表单", required = true)
      @Parameter(description = "待上传的正式文件", required = true)
      MultipartFile file,
      HttpServletRequest request) {
    return super.upload(file, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "初始化分片上传",
      description = "为正式文件创建分片上传会话。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "分片上传初始化成功"),
      @ApiResponse(responseCode = "400", description = "分片上传参数不合法")
  })
  public WebApiResult<StartChunkResult> startChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "分片上传初始化请求", required = true)
      @RequestBody StartChunkRequest startChunkRequest,
      HttpServletRequest request) {
    return super.startChunk(startChunkRequest, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "上传正式文件分片",
      description = "上传正式文件的单个分片内容。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件分片上传成功"),
      @ApiResponse(responseCode = "400", description = "文件分片参数不合法")
  })
  public WebApiResult<ChunkResult> uploadChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "分片上传表单请求", required = true)
      UploadChunkRequestVO uploadChunkRequest,
      HttpServletRequest request) {
    return super.uploadChunk(uploadChunkRequest, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "完成分片上传",
      description = "合并正式文件的全部分片并返回上传结果。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "分片文件合并成功"),
      @ApiResponse(responseCode = "400", description = "分片完成参数不合法")
  })
  public WebApiResult<UploadResult> finishChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "分片上传完成请求", required = true)
      @RequestBody FinishChunkRequest finishChunkRequest,
      HttpServletRequest request) {
    return super.finishChunk(finishChunkRequest, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "上传临时文件",
      description = "上传单个临时文件并返回临时访问结果。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "临时文件上传成功"),
      @ApiResponse(responseCode = "400", description = "上传文件参数不合法")
  })
  public WebApiResult<TempUploadResult> uploadTemp(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "临时文件上传表单", required = true)
      @Parameter(description = "待上传的临时文件", required = true)
      MultipartFile file,
      HttpServletRequest request) {
    return super.uploadTemp(file, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "初始化临时文件分片上传",
      description = "为临时文件创建分片上传会话。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "临时分片上传初始化成功"),
      @ApiResponse(responseCode = "400", description = "分片上传参数不合法")
  })
  public WebApiResult<StartChunkResult> startTempChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "临时文件分片初始化请求", required = true)
      @RequestBody StartChunkRequest startChunkRequest,
      HttpServletRequest request) {
    return super.startTempChunk(startChunkRequest, request);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "上传临时文件分片",
      description = "上传临时文件的单个分片内容。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "临时文件分片上传成功"),
      @ApiResponse(responseCode = "400", description = "文件分片参数不合法")
  })
  public WebApiResult<ChunkResult> uploadTempChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "临时文件分片上传表单请求", required = true)
      UploadChunkRequestVO uploadChunkRequest) {
    return super.uploadTempChunk(uploadChunkRequest);
  }

  @Override
  @PermitAll
  @Operation(
      summary = "完成临时文件分片上传",
      description = "合并临时文件的全部分片并返回上传结果。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "临时分片文件合并成功"),
      @ApiResponse(responseCode = "400", description = "分片完成参数不合法")
  })
  public WebApiResult<TempUploadResult> finishTempChunk(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "临时文件分片完成请求", required = true)
      @RequestBody FinishChunkRequest finishChunkRequest,
      HttpServletRequest request) {
    return super.finishTempChunk(finishChunkRequest, request);
  }
}
