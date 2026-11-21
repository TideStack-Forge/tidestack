package com.ouroboros.configuration.validation;

/**
 * 配置值保存前的领域校验扩展点。
 *
 * <p>配置模块只负责调用扩展点，不依赖具体业务模块。校验器不得写入配置或执行远程副作用；
 * 校验失败应直接抛出平台异常，使当前配置批次回滚。
 */
public interface ConfigurationValueValidator {

  /**
   * 是否由当前校验器负责指定配置键。
   *
   * @param key 配置键
   * @return 是否支持
   */
  boolean supports(String key);

  /**
   * 校验待保存的原始值。
   *
   * @param value 待保存值，可能是 JSON 字符串或已转换的 Java 容器
   */
  void validate(Object value);
}
