package com.ouroboros.data.dict.constant;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * @author sunquan
 */

public enum DictType {

  /**
   * 代码表
   */
  CODD_TABLE("code_table"),

  /**
   * 枚举值
   */
  ENUM("enum"),

  /**
   * 状态表
   */
  STATE_TABLE("state_table");

  private String value;

  DictType(String value) {
    this.value = value;
  }

  public static Optional<DictType> valueOfEnum(String value) {
    return Arrays.stream(values()).filter(e -> Objects.equals(e.getValue(), value)).findFirst();
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return getValue();
  }
}
