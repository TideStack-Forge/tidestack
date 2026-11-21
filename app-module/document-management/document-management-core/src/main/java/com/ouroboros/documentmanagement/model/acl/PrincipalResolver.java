package com.ouroboros.documentmanagement.model.acl;

import java.util.List;

/**
 * Principal 解析器接口，用于将 Principal 解析为用户ID列表
 */
public interface PrincipalResolver {
  /**
   * 解析 Principal 为用户ID列表
   *
   * @param principal Principal标识
   * @return 用户ID列表
   */
  List<String> expandToUserIds(String principal);

  /**
   * 判断一个用户是否包含 Principal
   *
   * @param principal Principal标识
   * @param userId 用户ID
   * @return 如果用户包含该Principal则返回true
   */
  boolean containsUser(String principal, String userId);

  /**
   * 获取用户所拥有的该类型的所有 Principal 列表
   *
   * @param userId 用户ID
   * @return 该用户所拥有的Principal列表（如：用户所属的所有组、角色等）
   */
  List<String> getPrincipalsForUser(String userId);

  /**
   * 获取 Principal 的显示名称
   *
   * @param principal Principal标识
   * @return Principal的显示名称，如果无法解析则返回 principal 本身
   */
  String getDisplayName(String principal);
}
