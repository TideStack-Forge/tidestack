package com.ouroboros.notification.internal.message.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author liansz
 */
public enum InternalMessageStateEnum {

  /**
   * 未读
   */
  UNREAD("unread"),

  /**
   * 已读
   */
  READ("read");

  private final String value;

  InternalMessageStateEnum(String value) {
    this.value = value;
  }

  public static Optional<InternalMessageStateEnum> valueOfEnum(String value) {
    return Arrays.stream(values()).filter(e -> e.getValue().equals(value)).findFirst();
  }

  public String getValue() {
    return value;
  }
}
