package com.ouroboros.workflow.approval.external.runtime.event;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;

/**
 * 外部事件入箱服务：所有结果获取通道（HTTP 回调、适配器自有接收器、轮询补偿）统一经本服务
 * 幂等写入收件箱；HTTP 入口与适配器自有接收器共用同一条幂等/重试路径。
 */
public interface ExternalEventIngestService {

  /**
   * 幂等入箱：同 (adapterId, connectionId, eventId) 已存在时返回成功但不重复写入/处理。
   */
  Try<Void> ingest(ExternalCallbackEnvelope envelope);
}
