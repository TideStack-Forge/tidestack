package com.ouroboros.workflow.approval.external.fake;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;

import io.vavr.control.Try;

/**
 * fake 适配器回调模拟端点（演示/测试专用，javadoc 注明非生产安全边界）：
 * 组装规范化事件包直投 inbox，与 HTTP 回调入口共用同一幂等/重试路径，不绕过 inbox。
 */
@RestController
@RequestMapping("/api/approval-workflow/external/fake/{connectionId}/simulate-callback")
public class FakeSimulateCallbackController {

  private final ExternalEventIngestService ingestService;

  @Autowired
  public FakeSimulateCallbackController(ExternalEventIngestService ingestService) {
    this.ingestService = ingestService;
  }

  @PostMapping
  public Map<String, Object> simulate(
      @PathVariable String connectionId,
      @RequestBody Map<String, Object> payload) {
    String externalInstanceId = String.valueOf(payload.get("externalInstanceId"));
    String eventId = payload.get("eventId") == null ? UUID.randomUUID().toString() : String.valueOf(payload.get("eventId"));
    String resultCode = payload.get("resultCode") == null ? null : String.valueOf(payload.get("resultCode"));
    String eventType = payload.get("eventType") == null ? null : String.valueOf(payload.get("eventType"));
    ExternalCallbackEnvelope envelope = new ExternalCallbackEnvelope(
        eventId, FakeEngineAdapterProvider.ADAPTER_ID, connectionId, externalInstanceId,
        payload.get("externalTaskId") == null ? null : String.valueOf(payload.get("externalTaskId")),
        null, null, eventType, resultCode, null);
    Try<Void> ingested = ingestService.ingest(envelope);
    if (ingested.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN + "] fake 回调入箱失败: "
              + ingested.getCause().getMessage(), ingested.getCause());
    }
    return Map.of("accepted", Boolean.TRUE, "eventId", eventId);
  }
}
