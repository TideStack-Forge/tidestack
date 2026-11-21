package com.ouroboros.notification.internal.message.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author liansz
 */
public enum InternalMessagePlatformEnum {

  /**
   * WEB端
   */
  WEB("web"),

  /**
   * APP端
   */
  APP("app");

  private final String value;

  InternalMessagePlatformEnum(String value) {
    this.value = value;
  }

  public static Optional<InternalMessagePlatformEnum> valueOfEnum(String value) {
    return Arrays.stream(values()).filter(e -> e.getValue().equals(value)).findFirst();
  }

  public String getValue() {
    return value;
  }
}
