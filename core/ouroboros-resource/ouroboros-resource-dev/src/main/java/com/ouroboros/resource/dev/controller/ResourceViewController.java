package com.ouroboros.resource.dev.controller;

import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.dev.service.ResourceDevService;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.util.ResourcePathUtils;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/{appName}/resource/view")
public class ResourceViewController {

  private final ResourceDevService resourceDevService;

  public ResourceViewController(ResourceDevService resourceDevService) {
    this.resourceDevService = resourceDevService;
  }

  @GetMapping
  public ResponseEntity<byte[]> view(@PathVariable("appName") String appName,
                                     @RequestParam("key") String key) {
    String normalizedKey = ResourcePathUtils.toResourceKey(ResourcePathUtils.normalize(key));
    if (StringUtils.isBlank(normalizedKey) || normalizedKey.contains("..")) {
      return ResponseEntity.badRequest().build();
    }

    String fullName = ResourcePathUtils.toScopedResourcePath(ResourceScope.FRONTEND, normalizedKey);
    ResourceModel model = resourceDevService.getOne(fullName).orElse(null);
    Try<byte[]> contentResult = resourceDevService.downloadResourceFile(fullName);
    if (model == null || contentResult.isFailure()) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok()
        .contentType(resolveMediaType(model.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(
            StringUtils.defaultIfBlank(model.getFileName(), ResourcePathUtils.getFileName(normalizedKey))))
        .body(contentResult.get());
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
    return "inline; filename=\"" + safeFileName + "\"";
  }
}
