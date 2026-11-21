package com.ouroboros.security.rbac;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 主体提供者接口 - 用于主体数据访问
 * 通过SPI机制注册，支持同一类型的多数据源
 *
 * @author songmingxu
 */
public interface SubjectProvider {

  /**
   * 获取支持的主体类型
   *
   * @return 支持的主体类型
   */
  String getSupportedType();

  /**
   * 获取提供者名称（用于区分同类型的不同数据源）
   *
   * @return 提供者名称
   */
  default String getProviderName() {
    return "default";
  }

  /**
   * 根据ID获取主体
   *
   * @param id 主体ID
   * @return 主体对象，如果不存在则返回空
   */
  Optional<Subject> getById(String id);

  /**
   * 批量根据ID获取主体
   *
   * @param ids 主体ID集合
   * @return 主体列表
   */
  List<Subject> getByIds(Collection<String> ids);

  /**
   * 获取所有主体
   *
   * @return 主体列表
   */
  List<Subject> getAll();
}
