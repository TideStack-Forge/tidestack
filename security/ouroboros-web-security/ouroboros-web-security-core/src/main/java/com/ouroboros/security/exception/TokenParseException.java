package com.ouroboros.security.exception;

/**
 * token解析异常
 *
 * @author liansz
 */
public class TokenParseException extends TokenException {

  public TokenParseException(String msg) {
    super(msg);
  }

  public TokenParseException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
