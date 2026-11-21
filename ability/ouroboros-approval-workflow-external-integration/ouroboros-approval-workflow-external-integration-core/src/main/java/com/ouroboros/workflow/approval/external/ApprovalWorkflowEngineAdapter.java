package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部流程引擎适配器最小标识接口。
 *
 * <p>具体能力按端口拆分（见 {@link ExternalWorkflowStarter}、{@link ExternalInstanceReader} 等），
 * 不要让适配器被迫实现所有外部能力。能力集由 Registry 按实现的端口派生。
 */
public interface ApprovalWorkflowEngineAdapter {

  /**
   * 连接健康检查。映射激活前必须调用；失败应返回不健康的描述而不是抛出。
   */
  Try<ExternalHealth> health();
}
