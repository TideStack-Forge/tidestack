package com.ouroboros.workflow.approval.external.runtime.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;

import io.vavr.control.Try;

/**
 * 外部事件入箱实现：幂等写入收件箱。业务处理由受控 worker 领取 inbox，HTTP 线程不执行动作。
 */
@Service
public class ExternalEventIngestServiceImpl implements ExternalEventIngestService {

  private final ExternalEventInboxService inboxService;

  @Autowired
  public ExternalEventIngestServiceImpl(ExternalEventInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Override
  @Transactional
  public Try<Void> ingest(ExternalCallbackEnvelope envelope) {
    if (envelope == null || envelope.eventId() == null || envelope.eventId().isBlank()) {
      return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN + "] 外部事件缺少 eventId"));
    }
    ExternalEventInbox inbox = new ExternalEventInbox();
    inbox.setAdapterId(envelope.adapterId());
    inbox.setConnectionId(envelope.connectionId());
    inbox.setEventId(envelope.eventId());
    inbox.setExternalInstanceId(envelope.externalInstanceId());
    inbox.setExternalTaskId(envelope.externalTaskId());
    inbox.setSequence(envelope.sequence());
    inbox.setExternalEventTime(envelope.externalEventTime());
    inbox.setEventType(envelope.eventType());
    inbox.setResultCode(envelope.resultCode());
    inbox.setPayloadDigest(envelope.payloadDigest());

    Try<ExternalEventInbox> created = inboxService.createIfAbsent(inbox);
    if (created.isFailure()) {
      return Try.failure(created.getCause());
    }
    if (created.get() == null) {
      // 幂等命中：不重复处理
      return Try.success(null);
    }

    // 仅持久化 PENDING receipt；ExternalEventRetrySweeper 负责受控 claim 和业务处理。
    return Try.success(null);
  }
}
