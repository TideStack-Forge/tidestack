package com.ouroboros.rpc.exception;

import com.ouroboros.exception.OuroborosException;

public class RPCException extends OuroborosException {
  final private static Integer DEFAULT_ERROR_CODE = 3000;

  public RPCException(String message) {
    super(DEFAULT_ERROR_CODE, message);
  }

  public RPCException(Integer errorCode, String message) {
    super(errorCode, message);
  }

  public RPCException(String message, Throwable cause) {
    super(message, cause);
  }
}
