package com.ouroboros.security.rbac;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.util.SPI;

/**
 * 主体中心 - 统一管理不同类型的SubjectProvider
 * 支持同一类型的多数据源，通过SPI机制加载Provider
 *
 * @author songmingxu
 */
public interface SubjectCenter {

  /**
   * 根据类型和ID获取主体
   * 会按优先级遍历该类型的所有Provider，返回第一个找到的结果
   *
   * @param type 主体类型
   * @param id   主体ID
   * @return 主体对象，如果不存在则返回空
   */
  Optional<Subject> getByTypeAndId(String type, String id);

  /**
   * 根据类型、ID和提供者名称获取主体
   *
   * @param type         主体类型
   * @param id           主体ID
   * @param providerName 提供者名称
   * @return 主体对象，如果不存在则返回空
   */
  Optional<Subject> getByTypeAndId(String type, String id, String providerName);

  /**
   * 批量根据类型和ID获取主体
   *
   * @param type 主体类型
   * @param ids  主体ID集合
   * @return 主体列表
   */
  List<Subject> getByTypeAndIds(String type, Collection<String> ids);

  /**
   * 根据类型获取所有主体
   *
   * @param type 主体类型
   * @return 主体列表
   */
  List<Subject> getByType(String type);

  /**
   * 获取支持的主体类型列表
   *
   * @return 支持的主体类型列表
   */
  List<String> getSupportedTypes();

  /**
   * 获取指定类型的所有提供者名称
   *
   * @param type 主体类型
   * @return 提供者名称列表
   */
  List<String> getProviderNames(String type);

  static SubjectCenter instance() {
    return SPI.getCachedPrimaryService(SubjectCenter.class);
  }
}
