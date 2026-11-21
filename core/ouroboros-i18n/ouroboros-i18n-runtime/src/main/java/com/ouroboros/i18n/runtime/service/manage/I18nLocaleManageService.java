package com.ouroboros.i18n.runtime.service.manage;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.runtime.dto.ExportResult;
import com.ouroboros.i18n.runtime.dto.I18nLanguageSummary;
import com.ouroboros.i18n.runtime.dto.SyncResult;

/**
 * 国际化字典管理服务
 * 负责字典的增删改操作
 */
public interface I18nLocaleManageService {

  /**
   * 获取所有语言列表（包括禁用的）
   * 用于管理界面显示
   *
   * @return 所有语言列表
   */
  List<I18nLanguageSummary> getAllLanguages();

  /**
   * 获取语言详情
   *
   * @param locale 语言代码
   * @return 语言详情
   */
  Optional<I18nLocale> getLanguageDetail(String locale);

  /**
   * 导出语言文件
   *
   * @param locale 语言代码
   * @return 导出结果
   */
  Either<OuroborosException, ExportResult> exportLanguage(String locale);

  /**
   * 保存完整字典
   *
   * @param locale 字典对象
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> saveLocale(I18nLocale locale);

  /**
   * 从提取器同步文案到数据库
   *
   * @param locale         语言代码
   * @param removeOrphaned 是否删除现有locale中多出来的key（默认false）
   * @return 同步结果（新增和删除的条目数量）
   */
  Either<OuroborosException, SyncResult> syncFromExtractors(String locale, boolean removeOrphaned);

  /**
   * 导出翻译模板
   *
   * @return 包含所有文案键的模板文件
   */
  Either<OuroborosException, ExportResult> exportTemplate();

  /**
   * 提取文案生成字典模板
   *
   * @param sourceLocale 源语言
   * @return 字典模板
   */
  Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale);

  /**
   * 设置默认语言
   *
   * @param locale 语言代码
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> setDefaultLocale(String locale);

  /**
   * 删除语言
   *
   * @param locale 语言代码
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> deleteLanguage(String locale);

  /**
   * 启用/禁用语言
   *
   * @param locale  语言代码
   * @param enabled 是否启用
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> setLanguageEnabled(String locale, boolean enabled);
}
