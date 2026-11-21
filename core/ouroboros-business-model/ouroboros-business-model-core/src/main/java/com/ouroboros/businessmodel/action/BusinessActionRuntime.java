package com.ouroboros.businessmodel.action;

/**
 * Unified entry point for transport-independent business Action calls.
 *
 * <p>Action 以 {@code PROPAGATION_REQUIRED} 参与业务事务。返回值只代表本次执行结果；
 * 加入调用方已有事务时，最终提交或回滚由最外层事务所有者决定，完成态审计与
 * 提交后唤醒只发生在最外层事务真正提交之后。
 */
public interface BusinessActionRuntime {
  java.util.Optional<BusinessActionImplementation> inspectImplementation(String modelFullName, String actionName);

  java.util.Optional<String> inspectContractFingerprint(String modelFullName, String actionName);

  java.util.List<BusinessActionDescription> describe(String modelFullName);

  Object invoke(BusinessActionInvocation invocation);

  default Object invoke(String modelFullName, String actionName, BusinessActionInput<?, ?> input) {
    return invoke(new BusinessActionInvocation(modelFullName, actionName, input));
  }

  BusinessActionAvailability checkAvailability(BusinessActionInvocation invocation);
}
