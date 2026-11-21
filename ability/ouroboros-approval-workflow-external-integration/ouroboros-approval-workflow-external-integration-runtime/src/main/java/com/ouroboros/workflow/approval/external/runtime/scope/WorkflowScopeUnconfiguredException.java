package com.ouroboros.workflow.approval.external.runtime.scope;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;

/**
 * 外部协作作用域未装配。
 *
 * <p>由 {@link DefaultWorkflowScopeResolver} 在部署方尚未提供 {@link WorkflowScopeResolver} 业务实现时抛出。
 * 读路径据此把外部聚合降级为"无外部数据"（平台未启用外部协作），写路径据此显式失败；
 * 已装配的解析器若判定无权访问，应抛出其他异常（例如 {@code AccessDeniedException}），不得复用本类型，
 * 以免被读路径误当作"未启用"而静默放行。
 */
public class WorkflowScopeUnconfiguredException extends PlatformException {

  public WorkflowScopeUnconfiguredException(String message) {
    super("[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] " + message);
  }
}
