package com.ouroboros.vcs.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.exception.PlatformException;

public class VCSException extends PlatformException {
  private static final Logger logger = LoggerFactory.getLogger(VCSException.class);

  private final VCSErrorCode errorCode;
  private final Object[] parameters;

  public VCSException(VCSErrorCode errorCode, Object... parameters) {
    super(errorCode.formatMessage(parameters));
    this.errorCode = errorCode;
    this.parameters = parameters;
    logger.error("VCS Error [{}]: {}", errorCode.getCode(), getMessage());
  }

  public VCSException(VCSErrorCode errorCode, Throwable cause, Object... parameters) {
    super(errorCode.formatMessage(parameters), cause);
    this.errorCode = errorCode;
    this.parameters = parameters;
    logger.error("VCS Error [{}]: {}", errorCode.getCode(), getMessage(), cause);
  }

  public VCSException(String message) {
    super(message);
    this.errorCode = VCSErrorCode.GENERAL_ERROR;
    this.parameters = new Object[]{message};
    logger.error("VCS Error: {}", message);
  }

  public VCSException(String message, Throwable cause) {
    super(message, cause);
    this.errorCode = VCSErrorCode.GENERAL_ERROR;
    this.parameters = new Object[]{message};
    logger.error("VCS Error: {}", message, cause);
  }

  @Override
  public Integer getErrorCode() {
    return errorCode != null ? errorCode.getCode().hashCode() : 0;
  }

  public VCSErrorCode getVCSErrorCode() {
    return errorCode;
  }

  public Object[] getParameters() {
    return parameters;
  }

  public String getErrorCodeString() {
    return errorCode != null ? errorCode.getCode() : "VCS-000";
  }
}
