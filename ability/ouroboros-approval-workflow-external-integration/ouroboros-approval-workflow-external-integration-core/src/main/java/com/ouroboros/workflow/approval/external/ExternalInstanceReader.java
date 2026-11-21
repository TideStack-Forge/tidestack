package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部实例增量读取端口。仅用于 {@code authority=EXTERNAL_ENGINE}。
 *
 * <p>实现本端口表示 provider 能按固定时间窗和游标扫描变化实例；只支持按已知 ID
 * 查询详情的 provider 应实现 {@link ExternalInstanceDetailReader}。
 */
public interface ExternalInstanceReader extends ExternalInstanceDetailReader {

  /**
   * 增量对账查询：按时间窗/游标返回发生变化的实例分页。禁止基于 offset 拼接分页。
   */
  Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query);
}
