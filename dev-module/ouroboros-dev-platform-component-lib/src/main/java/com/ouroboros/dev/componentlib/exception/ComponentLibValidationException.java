package com.ouroboros.dev.componentlib.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 组件库验证异常，用于组件库配置验证失败的场景
 * 例如：缺少必填字段、格式校验失败、ZIP包格式错误等
 */
public class ComponentLibValidationException extends BusinessException {
  private static final Integer ERROR_CODE = -4101;

  public ComponentLibValidationException(String message) {
    super(ERROR_CODE, message);
  }

  public ComponentLibValidationException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
  }
}
