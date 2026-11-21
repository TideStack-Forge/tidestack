package com.ouroboros.configuration.service;

import java.util.List;

import com.ouroboros.configuration.model.ConfigurationValueModel;

/**
 * @author liansz
 */
public interface ConfigurationValueService {

  /**
   * 更新
   *
   * @param model
   */
  boolean update(ConfigurationValueModel model);

  /**
   * Atomically update or insert a persisted configuration value when the
   * supplied effective revision still matches. {@code defaultRevision} is used
   * only when the key has no persisted row yet.
   */
  boolean update(ConfigurationValueModel model, String expectedRevision, String defaultRevision);

  /** Remove only the observed incarnation/version, or confirm the matching absent default. */
  boolean removeOverride(String key, String expectedRevision, String defaultRevision);

  /**
   * 通过key获取
   *
   * @param key
   * @return
   */
  ConfigurationValueModel get(String key);

  /** Lock an existing row in the caller's transaction through the canonical data-model plugin chain. */
  ConfigurationValueModel getForUpdate(String key);

  /**
   * 通过key获取
   *
   * @param key
   * @return
   * @deprecated 请使用 {@link #get(String)}。
   */
  @Deprecated
  default ConfigurationValueModel getByKey(String key) {
    return get(key);
  }

  /**
   * 获取全部配置值
   *
   * @return
   */
  List<? extends ConfigurationValueModel> getAllConfigurationValue();
}
