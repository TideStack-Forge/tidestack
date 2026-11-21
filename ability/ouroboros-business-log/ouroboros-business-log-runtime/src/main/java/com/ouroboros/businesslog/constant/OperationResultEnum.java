package com.ouroboros.businesslog.constant;

/**
 * @author liansz
 **/
public enum OperationResultEnum {

  UNKNOWN(0),

  SUCCESS(1),

  FAILURE(2);

  private final int value;

  OperationResultEnum(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
