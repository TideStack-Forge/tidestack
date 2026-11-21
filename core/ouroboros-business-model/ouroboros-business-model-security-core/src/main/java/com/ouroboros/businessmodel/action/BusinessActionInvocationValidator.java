package com.ouroboros.businessmodel.action;

/** Validates trusted integration bindings before an Action implementation can perform business writes. */
@FunctionalInterface
public interface BusinessActionInvocationValidator {
  void validate(BusinessActionContext context, BusinessActionInput<?, ?> input);
}
