package com.ouroboros.workflow.approval.external.runtime.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

class ExternalEventRetrySweeperTest {

  @Test
  void doesNotReingestWhenRetryResetDidNotUpdateAnyRow() {
    ExternalEventInboxService inboxService = mock(ExternalEventInboxService.class);
    ExternalEventProcessor processor = mock(ExternalEventProcessor.class);
    ExternalEventInbox row = new ExternalEventInbox();
    row.setId("inbox-1");
    row.setStatus(ExternalEventInbox.STATUS_FAILED);
    row.setRetryCount(0);

    when(inboxService.findPendingOlderThan(any())).thenReturn(List.of());
    when(inboxService.findExpiredProcessing(any())).thenReturn(List.of());
    when(inboxService.findFailedRetryable(any())).thenReturn(List.of(row));
    when(inboxService.requeueAutomatically(org.mockito.ArgumentMatchers.eq(row.getId()), any())).thenReturn(
        Try.failure(new IllegalStateException("状态已变化")));

    new ExternalEventRetrySweeper(inboxService, processor).sweep();

    verifyNoInteractions(processor);
  }

  @Test
  void capsSingleSweepAtOneHundredRows() {
    ExternalEventInboxService inboxService = mock(ExternalEventInboxService.class);
    ExternalEventProcessor processor = mock(ExternalEventProcessor.class);
    List<ExternalEventInbox> rows = java.util.stream.IntStream.range(0, 101)
        .mapToObj(index -> {
          ExternalEventInbox row = new ExternalEventInbox();
          row.setId("inbox-" + index);
          row.setStatus(ExternalEventInbox.STATUS_PENDING);
          return row;
        })
        .toList();
    when(inboxService.findPendingOlderThan(any())).thenReturn(rows);
    when(processor.processRow(any())).thenReturn(Try.success(null));

    new ExternalEventRetrySweeper(inboxService, processor).sweep();

    verify(processor, times(100)).processRow(any());
    verify(inboxService).findExpiredProcessing(any());
    verify(inboxService).findFailedRetryable(any());
  }

  @Test
  void retriesFailedRowsBeforeLargePendingBacklog() {
    ExternalEventInboxService inboxService = mock(ExternalEventInboxService.class);
    ExternalEventProcessor processor = mock(ExternalEventProcessor.class);
    ExternalEventInbox failed = new ExternalEventInbox();
    failed.setId("failed-1");
    failed.setStatus(ExternalEventInbox.STATUS_FAILED);
    List<ExternalEventInbox> pendingRows = java.util.stream.IntStream.range(0, 101)
        .mapToObj(index -> {
          ExternalEventInbox row = new ExternalEventInbox();
          row.setId("pending-" + index);
          row.setStatus(ExternalEventInbox.STATUS_PENDING);
          return row;
        })
        .toList();
    when(inboxService.findExpiredProcessing(any())).thenReturn(List.of());
    when(inboxService.findFailedRetryable(any())).thenReturn(List.of(failed));
    when(inboxService.findPendingOlderThan(any())).thenReturn(pendingRows);
    when(inboxService.requeueAutomatically(any(), any())).thenReturn(Try.success(null));
    when(processor.processRow(any())).thenReturn(Try.success(null));

    new ExternalEventRetrySweeper(inboxService, processor).sweep();

    verify(inboxService).requeueAutomatically(org.mockito.ArgumentMatchers.eq("failed-1"), any());
    verify(processor, times(100)).processRow(any());
  }
}
