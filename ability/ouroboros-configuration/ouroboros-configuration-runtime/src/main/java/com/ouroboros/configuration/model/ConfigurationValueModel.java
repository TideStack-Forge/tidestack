package com.ouroboros.configuration.model;

/**
 * @author liansz
 */
public interface ConfigurationValueModel {

  /**
   * 唯一键
   *
   * @return
   */
  String getKey();

  /**
   * 值
   *
   * @return
   */
  String getValue();
}
