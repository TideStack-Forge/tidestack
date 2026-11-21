package com.ouroboros.notification.constant;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author liansz
 */
public enum NotificationStateConstant {

  SUCCESS("success"),

  FAIL("fail"),

  PENDING("pending"),

  SENDING("sending"),

  RETRYABLE("retryable"),

  BLOCKED("blocked"),

  SKIPPED("skipped"),

  UNKNOWN("unknown");

  private final String value;

  NotificationStateConstant(String value) {
    this.value = value;
  }

  public static NotificationStateConstant of(String value) {
    return Arrays.stream(NotificationStateConstant.values())
        .filter(i -> Objects.equals(i.value, value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown value: " + value));
  }

  public String getValue() {
    return value;
  }
}
