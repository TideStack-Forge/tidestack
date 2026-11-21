package com.ouroboros.businessmodel.action;

import com.ouroboros.exception.BusinessException;

/** A declared business condition failed; execution must roll back before delivery is paused. */
public final class BusinessActionPreconditionException extends BusinessException {
  public BusinessActionPreconditionException(String message) {
    super(message);
  }
}
