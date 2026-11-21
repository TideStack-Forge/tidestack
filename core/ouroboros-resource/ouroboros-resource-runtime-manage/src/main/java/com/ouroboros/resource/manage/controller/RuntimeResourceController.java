package com.ouroboros.resource.manage.controller;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.ouroboros.resource.manage.dto.RuntimeResourceItem;
import com.ouroboros.resource.manage.service.RuntimeResourceManageService;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.web.WebApiResult;

import io.vavr.control.Try;

/**
 * Runtime override management surface for system settings.
 */
@RestController
@RequestMapping("/api/sys/resource/runtime")
public class RuntimeResourceController {

  private static final Logger logger = LoggerFactory.getLogger(RuntimeResourceController.class);

  private final RuntimeResourceManageService resourceService;

  public RuntimeResourceController(RuntimeResourceManageService resourceService) {
    this.resourceService = resourceService;
  }

  @Operation(summary = "列出运行时资源", description = "按资源域、目录和关键字过滤列出运行时资源。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回运行时资源列表")
  })
  @GetMapping
  public ItemsWebApiResult<RuntimeResourceItem> list(
      @Parameter(description = "资源域")
      @RequestParam(value = "scope", required = false) String scope,
      @Parameter(description = "目录路径")
      @RequestParam(value = "directory", required = false) String directory,
      @Parameter(description = "关键字")
      @RequestParam(value = "keyword", required = false) String keyword) {
    logger.debug("Listing runtime resources with scope: {}, directory: {}, keyword: {}", scope, directory, keyword);
    Try<java.util.List<RuntimeResourceItem>> result =
        Try.of(() -> resourceService.list(parseScope(scope), directory, keyword));
    return ItemsWebApiResult.of(result);
  }

  @Operation(summary = "列出运行时资源目录", description = "按资源域列出运行时资源目录树。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回运行时资源目录列表")
  })
  @GetMapping("/directories")
  public ItemsWebApiResult<TreeSelectOption> listDirectories(
      @Parameter(description = "资源域")
      @RequestParam(value = "scope", required = false) String scope) {
    logger.debug("Listing runtime resource directories with scope: {}", scope);
    Try<java.util.List<TreeSelectOption>> result =
        Try.of(() -> resourceService.listDirectories(parseScope(scope)));
    return ItemsWebApiResult.of(result);
  }

  @Operation(summary = "获取运行时资源详情", description = "根据资源域和 key 获取运行时资源详情。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回运行时资源详情"),
      @ApiResponse(responseCode = "404", description = "运行时资源不存在")
  })
  @GetMapping("/effective")
  public WebApiResult<RuntimeResourceItem> getEffective(
      @Parameter(description = "资源域", required = true)
      @RequestParam("scope") String scope,
      @Parameter(description = "资源 key", required = true)
      @RequestParam("key") String key) {
    logger.debug("Getting effective runtime resource: {}/{}", scope, key);
    Try<RuntimeResourceItem> result = Try.of(() -> resourceService.get(parseScope(scope), key));
    return WebApiResult.of(result);
  }

  @Operation(summary = "下载运行时资源内容", description = "下载运行时资源的有效内容。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "资源内容下载成功"),
      @ApiResponse(responseCode = "404", description = "运行时资源不存在")
  })
  @GetMapping("/content")
  public ResponseEntity<byte[]> downloadEffectiveContent(
      @Parameter(description = "资源域", required = true)
      @RequestParam("scope") String scope,
      @Parameter(description = "资源 key", required = true)
      @RequestParam("key") String key) {
    ResourceScope resourceScope;
    try {
      resourceScope = parseScope(scope);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }

    Try<RuntimeResourceItem> itemResult = Try.of(() -> resourceService.get(resourceScope, key));
    Try<byte[]> contentResult = Try.of(() -> resourceService.downloadEffectiveContent(resourceScope, key));
    if (itemResult.isFailure() || contentResult.isFailure()) {
      return ResponseEntity.notFound().build();
    }

    RuntimeResourceItem item = itemResult.get();
    return ResponseEntity.ok()
        .contentType(resolveMediaType(item.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(item.getFileName()))
        .body(contentResult.get());
  }

  @Operation(summary = "上传运行时资源覆盖内容", description = "上传指定资源的运行时覆盖文件内容。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "运行时资源上传成功"),
      @ApiResponse(responseCode = "400", description = "上传参数不合法")
  })
  @PostMapping("/content")
  public WebApiResult<RuntimeResourceItem> upload(
      @Parameter(description = "资源域", required = true)
      @RequestParam("scope") String scope,
      @Parameter(description = "资源 key", required = true)
      @RequestParam("key") String key,
      @Parameter(description = "资源文件", required = true)
      @RequestParam("file") MultipartFile file) {
    String operator = resolveOperator();
    logger.info("Uploading runtime override: {}/{} by {}", scope, key, operator);
    Try<RuntimeResourceItem> result = Try.of(() -> file.getBytes())
        .flatMap(bytes -> Try.of(() -> resourceService.upload(
            parseScope(scope),
            key,
            bytes,
            file.getContentType(),
            file.getOriginalFilename(),
            operator
        )));
    return WebApiResult.of(result);
  }

  @Operation(summary = "删除运行时资源覆盖", description = "删除指定资源的运行时覆盖内容。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "运行时资源覆盖删除成功"),
      @ApiResponse(responseCode = "404", description = "运行时资源不存在")
  })
  @DeleteMapping("/content")
  public WebApiResult<RuntimeResourceItem> deleteOverride(
      @Parameter(description = "资源域", required = true)
      @RequestParam("scope") String scope,
      @Parameter(description = "资源 key", required = true)
      @RequestParam("key") String key) {
    logger.info("Deleting runtime override: {}/{}", scope, key);
    Try<RuntimeResourceItem> result = Try.of(() -> resourceService.deleteOverride(parseScope(scope), key));
    return WebApiResult.of(result);
  }

  @Operation(summary = "恢复默认资源", description = "将指定资源恢复为应用默认内容。", tags = {"resource"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "默认资源恢复成功"),
      @ApiResponse(responseCode = "404", description = "运行时资源不存在")
  })
  @PostMapping("/restore")
  public WebApiResult<RuntimeResourceItem> restoreDefault(
      @Parameter(description = "资源域", required = true)
      @RequestParam("scope") String scope,
      @Parameter(description = "资源 key", required = true)
      @RequestParam("key") String key) {
    logger.info("Restoring runtime resource to app default: {}/{}", scope, key);
    Try<RuntimeResourceItem> result = Try.of(() -> resourceService.restoreDefault(parseScope(scope), key));
    return WebApiResult.of(result);
  }

  private ResourceScope parseScope(String scope) {
    return StringUtils.isBlank(scope) ? null : ResourceScope.fromValue(scope);
  }

  private static MediaType resolveMediaType(String contentType) {
    if (contentType == null || contentType.trim().isEmpty()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (Exception e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private static String buildContentDisposition(String fileName) {
    String safeFileName = (fileName == null || fileName.trim().isEmpty() ? "resource.bin" : fileName)
        .replace("\"", "");
    return "attachment; filename=\"" + safeFileName + "\"";
  }

  private static String resolveOperator() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return "system";
    }
    Object principal = authentication.getPrincipal();
    String operator = principal instanceof String
        ? (String) principal
        : StringUtils.defaultIfBlank(authentication.getName(), null);
    if (StringUtils.isBlank(operator) || "anonymousUser".equalsIgnoreCase(operator)) {
      return "system";
    }
    return operator;
  }
}
