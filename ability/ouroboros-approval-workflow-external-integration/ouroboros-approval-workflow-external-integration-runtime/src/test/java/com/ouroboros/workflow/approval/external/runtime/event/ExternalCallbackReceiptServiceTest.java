package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;

import io.vavr.control.Try;

class ExternalCallbackReceiptServiceTest {

  @Test
  void verifiedBatchIsIngestedBeforeReceiptCompletes() {
    TypedDataModel<String, ExternalCallbackReceipt> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of()));
    when(dataModel.insert(any(ExternalCallbackReceipt.class)))
        .thenAnswer(invocation -> Try.success(invocation.getArgument(0)));
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));
    ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);
    when(ingest.ingest(any())).thenReturn(Try.success(null));

    TestService service = new TestService(dataModel, ingest);
    Try<Void> result = service.receive("adapter-a", "connection-a", "nonce-a", "digest-a",
        List.of(envelope("event-a"), envelope("event-b")));

    assertThat(result.isSuccess()).isTrue();
    verify(dataModel).insert(any(ExternalCallbackReceipt.class));
    verify(ingest, org.mockito.Mockito.times(2)).ingest(any());
    verify(dataModel).update(anyMap(), org.mockito.ArgumentMatchers.argThat(data ->
        ExternalCallbackReceipt.STATUS_COMPLETED.equals(data.get("status"))));
  }

  @Test
  void sameNonceWithDifferentDigestIsAConflictBeforeIngest() {
    TypedDataModel<String, ExternalCallbackReceipt> dataModel = mock(TypedDataModel.class);
    ExternalCallbackReceipt existing = receipt("adapter-a", "connection-a", "nonce-a", "digest-a");
    when(dataModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of(existing)));
    ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);

    TestService service = new TestService(dataModel, ingest);
    Try<Void> result = service.receive("adapter-a", "connection-a", "nonce-a", "digest-b",
        List.of(envelope("event-a")));

    assertThat(result.isFailure()).isTrue();
    verify(ingest, never()).ingest(any());
  }

  @Test
  void liveClaimCannotBeTakenByAnotherWorker() {
    TypedDataModel<String, ExternalCallbackReceipt> dataModel = mock(TypedDataModel.class);
    ExternalCallbackReceipt existing = receipt("adapter-a", "connection-a", "nonce-a", "digest-a");
    existing.setStatus(ExternalCallbackReceipt.STATUS_RECEIVING);
    existing.setClaimUntil(LocalDateTime.now().plusMinutes(1));
    when(dataModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of(existing)));
    ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);

    TestService service = new TestService(dataModel, ingest);
    Try<Void> result = service.receive("adapter-a", "connection-a", "nonce-a", "digest-a",
        List.of(envelope("event-a")));

    assertThat(result.isFailure()).isTrue();
    verify(dataModel, never()).update(anyMap(), anyMap());
    verify(ingest, never()).ingest(any());
  }

  @Test
  void failedBatchReleasesClaimAndCanBeRetriedImmediately() {
    TypedDataModel<String, ExternalCallbackReceipt> dataModel = mock(TypedDataModel.class);
    ExternalCallbackReceipt existing = receipt("adapter-a", "connection-a", "nonce-a", "digest-a");
    existing.setEventCount(2);
    existing.setClaimToken("expired-token");
    existing.setClaimUntil(LocalDateTime.now().minusMinutes(1));
    when(dataModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of(existing)));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(invocation -> {
      Map<?, ?> data = invocation.getArgument(1);
      data.forEach((field, value) -> {
        switch (String.valueOf(field)) {
          case "status" -> existing.setStatus((String) value);
          case "claimToken" -> existing.setClaimToken((String) value);
          case "claimUntil" -> existing.setClaimUntil((LocalDateTime) value);
          case "lastError" -> existing.setLastError((String) value);
          case "completedAt" -> existing.setCompletedAt((LocalDateTime) value);
          default -> { }
        }
      });
      return Try.success(1L);
    });
    ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);
    when(ingest.ingest(any())).thenReturn(
        Try.success(null), Try.failure(new IllegalStateException("second event failed")),
        Try.success(null), Try.success(null));

    TestService service = new TestService(dataModel, ingest);
    List<ExternalCallbackEnvelope> batch = List.of(envelope("event-a"), envelope("event-b"));

    Try<Void> first = service.receive("adapter-a", "connection-a", "nonce-a", "digest-a", batch);
    Try<Void> second = service.receive("adapter-a", "connection-a", "nonce-a", "digest-a", batch);

    assertThat(first.isFailure()).isTrue();
    assertThat(existing.getClaimToken()).isNull();
    assertThat(existing.getClaimUntil()).isNull();
    assertThat(second.isSuccess()).isTrue();
    assertThat(existing.getStatus()).isEqualTo(ExternalCallbackReceipt.STATUS_COMPLETED);
    verify(ingest, org.mockito.Mockito.times(4)).ingest(any());
  }

  @Test
  void retentionCleanupTargetsCompletedReceiptsOnly() {
    TypedDataModel<String, ExternalCallbackReceipt> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of()));
    when(dataModel.insert(any(ExternalCallbackReceipt.class)))
        .thenAnswer(invocation -> Try.success(invocation.getArgument(0)));
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));
    ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);
    when(ingest.ingest(any())).thenReturn(Try.success(null));

    TestService service = new TestService(dataModel, ingest);
    assertThat(service.receive("adapter-a", "connection-a", "nonce-a", "digest-a",
        List.of(envelope("event-a"))).isSuccess()).isTrue();

    verify(dataModel).delete(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(where ->
        where.containsKey("status")
            && where.containsKey("expiresAt")
            && ExternalCallbackReceipt.STATUS_COMPLETED.equals(
                ((Map<?, ?>) where.get("status")).get("="))));
  }

  private ExternalCallbackEnvelope envelope(String eventId) {
    return new ExternalCallbackEnvelope(eventId, "adapter-a", "connection-a", "instance-a",
        null, null, null, "change", "APPROVED", "digest-a");
  }

  private ExternalCallbackReceipt receipt(String adapterId, String connectionId, String nonce, String digest) {
    ExternalCallbackReceipt receipt = new ExternalCallbackReceipt();
    receipt.setId("receipt-a");
    receipt.setAdapterId(adapterId);
    receipt.setConnectionId(connectionId);
    receipt.setNonce(nonce);
    receipt.setPayloadDigest(digest);
    receipt.setEventCount(1);
    receipt.setStatus(ExternalCallbackReceipt.STATUS_RECEIVING);
    return receipt;
  }

  private static final class TestService extends PersistentExternalCallbackReceiptService {
    private final TypedDataModel<String, ExternalCallbackReceipt> dataModel;

    private TestService(
        TypedDataModel<String, ExternalCallbackReceipt> dataModel,
        ExternalEventIngestService ingestService) {
      super(ingestService);
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalCallbackReceipt> getDataModel() {
      return dataModel;
    }
  }
}
