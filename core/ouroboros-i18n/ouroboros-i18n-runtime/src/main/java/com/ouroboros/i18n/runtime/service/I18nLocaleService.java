package com.ouroboros.i18n.runtime.service;

import java.util.List;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.model.I18nLocale;

/**
 * 字典管理服务
 */
public interface I18nLocaleService {

  /**
   * 获取字典（包含完整翻译内容）
   * 多个版本的字典会自动合并，高版本覆盖低版本
   *
   * @param locale 语言代码
   * @return 字典
   */
  Either<OuroborosException, I18nLocale> getLocale(String locale);

  /**
   * 获取所有支持的语言
   *
   * @return 语言列表
   */
  List<I18nLocale> getSupportedLanguages();

  /**
   * 提取文案生成字典模板
   *
   * @param sourceLocale 源语言
   * @return 字典模板
   */
  Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale);
}
