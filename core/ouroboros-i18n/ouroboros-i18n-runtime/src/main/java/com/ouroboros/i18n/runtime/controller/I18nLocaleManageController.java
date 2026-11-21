package com.ouroboros.i18n.runtime.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.runtime.dto.ExportResult;
import com.ouroboros.i18n.runtime.dto.I18nLanguageSummary;
import com.ouroboros.i18n.runtime.dto.SyncRequest;
import com.ouroboros.i18n.runtime.dto.SyncResult;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.i18n.runtime.service.manage.I18nLocaleManageService;
import com.ouroboros.security.annotation.Authority;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

/**
 * 国际化字典管理 Controller
 */
@RestController
@RequestMapping("/api/i18n/manage")
public class I18nLocaleManageController {

  private static final Logger logger = LoggerFactory.getLogger(I18nLocaleManageController.class);

  @Autowired
  private I18nLocaleManageService manageService;

  /**
   * 获取所有语言列表（包括禁用的）
   * 用于管理界面
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "列出全部语言", description = "返回国际化管理界面的全部语言列表。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回语言列表")
  })
  @GetMapping("/languages")
  public WebApiResult<List<I18nLanguageSummary>> getAllLanguages() {
    try {
      List<I18nLanguageSummary> languages = manageService.getAllLanguages();
      return WebApiResult.success(languages);
    } catch (Exception e) {
      logger.error("Error getting all languages", e);
      return WebApiResult.fail("获取所有语言列表失败");
    }
  }

  /**
   * 获取语言详情
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "获取语言详情", description = "根据语言代码获取国际化语言详情。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回语言详情"),
      @ApiResponse(responseCode = "404", description = "语言不存在")
  })
  @GetMapping("/languages/{locale}")
  public WebApiResult<I18nLocale> getLanguageDetail(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return WebApiResult.fail("语言代码不能为空");
    }

    try {
      Optional<I18nLocale> language = manageService.getLanguageDetail(locale);
      if (language.isPresent()) {
        return WebApiResult.success(language.get());
      } else {
        return WebApiResult.fail("语言不存在: " + locale);
      }
    } catch (Exception e) {
      logger.error("Error getting language detail for: {}", locale, e);
      return WebApiResult.fail("获取语言详情失败");
    }
  }

  /**
   * 导出语言文件
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "导出语言文件", description = "导出指定语言代码的语言文件。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "语言文件导出成功"),
      @ApiResponse(responseCode = "404", description = "语言不存在")
  })
  @GetMapping("/languages/{locale}/export")
  public ResponseEntity<byte[]> exportLanguage(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    try {
      var result = manageService.exportLanguage(locale);
      if (result.isRight()) {
        ExportResult exportResult = result.get();
        byte[] content = exportResult.getContent().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(exportResult.getFilename(), StandardCharsets.UTF_8)
                .build()
        );

        return ResponseEntity.ok()
            .headers(headers)
            .body(content);
      } else {
        logger.warn("Export failed for locale: {}, error: {}", locale, result.getLeft().getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
    } catch (Exception e) {
      logger.error("Error exporting language: {}", locale, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * 导出翻译模板
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "导出翻译模板", description = "导出用于翻译的语言模板文件。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "翻译模板导出成功")
  })
  @GetMapping("/template/export")
  public ResponseEntity<byte[]> exportTemplate() {
    try {
      var result = manageService.exportTemplate();
      if (result.isRight()) {
        ExportResult exportResult = result.get();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", exportResult.getFilename());

        return ResponseEntity.ok()
            .headers(headers)
            .body(exportResult.getContent().getBytes(StandardCharsets.UTF_8));
      } else {
        OuroborosException error = result.getLeft();
        logger.warn("Export template failed, error: {}", error.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
    } catch (Exception e) {
      logger.error("Error exporting template", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * 保存字典
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "保存语言包", description = "保存或更新一个国际化语言包。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "语言包保存成功"),
      @ApiResponse(responseCode = "400", description = "语言包参数不合法")
  })
  @PostMapping("/locale")
  public WebApiResult<Boolean> saveLocale(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "语言包定义", required = true)
      @RequestBody I18nLocale locale) {
    if (locale == null || locale.getLocale() == null) {
      return WebApiResult.fail("参数不能为空");
    }

    return manageService.saveLocale(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 从提取器同步文案
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "同步提取器文案", description = "从文案提取器同步指定语言的国际化文案。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文案同步成功"),
      @ApiResponse(responseCode = "400", description = "同步参数不合法")
  })
  @PostMapping("/sync")
  public WebApiResult<SyncResult> syncFromExtractors(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "文案同步请求", required = true)
      @RequestBody SyncRequest request) {
    if (request.getLocale() == null) {
      return WebApiResult.fail("locale 不能为空");
    }

    return manageService.syncFromExtractors(request.getLocale(), request.getRemoveOrphaned())
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 导入语言文件
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "导入语言文件", description = "上传并导入国际化语言文件。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "语言文件导入成功"),
      @ApiResponse(responseCode = "400", description = "上传文件为空或格式错误")
  })
  @PostMapping("/import")
  public WebApiResult<Boolean> importLocale(
      @Parameter(description = "语言文件", required = true)
      @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return WebApiResult.fail("文件不能为空");
    }

    try {
      String content = new String(file.getBytes(), StandardCharsets.UTF_8);
      logger.debug("Importing locale file, content length: {}", content.length());

      I18nLocale locale = Json.tryToBean(I18nLocaleImpl.class, content)
          .onFailure(error -> logger.error("Failed to parse locale file, error: {}", error.getMessage()))
          .getOrElse(() -> null);

      if (locale == null) {
        logger.error("Failed to parse locale file, content: {}", content);
        return WebApiResult.fail("文件格式错误，无法解析为 I18nLocale 对象");
      }

      if (locale.getLocale() == null || locale.getLocale().trim().isEmpty()) {
        logger.error("Locale code is missing in the file");
        return WebApiResult.fail("文件中缺少 locale 字段");
      }

      return manageService.saveLocale(locale)
          .map(WebApiResult::ok)
          .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
    } catch (Exception e) {
      logger.error("Failed to import locale file", e);
      return WebApiResult.fail("导入失败: " + e.getMessage());
    }
  }

  /**
   * 设置默认语言
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "设置默认语言", description = "将指定语言代码设置为默认语言。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "默认语言设置成功"),
      @ApiResponse(responseCode = "404", description = "语言不存在")
  })
  @PostMapping("/languages/{locale}/set-default")
  public WebApiResult<Boolean> setDefaultLocale(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return WebApiResult.fail("locale 不能为空");
    }

    return manageService.setDefaultLocale(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 删除语言
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "删除语言", description = "删除指定语言代码的语言包。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "语言删除成功"),
      @ApiResponse(responseCode = "404", description = "语言不存在")
  })
  @DeleteMapping("/languages/{locale}")
  public WebApiResult<Boolean> deleteLanguage(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return WebApiResult.fail("locale 不能为空");
    }

    return manageService.deleteLanguage(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 启用/禁用语言
   */
  @Authority("systemSetting.i18nManagement")
  @Operation(summary = "启用或禁用语言", description = "修改指定语言代码的启用状态。", tags = {"i18n"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "语言启用状态更新成功"),
      @ApiResponse(responseCode = "404", description = "语言不存在")
  })
  @PostMapping("/languages/{locale}/enabled")
  public WebApiResult<Boolean> setLanguageEnabled(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale,
      @Parameter(description = "是否启用该语言", required = true)
      @RequestParam("enabled") boolean enabled) {

    if (locale == null || locale.trim().isEmpty()) {
      return WebApiResult.fail("locale 不能为空");
    }

    return manageService.setLanguageEnabled(locale, enabled)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }
}
