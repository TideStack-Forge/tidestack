package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部任务读取端口。仅用于 {@code authority=EXTERNAL_ENGINE + integrationMode=FULL_PROCESS}。
 */
public interface ExternalTaskReader {

  /**
   * 按用户/条件查询外部任务分页（待办列表聚合）。
   */
  Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query);

  /**
   * 查询单个外部任务快照（详情补偿查询）。
   */
  Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task);

  /**
   * 获取任务表单快照，支撑本地审批中心表单展示。无表单能力时返回能力不支持错误。
   */
  Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task);
}
