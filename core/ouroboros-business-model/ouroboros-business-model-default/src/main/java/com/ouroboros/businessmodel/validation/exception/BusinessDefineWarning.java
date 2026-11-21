package com.ouroboros.businessmodel.validation.exception;

/**
 * 业务模型定义警告
 *
 * @author liansz
 **/
public abstract class BusinessDefineWarning extends BusinessDefineException {

  public BusinessDefineWarning(String message) {
    super(message);
  }
}
