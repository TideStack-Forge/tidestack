package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部实例详情读取端口。仅用于 {@code authority=EXTERNAL_ENGINE}。
 *
 * <p>该端口只承诺按已知外部实例 ID 读取快照，不隐含全局增量扫描能力。
 */
public interface ExternalInstanceDetailReader {

  /** 查询单个外部实例快照，用于已知 binding 的状态补偿。 */
  Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance);
}
