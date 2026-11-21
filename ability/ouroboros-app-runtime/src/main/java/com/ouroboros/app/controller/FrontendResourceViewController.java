package com.ouroboros.app.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.ouroboros.AppContext;
import com.ouroboros.exception.InternalException;
import com.ouroboros.model.AppInfo;
import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceDescriptor;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.spi.ResourceService;
import com.ouroboros.security.annotation.Anonymous;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@ConditionalOnBean(ResourceService.class)
@RequestMapping("/api/resource/view")
public class FrontendResourceViewController {

  private final ResourceService resourceService;
  private final AppContext appContext;

  public FrontendResourceViewController(ResourceService resourceService, AppContext appContext) {
    this.resourceService = resourceService;
    this.appContext = appContext;
  }

  @Operation(
      summary = "查看前端资源文件",
      description = "根据资源 key 读取前端资源内容，并以内联文件形式返回给浏览器。",
      tags = {"app-runtime"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回前端资源内容"),
      @ApiResponse(responseCode = "400", description = "资源 key 非法"),
      @ApiResponse(responseCode = "404", description = "未找到对应前端资源")
  })
  @GetMapping
  @Anonymous
  public ResponseEntity<byte[]> view(
      @Parameter(description = "前端资源 key，不能包含 frontend/、backend/ 或路径穿越片段", required = true)
      @RequestParam("key") String key) {
    String normalizedKey = normalizeKey(key);
    if (normalizedKey == null) {
      return ResponseEntity.badRequest().build();
    }

    String appName = appContext.getAppInfo()
        .map(AppInfo::getName)
        .filter(StringUtils::isNotBlank)
        .orElse(null);
    ResourceResolveContext context = new ResourceResolveContext(appName, ResourceScope.FRONTEND, normalizedKey);
    ResourceReadResult result = resourceService.read(context);
    Resource resource = result == null ? null : result.getEffectiveResource();
    ResourceDescriptor descriptor = result == null ? null : result.getEffectiveDescriptor();
    if (resource == null || descriptor == null || resource.getContent() == null) {
      return ResponseEntity.notFound().build();
    }

    try (InputStream inputStream = resource.getContent()) {
      return ResponseEntity.ok()
          .contentType(resolveMediaType(descriptor.getContentType()))
          .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(descriptor.getFileName()))
          .body(IOUtils.toByteArray(inputStream));
    } catch (IOException e) {
      throw new InternalException("前端资源读取失败", e);
    }
  }

  private static String normalizeKey(String key) {
    String normalized = StringUtils.stripToNull(key);
    if (normalized == null) {
      return null;
    }
    normalized = StringUtils.strip(normalized, "/");
    if (StringUtils.isBlank(normalized)
        || StringUtils.contains(normalized, "..")
        || StringUtils.startsWith(normalized, "frontend/")
        || StringUtils.startsWith(normalized, "backend/")) {
      return null;
    }
    return normalized;
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
