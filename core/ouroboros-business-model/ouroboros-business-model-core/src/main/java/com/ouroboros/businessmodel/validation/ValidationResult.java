package com.ouroboros.businessmodel.validation;

import java.util.Collections;
import java.util.List;

/**
 * 验证结果
 */
public class ValidationResult {
  private final List<Throwable> errors;

  public ValidationResult(List<Throwable> errors) {
    this.errors = errors;
  }

  public boolean isValid() {
    return errors.size() == 0;
  }

  public List<Throwable> getErrors() {
    return Collections.unmodifiableList(errors);
  }
}
