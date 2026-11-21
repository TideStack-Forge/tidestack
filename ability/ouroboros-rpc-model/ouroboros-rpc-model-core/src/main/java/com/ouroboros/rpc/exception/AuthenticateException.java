package com.ouroboros.rpc.exception;

public class AuthenticateException extends RPCException {

  public AuthenticateException(String message) {
    super(message);
  }

  public AuthenticateException(Integer errorCode, String message) {
    super(errorCode, message);
  }

  public AuthenticateException(String message, Throwable cause) {
    super(message, cause);
  }
}
