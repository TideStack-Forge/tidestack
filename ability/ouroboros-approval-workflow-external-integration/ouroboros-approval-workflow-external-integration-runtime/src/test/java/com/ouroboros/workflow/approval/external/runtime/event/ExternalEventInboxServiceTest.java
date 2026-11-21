package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.exception.BusinessException;

import io.vavr.control.Try;

class ExternalEventInboxServiceTest {

  private Map<String, ExternalEventInbox> store;
  private List<Map<String, Object>> updateStatements;
  private List<Map<String, Object>> queryStatements;
  private AtomicBoolean forceZeroUpdate;
  private InMemoryInboxService service;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    updateStatements = new ArrayList<>();
    queryStatements = new ArrayList<>();
    forceZeroUpdate = new AtomicBoolean();
    service = new InMemoryInboxService(
        mockDataModel(store, updateStatements, queryStatements, forceZeroUpdate), store);
  }

  @Test
  void pendingRowCanOnlyBeClaimedOnce() {
    ExternalEventInbox inbox = pending("inbox-1");
    store.put(inbox.getId(), inbox);

    var first = service.claim(inbox.getId(), "worker-a", 60);
    var second = service.claim(inbox.getId(), "worker-b", 60);

    assertThat(first).isPresent();
    assertThat(second).isEmpty();
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSING);
    assertThat(store.get(inbox.getId()).getClaimOwner()).isEqualTo("worker-a");
    assertThat(store.get(inbox.getId()).getClaimToken()).isEqualTo(first.get().claimToken());
  }

  @Test
  void expiredProcessingCanBeReclaimedAndOldTokenCannotComplete() {
    ExternalEventInbox inbox = pending("inbox-2");
    store.put(inbox.getId(), inbox);
    var first = service.claim(inbox.getId(), "worker-a", 60).orElseThrow();
    String oldToken = first.claimToken();

    assertThat(service.claim(inbox.getId(), "worker-b", 60)).isEmpty();

    store.get(inbox.getId()).setClaimUntil(LocalDateTime.now().minusSeconds(1));
    var second = service.claim(inbox.getId(), "worker-b", 60).orElseThrow();

    assertThat(second.claimToken()).isNotEqualTo(oldToken);
    assertThat(service.markProcessed(inbox.getId(), oldToken)).isFalse();
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSING);
    assertThat(store.get(inbox.getId()).getClaimOwner()).isEqualTo("worker-b");

    assertThat(service.markProcessed(inbox.getId(), second.claimToken())).isTrue();
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    assertThat(store.get(inbox.getId()).getClaimToken()).isNull();
    assertThat(store.get(inbox.getId()).getClaimUntil()).isNull();
  }

  @Test
  void markFailureRequiresCurrentClaimTokenAndMovesPastMaxRetryToDead() {
    ExternalEventInbox inbox = pending("inbox-3");
    inbox.setRetryCount(ExternalEventInbox.MAX_RETRY_COUNT);
    store.put(inbox.getId(), inbox);
    var claim = service.claim(inbox.getId(), "worker-a", 60).orElseThrow();

    assertThat(service.markFailure(inbox.getId(), "wrong-token", "ignored")).isFalse();
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSING);

    assertThat(service.markFailure(inbox.getId(), claim.claimToken(), "boom")).isTrue();
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_DEAD);
    assertThat(store.get(inbox.getId()).getRetryCount()).isEqualTo(ExternalEventInbox.MAX_RETRY_COUNT + 1);
    assertThat(store.get(inbox.getId()).getLastError()).isEqualTo("boom");
    assertThat(store.get(inbox.getId()).getClaimToken()).isNull();
  }

  @Test
  void automaticRequeuePreservesRetryBudgetAndClearsClaimFields() {
    ExternalEventInbox inbox = pending("inbox-4");
    inbox.setStatus(ExternalEventInbox.STATUS_FAILED);
    inbox.setRetryCount(3);
    inbox.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
    inbox.setClaimOwner("old-worker");
    inbox.setClaimToken("old-token");
    inbox.setClaimUntil(LocalDateTime.now().minusMinutes(1));
    inbox.setLastError("boom");
    store.put(inbox.getId(), inbox);

    LocalDateTime now = LocalDateTime.now();
    var retry = service.requeueAutomatically(inbox.getId(), now);

    assertThat(retry.isSuccess()).isTrue();
    assertThat(updateStatements).singleElement().satisfies(statement -> {
      assertThat(statement.get("id")).isEqualTo(Map.of("=", inbox.getId()));
      assertThat(statement.get("status")).isEqualTo(Map.of("=", ExternalEventInbox.STATUS_FAILED));
      assertThat(statement.get("retryCount")).isEqualTo(Map.of("=", 3));
    });
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PENDING);
    assertThat(store.get(inbox.getId()).getRetryCount()).isEqualTo(3);
    assertThat(store.get(inbox.getId()).getNextAttemptAt()).isNull();
    assertThat(store.get(inbox.getId()).getClaimToken()).isNull();
    assertThat(store.get(inbox.getId()).getLastError()).isEqualTo("boom");
  }

  @Test
  void manualReplayResetsRetryBudgetForPersistedDeadStatus() {
    ExternalEventInbox inbox = pending("inbox-5");
    inbox.setStatus(ExternalEventInbox.STATUS_DEAD);
    inbox.setRetryCount(ExternalEventInbox.MAX_RETRY_COUNT + 1);
    inbox.setClaimOwner("old-worker");
    inbox.setClaimToken("old-token");
    inbox.setClaimUntil(LocalDateTime.now().minusMinutes(1));
    inbox.setLastError("boom");
    store.put(inbox.getId(), inbox);

    var retry = service.replayManually(inbox.getId());

    assertThat(retry.isSuccess()).isTrue();
    assertThat(updateStatements).singleElement().satisfies(statement ->
        assertThat(statement.get("status")).isEqualTo(Map.of("=", ExternalEventInbox.STATUS_DEAD)));
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_PENDING);
    assertThat(store.get(inbox.getId()).getRetryCount()).isZero();
    assertThat(store.get(inbox.getId()).getClaimOwner()).isNull();
    assertThat(store.get(inbox.getId()).getClaimToken()).isNull();
    assertThat(store.get(inbox.getId()).getClaimUntil()).isNull();
    assertThat(store.get(inbox.getId()).getLastError()).isNull();
  }

  @Test
  void automaticRequeueFailsWhenConditionalUpdateAffectsNoRows() {
    ExternalEventInbox inbox = pending("inbox-6");
    inbox.setStatus(ExternalEventInbox.STATUS_FAILED);
    inbox.setRetryCount(2);
    inbox.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
    inbox.setLastError("boom");
    store.put(inbox.getId(), inbox);
    forceZeroUpdate.set(true);

    var retry = service.requeueAutomatically(inbox.getId(), LocalDateTime.now());

    assertThat(retry.isFailure()).isTrue();
    assertThat(retry.getCause()).isInstanceOf(BusinessException.class);
    assertThat(store.get(inbox.getId()).getStatus()).isEqualTo(ExternalEventInbox.STATUS_FAILED);
    assertThat(store.get(inbox.getId()).getRetryCount()).isEqualTo(2);
    assertThat(store.get(inbox.getId()).getLastError()).isEqualTo("boom");
  }

  @Test
  void recoveryQueriesPushFilteringOrderingAndLimitToDataModel() {
    LocalDateTime now = LocalDateTime.now();

    service.findPendingOlderThan(now, 7);
    service.findFailedRetryable(now, 8);
    service.findExpiredProcessing(now, 9);

    assertThat(queryStatements).hasSize(3);
    Map<String, Object> pending = queryStatements.get(0);
    assertThat(pending.get("WHERE")).isEqualTo(Map.of(
        "status", Map.of("=", ExternalEventInbox.STATUS_PENDING),
        "receivedAt", Map.of("<", now)));
    assertThat(pending.get("ORDER")).isEqualTo(Map.of(
        "receivedAt", "asc", "createdAt", "asc", "id", "asc"));
    assertThat(pending.get("LIMIT")).isEqualTo(7);

    Map<String, Object> failed = queryStatements.get(1);
    Map<String, Object> expectedFailedWhere = new LinkedHashMap<>();
    expectedFailedWhere.put("status", Map.of("=", ExternalEventInbox.STATUS_FAILED));
    expectedFailedWhere.put("retryCount", Map.of("<=", ExternalEventInbox.MAX_RETRY_COUNT));
    Map<String, Object> due = new LinkedHashMap<>();
    due.put("nextAttemptAt", Map.of("<=", now));
    Map<String, Object> immediate = new LinkedHashMap<>();
    immediate.put("nextAttemptAt", null);
    expectedFailedWhere.put("$or", List.of(due, immediate));
    assertThat(failed.get("WHERE")).isEqualTo(expectedFailedWhere);
    assertThat(failed.get("ORDER")).isEqualTo(Map.of(
        "nextAttemptAt", "asc", "createdAt", "asc", "id", "asc"));
    assertThat(failed.get("LIMIT")).isEqualTo(8);

    Map<String, Object> processing = queryStatements.get(2);
    assertThat(processing.get("WHERE")).isEqualTo(Map.of(
        "status", Map.of("=", ExternalEventInbox.STATUS_PROCESSING),
        "claimUntil", Map.of("<=", now)));
    assertThat(processing.get("ORDER")).isEqualTo(Map.of(
        "claimUntil", "asc", "createdAt", "asc", "id", "asc"));
    assertThat(processing.get("LIMIT")).isEqualTo(9);
  }

  @Test
  void createIfAbsentReturnsNullForSameEventFact() {
    ExternalEventInbox first = pending("inbox-7");
    first.setPayloadDigest("digest-1");
    var created = service.createIfAbsent(first);
    assertThat(created.isSuccess()).isTrue();
    assertThat(created.get()).isSameAs(first);

    ExternalEventInbox retry = pending("inbox-8");
    retry.setEventId(first.getEventId());
    retry.setExternalInstanceId(first.getExternalInstanceId());
    retry.setPayloadDigest("digest-1");
    var duplicate = service.createIfAbsent(retry);
    assertThat(duplicate.isSuccess()).isTrue();
    assertThat(duplicate.get()).isNull();
    assertThat(store).hasSize(1);
  }

  @Test
  void createIfAbsentRejectsSameEventIdWithDifferentFact() {
    ExternalEventInbox first = pending("inbox-9");
    first.setPayloadDigest("digest-1");
    service.createIfAbsent(first);

    ExternalEventInbox conflicting = pending("inbox-10");
    conflicting.setEventId(first.getEventId());
    conflicting.setPayloadDigest("digest-2");
    var result = service.createIfAbsent(conflicting);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).isInstanceOf(BusinessException.class)
        .hasMessageContaining("EXTERNAL_ACTION_CONFLICT");
    assertThat(store).hasSize(1);
  }

  private ExternalEventInbox pending(String id) {
    ExternalEventInbox inbox = new ExternalEventInbox();
    inbox.setId(id);
    inbox.setAdapterId("adapter-a");
    inbox.setConnectionId("conn-a");
    inbox.setEventId("event-" + id);
    inbox.setExternalInstanceId("ext-" + id);
    inbox.setStatus(ExternalEventInbox.STATUS_PENDING);
    inbox.setRetryCount(0);
    inbox.setReceivedAt(LocalDateTime.now());
    inbox.setCreatedAt(LocalDateTime.now());
    inbox.setUpdatedAt(LocalDateTime.now());
    return inbox;
  }

  static TypedDataModel<String, ExternalEventInbox> mockDataModel(
      Map<String, ExternalEventInbox> store,
      List<Map<String, Object>> updateStatements,
      List<Map<String, Object>> queryStatements,
      AtomicBoolean forceZeroUpdate) {
    TypedDataModel<String, ExternalEventInbox> dm = mock(TypedDataModel.class);
    when(dm.insert(any(ExternalEventInbox.class))).thenAnswer(inv -> {
      ExternalEventInbox inbox = inv.getArgument(0);
      if (store.values().stream().anyMatch(existing ->
          Objects.equals(existing.getAdapterId(), inbox.getAdapterId())
              && Objects.equals(existing.getConnectionId(), inbox.getConnectionId())
              && Objects.equals(existing.getEventId(), inbox.getEventId()))) {
        return Try.failure(new UniqueConstraintViolationException("duplicate event"));
      }
      store.put(inbox.getId(), inbox);
      return Try.success(inbox);
    });
    when(dm.get(any(), anyMap())).thenAnswer(inv -> {
      ExternalEventInbox inbox = store.get(inv.getArgument(0));
      return Try.success(copyOf(inbox));
    });
    when(dm.query(anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      queryStatements.add(statement);
      return Try.success(new ArrayList<>(store.values().stream()
          .filter(inbox -> matchesStatement(inbox, statement))
          .toList()));
    });
    when(dm.update(anyMap(), anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      updateStatements.add(statement);
      if (forceZeroUpdate.getAndSet(false)) {
        return Try.success(0L);
      }
      long rows = 0;
      for (ExternalEventInbox inbox : store.values()) {
        if (matchesStatement(inbox, statement)) {
          applyData(inbox, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dm;
  }

  static ExternalEventInbox copyOf(ExternalEventInbox source) {
    if (source == null) {
      return null;
    }
    ExternalEventInbox copy = new ExternalEventInbox();
    copy.setId(source.getId());
    copy.setAdapterId(source.getAdapterId());
    copy.setConnectionId(source.getConnectionId());
    copy.setEventId(source.getEventId());
    copy.setExternalInstanceId(source.getExternalInstanceId());
    copy.setExternalTaskId(source.getExternalTaskId());
    copy.setSequence(source.getSequence());
    copy.setExternalEventTime(source.getExternalEventTime());
    copy.setEventType(source.getEventType());
    copy.setResultCode(source.getResultCode());
    copy.setPayloadDigest(source.getPayloadDigest());
    copy.setStatus(source.getStatus());
    copy.setRetryCount(source.getRetryCount());
    copy.setNextAttemptAt(source.getNextAttemptAt());
    copy.setLastError(source.getLastError());
    copy.setClaimOwner(source.getClaimOwner());
    copy.setClaimToken(source.getClaimToken());
    copy.setClaimUntil(source.getClaimUntil());
    copy.setReceivedAt(source.getReceivedAt());
    copy.setProcessedAt(source.getProcessedAt());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }

  static boolean matchesStatement(ExternalEventInbox inbox, Map<String, Object> statement) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> condition)) {
        continue;
      }
      Object actual = switch (entry.getKey()) {
        case "id" -> inbox.getId();
        case "adapterId" -> inbox.getAdapterId();
        case "connectionId" -> inbox.getConnectionId();
        case "eventId" -> inbox.getEventId();
        case "status" -> inbox.getStatus();
        case "retryCount" -> inbox.getRetryCount();
        case "nextAttemptAt" -> inbox.getNextAttemptAt();
        case "claimToken" -> inbox.getClaimToken();
        case "claimUntil" -> inbox.getClaimUntil();
        default -> null;
      };
      if (condition.containsKey("=") && !Objects.equals(condition.get("="), actual)) {
        return false;
      }
      if (condition.containsKey("<") && !isBefore(actual, condition.get("<"))) {
        return false;
      }
    }
    return true;
  }

  static boolean isBefore(Object actual, Object expected) {
    if (actual instanceof LocalDateTime actualTime && expected instanceof LocalDateTime expectedTime) {
      return actualTime.isBefore(expectedTime);
    }
    return false;
  }

  static void applyData(ExternalEventInbox inbox, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> inbox.setStatus((String) value);
        case "retryCount" -> inbox.setRetryCount((Integer) value);
        case "nextAttemptAt" -> inbox.setNextAttemptAt((LocalDateTime) value);
        case "lastError" -> inbox.setLastError((String) value);
        case "claimOwner" -> inbox.setClaimOwner((String) value);
        case "claimToken" -> inbox.setClaimToken((String) value);
        case "claimUntil" -> inbox.setClaimUntil((LocalDateTime) value);
        case "processedAt" -> inbox.setProcessedAt((LocalDateTime) value);
        case "updatedAt" -> inbox.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  static final class InMemoryInboxService extends ExternalEventInboxService {
    private final TypedDataModel<String, ExternalEventInbox> dataModel;
    private final Map<String, ExternalEventInbox> store;

    InMemoryInboxService(
        TypedDataModel<String, ExternalEventInbox> dataModel,
        Map<String, ExternalEventInbox> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, ExternalEventInbox> getDataModel() {
      return dataModel;
    }

    @Override
    public Boolean update(String id, ExternalEventInbox model) {
      store.put(id, model);
      return true;
    }
  }
}
