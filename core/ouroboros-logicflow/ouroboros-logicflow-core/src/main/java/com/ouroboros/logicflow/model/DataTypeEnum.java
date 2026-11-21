package com.ouroboros.logicflow.model;

import java.util.Arrays;
import java.util.List;

public enum DataTypeEnum {
  STRING("string"),
  INTEGER("integer"),
  LONG("long"),
  FLOAT("float"),
  DOUBLE("double"),
  BOOLEAN("boolean"),
  DATE("date"),
  DATETIME("datetime"),
  TIME("time"),
  OBJECT("object"),
  ARRAY("array"),
  MAP("map"),
  ANY("any");

  static final List<DataTypeEnum> numericTypes = Arrays.asList(DataTypeEnum.INTEGER, DataTypeEnum.LONG, DataTypeEnum.FLOAT, DataTypeEnum.DOUBLE);
  private final String value;

  DataTypeEnum(String value) {
    this.value = value;
  }

  public static DataTypeEnum fromValue(String value) {
    for (DataTypeEnum dataType : DataTypeEnum.values()) {
      if (dataType.getValue().equals(value)) {
        return dataType;
      }
    }
    return DataTypeEnum.ANY;
  }

  public String getValue() {
    return value;
  }

  public boolean isCompatible(DataTypeEnum dataType) {
    if (this == dataType) {
      return true;
    }
    if (this == DataTypeEnum.ANY || dataType == DataTypeEnum.ANY) {
      return true;
    }
    if (numericTypes.contains(this) && numericTypes.contains(dataType)) {
      return true;
    }
    return false;
  }
}
