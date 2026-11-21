package com.ouroboros.dev.componentlib.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 组件库冲突异常，用于业务规则冲突场景
 * 例如：版本冲突、路径冲突、组件定义冲突、应用依赖冲突等
 */
public class ComponentLibConflictException extends BusinessException {
  private static final Integer ERROR_CODE = -4102;

  public ComponentLibConflictException(String message) {
    super(ERROR_CODE, message);
  }

  public ComponentLibConflictException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
  }
}
