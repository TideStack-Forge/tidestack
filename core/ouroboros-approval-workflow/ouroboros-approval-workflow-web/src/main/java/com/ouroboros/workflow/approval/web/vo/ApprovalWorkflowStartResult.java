package com.ouroboros.workflow.approval.web.vo;

import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowStartResult;

/** 保留旧 data 执行引用，同时提供统一启动状态，避免外部受理被误认为已启动。 */
public final class ApprovalWorkflowStartResult extends WebApiResult<String> {
  private final FlowStartResult startResult;

  public ApprovalWorkflowStartResult(FlowStartResult startResult) {
    super(startResult.status() == FlowStartResult.Status.FAILED ? -1 : 0,
        startResult.status() == FlowStartResult.Status.FAILED ? "审批启动失败" : "审批发起请求已受理", startResult.executionId());
    this.startResult = startResult;
  }

  public FlowStartResult getStartResult() {
    return startResult;
  }
}
