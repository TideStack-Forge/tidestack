package com.ouroboros.workflow.approval.external.runtime.scope;

/**
 * 流程映射作用域解析器。
 *
 * <p>scope 必须来自已认证的应用/租户上下文；无法解析时应抛出权限异常，禁止回退到全局默认作用域。
 */
public interface WorkflowScopeResolver {

  /**
   * 返回当前作用域标识。
   */
  String resolve();
}
