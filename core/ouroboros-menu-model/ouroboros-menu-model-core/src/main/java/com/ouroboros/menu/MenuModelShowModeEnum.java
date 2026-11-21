package com.ouroboros.menu;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ouroboros.exception.PlatformException;

/**
 * @author liansz
 */
public enum MenuModelShowModeEnum {

  /**
   * 标签模式
   */
  TAB("tab"),

  /**
   * 全屏模式
   */
  FULL_SCREEN("fullScreen"),

  /**
   * 新窗口模式
   */
  NEW_WINDOW("newWindow");

  private final String value;

  MenuModelShowModeEnum(String value) {
    this.value = value;
  }

  @JsonCreator
  public static MenuModelShowModeEnum fromValue(String value) {
    return Arrays.stream(MenuModelShowModeEnum.values())
        .filter(v -> v.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new PlatformException("Invalid value: " + value));
  }

  @JsonValue
  public String value() {
    return value;
  }
}
