package com.ouroboros.security.rbac;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户-主体绑定关系提供者接口
 * 通过SPI机制注册，支持多数据源
 * 
 * @author songmingxu
 */
public interface UserSubjectProvider {

  /**
   * 根据用户标识获取绑定关系
   *
   * @param userIdentity 用户标识
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubject> getByUser(String userIdentity);

  /**
   * 根据用户标识和主体类型获取绑定关系
   *
   * @param userIdentity 用户标识
   * @param subjectType 主体类型
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubject> getByUser(String userIdentity, String subjectType);

  /**
   * 根据用户标识获取所有绑定关系
   *
   * @param userIdentity 用户标识
   * @return 绑定关系列表
   */
  List<UserSubject> getAllByUser(String userIdentity);

  /**
   * 批量根据用户标识获取所有绑定关系
   *
   * @param userIdentities 用户标识集合
   * @return 用户标识到绑定关系列表的映射
   */
  Map<String, List<UserSubject>> getAllByUsers(Collection<String> userIdentities);

  /**
   * 批量根据用户标识和主体类型获取绑定关系
   *
   * @param userIdentities 用户标识集合
   * @param subjectType 主体类型
   * @return 用户标识到绑定关系的映射
   */
  Map<String, UserSubject> getByUsers(Collection<String> userIdentities, String subjectType);

  /**
   * 根据主体类型和ID获取绑定关系
   *
   * @param subjectType 主体类型
   * @param subjectId   主体ID
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubject> getBySubject(String subjectType, String subjectId);

  /**
   * 获取所有绑定关系
   *
   * @return 绑定关系列表
   */
  List<UserSubject> getAll();
}
