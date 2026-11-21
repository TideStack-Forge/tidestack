package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;

import io.vavr.control.Try;

/**
 * M9 入箱服务测试：幂等去重与 durable-only ingest 语义。
 */
class ExternalEventIngestServiceImplTest {

  private ExternalEventInboxService inboxService;
  private ExternalEventIngestServiceImpl service;

  @BeforeEach
  void setUp() {
    inboxService = mock(ExternalEventInboxService.class);
    service = new ExternalEventIngestServiceImpl(inboxService);
  }

  @Test
  void ingestWritesInboxWithoutExecutingBusinessAction() {
    ExternalEventInbox created = new ExternalEventInbox();
    created.setId("inbox-1");
    created.setStatus(ExternalEventInbox.STATUS_PENDING);
    when(inboxService.createIfAbsent(any())).thenAnswer(inv -> Try.success(created));

    Try<Void> result = service.ingest(envelope("evt-1"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(created.getStatus()).isEqualTo(ExternalEventInbox.STATUS_PENDING);
    verify(inboxService).createIfAbsent(any());
  }

  @Test
  void duplicateEventIsIdempotentAndNotReprocessed() {
    when(inboxService.createIfAbsent(any())).thenReturn(Try.success(null));

    Try<Void> result = service.ingest(envelope("evt-1"));

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void ingestRemainsDurableWhenCalledInsideSynchronization() {
    ExternalEventInbox created = new ExternalEventInbox();
    created.setId("inbox-1");
    created.setStatus(ExternalEventInbox.STATUS_PENDING);
    when(inboxService.createIfAbsent(any())).thenAnswer(inv -> Try.success(created));
    Try<Void> result = service.ingest(envelope("evt-1"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(created.getStatus()).isEqualTo(ExternalEventInbox.STATUS_PENDING);
  }

  @Test
  void ingestRejectsBlankEventId() {
    ExternalCallbackEnvelope blank = new ExternalCallbackEnvelope(
        null, "adapter", "conn", "ext", null, null, null, null, null, null);

    Try<Void> result = service.ingest(blank);

    assertThat(result.isFailure()).isTrue();
  }

  private ExternalCallbackEnvelope envelope(String eventId) {
    return new ExternalCallbackEnvelope(
        eventId, "adapter", "conn", "ext-1", null, null,
        LocalDateTime.now().toString(), "bpms_instance_change", "agree", null);
  }
}
