package com.ouroboros.dev.componentlib.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 组件库不存在异常，用户尝试引用不存在的组件库
 */
public class ComponentLibNotFoundException extends BusinessException {
  private static final Integer ERROR_CODE = -4103;

  public ComponentLibNotFoundException(String componentLibName) {
    super(ERROR_CODE, "组件库不存在: " + componentLibName);
  }

  public ComponentLibNotFoundException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
  }
}
