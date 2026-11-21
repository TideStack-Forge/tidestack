package com.ouroboros.businessmodel.action;

/** High-code implementation of a published business Action binding. */
public interface BusinessActionHandler {
  String getName();

  default String getImplementationRef() {
    return getClass().getName();
  }

  default String getImplementationVersion() {
    return getClass().getPackage().getImplementationVersion();
  }

  default void validateContract(com.ouroboros.businessmodel.behavior.ActionDefinition action) {
  }

  Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input);
}
