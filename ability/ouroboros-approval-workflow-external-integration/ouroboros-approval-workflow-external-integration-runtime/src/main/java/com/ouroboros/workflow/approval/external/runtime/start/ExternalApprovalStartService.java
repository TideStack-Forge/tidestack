package com.ouroboros.workflow.approval.external.runtime.start;

import io.vavr.control.Try;

/**
 * 存量结果型启动接口；保留请求和返回形状，内部转入 FlowInstanceService 的统一路由。
 * 新业务使用 FlowInstanceService.start(FlowStartCommand)，无需选择接入模式。
 */
public interface ExternalApprovalStartService {

  /**
   * 发起结果型外部审批。
   *
   * @param command 存量结果型启动命令；内部转换为统一 FlowStartCommand
   * @return 兼容的本地 trackingId 与外部实例引用
   */
  Try<ResultOnlyStartResult> start(ResultOnlyStartCommand command);
}
