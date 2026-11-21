package com.ouroboros.businessmodel.validation.exception;

/**
 * 业务模型接口定义错误
 *
 * @author liansz
 **/
public abstract class BusinessDefineError extends BusinessDefineException {

  public BusinessDefineError(String message) {
    super(message);
  }
}
