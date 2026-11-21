package com.ouroboros.i18n.runtime.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.runtime.dto.LanguageOption;
import com.ouroboros.i18n.runtime.service.I18nLocaleService;
import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 国际化字典 REST API
 *
 * @author AI Assistant
 */
@RestController
@RequestMapping("/api/i18n")
public class I18nLocaleController {

  @Autowired
  private I18nLocaleService localeService;

  /**
   * 获取指定语言的字典
   *
   * @param locale 语言代码 (zh_CN, en_US, ja_JP)
   * @return 字典数据
   */
  @Operation(
      summary = "获取语言包",
      description = "根据语言代码获取运行时国际化语言包内容。",
      tags = {"i18n"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回语言包"),
      @ApiResponse(responseCode = "404", description = "语言包不存在")
  })
  @GetMapping("/locale/{locale}")
  @Anonymous
  public WebApiResult<I18nLocale> getLocale(
      @Parameter(description = "语言代码", required = true)
      @PathVariable("locale") String locale) {
    return localeService.getLocale(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 获取支持的语言列表
   *
   * @return 语言选项列表
   */
  @Operation(
      summary = "列出支持语言",
      description = "返回当前系统支持的语言选项列表。",
      tags = {"i18n"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回语言选项列表")
  })
  @GetMapping("/languages")
  @Anonymous
  public ItemsWebApiResult<LanguageOption> getLanguages() {
    List<LanguageOption> options = localeService.getSupportedLanguages().stream()
        .map(locale -> new LanguageOption(locale.getLocale(), locale.getName(), locale.getVersion(), locale.getIsDefault()))
        .collect(Collectors.toList());
    return ItemsWebApiResult.success(options);
  }
}
