package com.ouroboros.workflow.approval.external.runtime.start;

import org.springframework.stereotype.Service;

import com.ouroboros.workflow.approval.FlowInstanceService;

import io.vavr.control.Try;

/** 存量高代码入口的兼容适配；引擎选择全部委托统一发起服务。 */
@Service
public class ExternalApprovalStartServiceImpl implements ExternalApprovalStartService {
  private final FlowInstanceService instances;

  public ExternalApprovalStartServiceImpl(FlowInstanceService instances) {
    this.instances = instances;
  }

  @Override
  public Try<ResultOnlyStartResult> start(ResultOnlyStartCommand command) {
    return instances.start(command.toFlowStartCommand()).map(result -> new ResultOnlyStartResult(
        result.executionId(), result.externalInstanceId(), result.externalUrl(), result.status().name(), result.startFactId()));
  }
}
