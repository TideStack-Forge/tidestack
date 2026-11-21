package com.ouroboros.i18n.provider;

import java.util.List;
import java.util.Optional;

import com.ouroboros.i18n.model.I18nLocale;

/**
 * 语言环境提供者接口（SPI）
 * <p>
 * 定义语言环境加载的标准接口，支持从不同来源加载：
 * - 文件系统（开发时）
 * - 数据库（运行时）
 * - 远程服务
 * <p>
 * 实现类通过 Java SPI 机制注册，可以有多个提供者，
 * 按照 @Priority 注解的优先级顺序加载。
 */
public interface I18nLocaleProvider {

  /**
   * 获取指定语言的环境配置
   *
   * @param locale 语言代码（如：zh-CN, en-US）
   * @return 语言环境对象，如果不存在返回 Optional.empty()
   */
  Optional<I18nLocale> getDictionary(String locale);

  /**
   * 获取所有支持的语言代码
   *
   * @return 语言代码列表
   */
  List<String> getSupportedLocales();
}
