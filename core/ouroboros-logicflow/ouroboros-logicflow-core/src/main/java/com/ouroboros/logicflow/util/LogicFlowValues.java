package com.ouroboros.logicflow.util;

import java.math.BigDecimal;

public final class LogicFlowValues {
  private LogicFlowValues() {
  }

  public static Boolean toBoolean(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof Number numberValue) {
      return numberValue.doubleValue() != 0;
    }
    if (value instanceof BigDecimal decimalValue) {
      return decimalValue.compareTo(BigDecimal.ZERO) != 0;
    }
    if (value instanceof CharSequence textValue) {
      var text = textValue.toString();
      if (text.isEmpty()) {
        return false;
      }
      if ("true".equalsIgnoreCase(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text)) {
        return false;
      }
      try {
        return Integer.parseInt(text) != 0;
      } catch (NumberFormatException ignored) {
        return true;
      }
    }
    return true;
  }
}
