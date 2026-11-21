package com.ouroboros.workflow.approval.external.runtime.web.vo;

import java.util.List;

/**
 * 批量验证报告（M21-T1）：对全部 ACTIVE 映射逐项执行只读校验（dry-run 校验/连接健康/外部定义校验），
 * 单项失败不中断整体。message 只含错误码与原因摘要（超 200 字符截断），不含凭据/密钥。
 */
public record BatchValidationReportVO(
    List<Item> items, int total, int okCount, int failedCount) {

  public record Item(
      String mappingId,
      String flowKey,
      String adapterId,
      String connectionId,
      String status,
      String message) {
  }
}
