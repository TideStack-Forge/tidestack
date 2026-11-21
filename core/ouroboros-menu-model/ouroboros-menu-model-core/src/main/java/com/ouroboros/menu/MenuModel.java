package com.ouroboros.menu;

import java.beans.Transient;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author liansz
 */
public interface MenuModel {
  /**
   * url（路由路径）
   *
   * @return
   */
  String getUrl();

  /**
   * 全名称
   *
   * @return
   */
  String getFullName();

  @Transient
  default String getParent() {
    if (getFullName() == null) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? null : getFullName().substring(0, lastIndexOfDot);
  }

  /**
   * 简单名称
   *
   * @return
   */
  @Transient
  default String getName() {
    if (getFullName() == null) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? getFullName() : getFullName().substring(lastIndexOfDot + 1);
  }

  /**
   * 图标
   *
   * @return
   */
  String getIcon();

  /**
   * 标题
   *
   * @return
   */
  String getTitle();

  /**
   * 显示模式
   *
   * @return
   */
  MenuModelShowModeEnum getShowMode();

  /**
   * 顺序
   *
   * @return
   */
  Integer getOrder();

  default MenuScope getScope() {
    return MenuScope.MAIN;
  }

  default Map<String, Object> getExtraProps() {
    return Collections.emptyMap();
  }

  default Object getExtraProp(String key) {
    return getExtraProps().get(key);
  }

  enum MenuScope {
    MAIN("main"),
    SECONDARY("secondary"),
    ALL("all");
    private String value;

    MenuScope(String value) {
      this.value = value;
    }

    @JsonCreator
    public static MenuScope fromValue(String value) {
      for (MenuScope menuScope : MenuScope.values()) {
        if (menuScope.getValue().equals(value)) {
          return menuScope;
        }
      }
      return MAIN;
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }
}
