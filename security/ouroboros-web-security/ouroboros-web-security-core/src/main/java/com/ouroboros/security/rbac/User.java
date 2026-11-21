package com.ouroboros.security.rbac;

import java.beans.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;

/**
 * 用户接口
 */
public interface User {

  /**
   * 用户唯一标识
   */
  String getUserIdentity();

  /**
   * 获取用户的 Claims（属性集合）
   */
  @Transient
  default Claims getClaims() {
    return Claims.empty();
  }

  /**
   * 获取角色列表
   */
  @Transient
  default List<Role> getRoles() {
    return Collections.emptyList();
  }

  /**
   * 获取关联的主体信息
   * 注意：需要在运行时通过 SubjectCenter 获取，此处返回 null
   * 实现类应该覆盖此方法
   */
  @Transient
  default Subject getSubject() {
    return null;
  }

  // ========== 便捷方法（委托给 Claims） ==========

  /** 账号昵称；不依赖 Claims 聚合，允许在聚合过程中读取。 */
  default String getNickname() {
    return null;
  }

  /** 正式名称由关联主体维护，例如员工档案中的姓名。 */
  default String getFormalName() {
    return Optional.ofNullable(getSubject()).map(Subject::getName).orElse(null);
  }

  /** 显示名称按昵称、正式名称、登录名的顺序确定。 */
  default String getDisplayName() {
    return Optional.ofNullable(getNickname()).filter(name -> !name.isBlank())
        .or(() -> Optional.ofNullable(getFormalName()).filter(name -> !name.isBlank()))
        .orElseGet(this::getUserIdentity);
  }

  /**
   * 获取头像 URL
   */
  default String getPicture() {
    return getClaims().getString(StandardClaims.PICTURE).orElse(null);
  }

  /**
   * 获取邮箱
   */
  default String getEmail() {
    return getClaims().getString(StandardClaims.EMAIL).orElse(null);
  }

  /**
   * 获取手机号
   */
  default String getPhoneNumber() {
    return getClaims().getString(StandardClaims.PHONE_NUMBER).orElse(null);
  }

  /**
   * 用户是否启用。禁用的用户无法登录。
   * 默认返回 true（向后兼容——所有现有用户视为启用状态）。
   */
  default boolean isEnabled() {
    return true;
  }
}
