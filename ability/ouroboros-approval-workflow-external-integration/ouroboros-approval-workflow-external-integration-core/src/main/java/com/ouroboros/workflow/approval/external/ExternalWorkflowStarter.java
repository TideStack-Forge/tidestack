package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部流程启动端口。仅用于 {@code authority=EXTERNAL_ENGINE}。
 */
public interface ExternalWorkflowStarter {

  /**
   * 启动外部流程实例。
   *
   * @param command 规范的启动命令（启动人、业务记录、表单、流程变量、幂等键、外部定义引用，
   *                可选抄送与发起时指定/自选审批人）
   * @return 外部实例引用、可选外部访问地址与初始快照
   */
  Try<ExternalStartResult> start(ExternalStartCommand command);
}
