package com.ouroboros.workflow.approval.external.runtime.scope;

/**
 * 默认作用域解析器：没有业务域作用域实现时拒绝路由。
 *
 * <p>抛出 {@link WorkflowScopeUnconfiguredException}，读路径据此把外部聚合降级为无外部数据，
 * 写路径据此显式失败。禁止回退到全局默认作用域。
 */
public class DefaultWorkflowScopeResolver implements WorkflowScopeResolver {

  @Override
  public String resolve() {
    throw new WorkflowScopeUnconfiguredException(
        "无权限：WorkflowScopeResolver 未装配，无法解析审批外部集成 scope");
  }
}
