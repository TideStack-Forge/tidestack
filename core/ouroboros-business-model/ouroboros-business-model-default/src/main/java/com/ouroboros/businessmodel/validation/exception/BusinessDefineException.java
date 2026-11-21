package com.ouroboros.businessmodel.validation.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 业务模型定义异常
 *
 * @author liansz
 **/
public abstract class BusinessDefineException extends BusinessException {

  public BusinessDefineException(Integer errorCode, String message) {
    super(errorCode, message);
  }

  public BusinessDefineException(Integer errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public BusinessDefineException(String message, Throwable cause) {
    super(message, cause);
  }

  public BusinessDefineException(String message) {
    super(message);
  }
}
