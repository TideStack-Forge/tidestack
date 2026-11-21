package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部工作流动作执行端口：统一承载任务动作与实例动作，同步返回动作结果。
 * 仅用于 {@code authority=EXTERNAL_ENGINE} 且适配器声明了对应动作能力。
 */
public interface ExternalWorkflowActionExecutor {

  /**
   * 执行规范动作（{@code approve}/{@code reject}/{@code transfer}/{@code addSign} 等）。
   *
   * <p>{@link ExternalActionCommand#idempotencyKey()} 必须非空。同一幂等键与同一命令重复调用时，
   * 适配器必须返回第一次调用的结果且不得重复产生远程副作用；同一幂等键对应不同命令时必须失败。
   * {@link ExternalActionResult#accepted()} 为 {@code false} 表示外部系统明确拒绝该动作，不得视为成功。
   *
   * @param command 命令中必须带规范动作名、当前用户、评论、表单、payload、幂等键和映射版本
   */
  Try<ExternalActionResult> execute(ExternalActionCommand command);
}
