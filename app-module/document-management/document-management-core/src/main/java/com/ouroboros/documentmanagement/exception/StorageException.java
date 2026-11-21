package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.InternalException;

/**
 * 存储异常
 */
public class StorageException extends InternalException {

  public static final int ERROR_CODE = -5001;

  public StorageException(String message) {
    super(ERROR_CODE, message);
  }

  public StorageException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
  }
}
