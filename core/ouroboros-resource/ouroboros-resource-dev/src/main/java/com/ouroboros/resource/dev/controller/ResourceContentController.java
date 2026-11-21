package com.ouroboros.resource.dev.controller;

import java.io.IOException;

import com.ouroboros.resource.dev.dto.ResourceContentUploadRequest;
import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.dev.service.ResourceDevService;
import com.ouroboros.resource.util.ResourcePathUtils;
import com.ouroboros.web.WebApiResult;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dev/{appName}/resource/content")
public class ResourceContentController {

  private final ResourceDevService resourceDevService;

  public ResourceContentController(ResourceDevService resourceDevService) {
    this.resourceDevService = resourceDevService;
  }

  @PostMapping
  public WebApiResult<Void> upload(@PathVariable("appName") String appName,
                                   ResourceContentUploadRequest request,
                                   @RequestParam("file") MultipartFile file) {
    Try<Void> result = Try.of(request::getScopedPath)
        .flatMap(fullName -> Try.of(file::getInputStream)
            .flatMap(inputStream -> resourceDevService.uploadResourceFile(
                fullName,
                inputStream,
                file.getContentType(),
                file.getOriginalFilename()
            )));
    return WebApiResult.of(result);
  }

  @GetMapping
  public ResponseEntity<byte[]> download(@PathVariable("appName") String appName,
                                         ResourceContentUploadRequest request) {
    String fullName;
    try {
      fullName = request.getScopedPath();
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }

    Try<byte[]> result = resourceDevService.downloadResourceFile(fullName);
    if (result.isFailure()) {
      return ResponseEntity.badRequest().build();
    }

    ResourceModel model = resourceDevService.getOne(fullName).orElse(null);
    String contentType = model == null ? null : model.getContentType();
    String fileName = model == null ? null : model.getFileName();

    return ResponseEntity.ok()
        .contentType(resolveMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(
            StringUtils.defaultIfBlank(fileName, ResourcePathUtils.getFileName(fullName))))
        .body(result.get());
  }

  @DeleteMapping
  public WebApiResult<Void> delete(@PathVariable("appName") String appName,
                                   ResourceContentUploadRequest request) {
    Try<Void> result = Try.of(request::getScopedPath)
        .flatMap(resourceDevService::deleteResourceFile);
    return WebApiResult.of(result);
  }

  private static MediaType resolveMediaType(String contentType) {
    if (StringUtils.isBlank(contentType)) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (Exception e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private static String buildContentDisposition(String fileName) {
    String safeFileName = StringUtils.defaultIfBlank(fileName, "resource.bin")
        .replace("\"", "");
    return "attachment; filename=\"" + safeFileName + "\"";
  }
}
