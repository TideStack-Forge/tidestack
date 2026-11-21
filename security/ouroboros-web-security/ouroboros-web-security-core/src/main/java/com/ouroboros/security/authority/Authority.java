package com.ouroboros.security.authority;

import java.beans.Transient;
import java.util.List;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

/**
 * @author liansz
 */
public interface Authority {

  /**
   * 全名称（唯一标识）
   * 示例：
   * - job-ticket.create
   *
   * @return
   */
  String getFullName();

  /** Only authorities whose full execution chain supports credential constraints may opt in. */
  default boolean isDelegable() { return false; }

  @Transient
  default String getParent() {
    if (getFullName() == null) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? null : getFullName().substring(0, lastIndexOfDot);
  }

  @Transient
  default String getName() {
    if (getFullName() == null) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? getFullName() : getFullName().substring(lastIndexOfDot + 1);
  }

  @Transient
  default Integer getLevel() {
    String parent = getParent();
    if (parent == null || parent.isEmpty()) {
      return 0;
    }
    return parent.split("\\.").length;
  }

  /**
   * 标题
   *
   * @return
   */
  String getTitle();

  /**
   * 访问控制定义
   *
   * @return
   */
  AccessControlDefine getAccessControlDefine();

  /**
   * api列表
   *
   * @return
   */
  List<String> getApis();

  /**
   * ui视图列表
   *
   * @return
   */
  List<String> getUiViews();

  /**
   * 菜单项列表
   *
   * @return
   */
  List<String> getMenuItems();

  default Integer getOrder() {
    return 0;
  }
}
