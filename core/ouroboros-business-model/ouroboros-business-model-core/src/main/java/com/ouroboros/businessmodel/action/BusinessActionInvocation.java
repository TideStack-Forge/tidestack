package com.ouroboros.businessmodel.action;

import java.util.Objects;

/** Transport-independent request to execute one published business Action. */
public record BusinessActionInvocation(String modelFullName,
                                       String actionName,
                                       BusinessActionInput<?, ?> input,
                                       String expectedContractFingerprint) {
  public BusinessActionInvocation(String modelFullName, String actionName, BusinessActionInput<?, ?> input) {
    this(modelFullName, actionName, input, null);
  }

  public BusinessActionInvocation {
    if (modelFullName == null || modelFullName.isBlank()) {
      throw new IllegalArgumentException("modelFullName must not be blank");
    }
    if (actionName == null || actionName.isBlank()) {
      throw new IllegalArgumentException("actionName must not be blank");
    }
    Objects.requireNonNull(input, "input must not be null");
  }
}
