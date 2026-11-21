package com.ouroboros.workflow.approval.external;

import java.util.List;

import io.vavr.control.Try;

/**
 * 外部回调编解码端口：唯一允许接触原始 HTTP/SDK 回调的地方。
 *
 * <p>验证签名、时间窗和重放保护后返回规范化事件包列表；批量推送（一次回调携带多个事件）在
 * codec 内拆分，入口与 inbox 始终按单个事件处理。
 */
public interface ExternalCallbackCodec {

  /**
   * 验证并解析原始回调请求。
   *
   * @param request 原始回调（headers + body）
   * @return 规范化事件包列表；验签失败返回失败而不是抛出
   */
  Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request);
}
