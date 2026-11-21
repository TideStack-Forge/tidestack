package com.ouroboros.i18n.extractor;

import java.util.List;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.model.I18nEntry;

/**
 * 文案提取器接口
 * <p>
 * 用于从各种元数据源（UI Schema、菜单、权限树等）提取需要翻译的文案。
 * 提取器通过 SPI 机制加载，支持扩展不同的提取实现。
 */
public interface TextExtractor {

  /**
   * 提取文案
   * <p>
   * 从特定的元数据源中提取所有需要翻译的文案条目。
   *
   * @return Either.Right 包含提取的条目列表，Either.Left 包含提取过程中的异常
   */
  Either<OuroborosException, List<I18nEntry>> extract();
}
