package com.ouroboros.i18n.dev.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.dev.dto.I18nLocaleExportResult;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncRequest;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncResult;
import com.ouroboros.i18n.dev.model.I18nLocaleModel;
import com.ouroboros.i18n.dev.service.I18nLocaleDevService;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/i18n")
public class I18nLocaleDevManageController {
  private final I18nLocaleDevService i18nLocaleDevService;

  public I18nLocaleDevManageController(I18nLocaleDevService i18nLocaleDevService) {
    this.i18nLocaleDevService = i18nLocaleDevService;
  }

  @GetMapping("/template/export")
  public ResponseEntity<byte[]> exportTemplate(@PathVariable String appName) {
    return buildDownloadResponse(i18nLocaleDevService.exportTemplate());
  }

  @GetMapping("/{locale}/export")
  public ResponseEntity<byte[]> exportLocale(@PathVariable String appName, @PathVariable String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    return buildDownloadResponse(i18nLocaleDevService.exportLocale(locale));
  }

  @PostMapping("/import")
  public WebApiResult<I18nLocaleModel> importLocale(@PathVariable String appName, @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return WebApiResult.fail("文件不能为空");
    }

    Try<I18nLocaleModel> result = Try.of(() -> new String(file.getBytes(), StandardCharsets.UTF_8))
        .flatMap(content -> Json.tryToBean(I18nLocaleModel.class, content))
        .flatMap(model -> model == null
            ? Try.failure(new BusinessException("文件格式错误，无法解析语言文件"))
            : i18nLocaleDevService.importLocale(model));

    return WebApiResult.of(result);
  }

  @PostMapping("/{locale}/sync")
  public WebApiResult<I18nLocaleSyncResult> syncFromMetadata(@PathVariable String appName,
                                                             @PathVariable String locale,
                                                             @RequestBody(required = false) I18nLocaleSyncRequest request) {
    if (locale == null || locale.trim().isEmpty()) {
      return WebApiResult.fail("locale 不能为空");
    }
    boolean removeOrphaned = request != null && Boolean.TRUE.equals(request.getRemoveOrphaned());
    return WebApiResult.of(i18nLocaleDevService.syncFromMetadata(locale, removeOrphaned));
  }

  private ResponseEntity<byte[]> buildDownloadResponse(Try<I18nLocaleExportResult> result) {
    return result.fold(
        error -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(),
        exportResult -> {
          byte[] content = exportResult.getContent().getBytes(StandardCharsets.UTF_8);
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(resolveContentType(exportResult.getContentType()));
          headers.setContentDisposition(
              ContentDisposition.attachment()
                  .filename(exportResult.getFilename(), StandardCharsets.UTF_8)
                  .build()
          );

          return ResponseEntity.ok()
              .headers(headers)
              .body(content);
        }
    );
  }

  private MediaType resolveContentType(String contentType) {
    if (contentType == null || contentType.trim().isEmpty()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    return MediaType.parseMediaType(contentType);
  }
}
