package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部审批记录读取端口：承接审批记录/时间线查询（{@code getTimeLine} 与耗时统计的规范来源）。
 * 不实现该端口的适配器不得伪造记录。
 */
public interface ExternalRecordReader {

  /**
   * 查询外部实例的审批记录分页（时间线）。
   */
  Try<ExternalRecordPage> queryRecords(ExternalInstanceRef instance);
}
