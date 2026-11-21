package com.ouroboros.workflow.approval.external;

import java.util.Map;

/**
 * 原始外部回调请求。入口只负责限流、读取原始请求并交给 codec；不得在 HTTP 线程中直接处理业务。
 *
 * @param adapterId    适配器 ID
 * @param connectionId 连接 ID
 * @param headers      原始请求头
 * @param body         原始请求体
 */
public record ExternalCallbackRequest(
    String adapterId,
    String connectionId,
    Map<String, String> headers,
    String body) {
}
