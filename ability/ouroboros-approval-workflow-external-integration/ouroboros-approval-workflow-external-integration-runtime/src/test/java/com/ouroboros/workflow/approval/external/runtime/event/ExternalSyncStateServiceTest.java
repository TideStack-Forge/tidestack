package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.exception.UniqueConstraintViolationException;

import io.vavr.control.Try;

class ExternalSyncStateServiceTest {

  private Map<String, ExternalSyncState> store;
  private ExternalSyncStateService service;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    service = new InMemorySyncStateService(mockDataModel(store));
  }

  @Test
  void sameConnectionCanOnlyBeClaimedByOneNode() {
    var first = service.claim("adapter-1", "conn-1", "node-a", 60);
    var second = service.claim("adapter-1", "conn-1", "node-b", 60);

    assertThat(first).isPresent();
    assertThat(second).isEmpty();
    assertThat(store.get("adapter-1:conn-1").getClaimOwner()).isEqualTo("node-a");
  }

  @Test
  void staleTokenCannotAdvanceCursorAfterExpiredLeaseIsReclaimed() {
    var first = service.claim("adapter-1", "conn-1", "node-a", 60).orElseThrow();
    store.get("adapter-1:conn-1").setClaimUntil(LocalDateTime.now().minusSeconds(1));
    var second = service.claim("adapter-1", "conn-1", "node-b", 60).orElseThrow();
    LocalDateTime syncedAt = LocalDateTime.now();

    assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
    assertThat(service.recordSync("adapter-1:conn-1", first.claimToken(), syncedAt, "stale")).isFalse();
    assertThat(service.recordSync("adapter-1:conn-1", second.claimToken(), syncedAt, "cursor-2")).isTrue();

    ExternalSyncState state = store.get("adapter-1:conn-1");
    assertThat(state.getCursor()).isEqualTo("cursor-2");
    assertThat(state.getLastSyncAt()).isEqualTo(syncedAt);
    assertThat(state.getClaimToken()).isNull();
  }

  @Test
  void recordingNonTerminalPageReleasesClaimAndPreservesRunCheckpoint() {
    var claim = service.claim("adapter-1", "conn-1", "node-a", 60).orElseThrow();
    LocalDateTime runFrom = LocalDateTime.now().minusMinutes(5);
    LocalDateTime runUntil = LocalDateTime.now();

    assertThat(service.beginRun(claim.state().getId(), claim.claimToken(), runFrom, runUntil)).isTrue();
    assertThat(service.recordPage(claim.state().getId(), claim.claimToken(), "cursor-2")).isTrue();

    ExternalSyncState state = store.get("adapter-1:conn-1");
    assertThat(state.getCursor()).isEqualTo("cursor-2");
    assertThat(state.getRunFrom()).isEqualTo(runFrom);
    assertThat(state.getRunUntil()).isEqualTo(runUntil);
    assertThat(state.getClaimOwner()).isNull();
    assertThat(state.getClaimToken()).isNull();
    assertThat(state.getClaimUntil()).isNull();
  }

  @Test
  void concurrentInitialInsertConflictEndsCurrentClaimWithoutRequeryingInSameTransaction() {
    TypedDataModel<String, ExternalSyncState> dataModel = mock(TypedDataModel.class);
    DataModel meta = mock(DataModel.class);
    when(dataModel.getDataModel()).thenReturn(meta);
    when(meta.getFields()).thenReturn(java.util.List.of());
    when(dataModel.get(any(), anyMap())).thenReturn(Try.success(null));
    when(dataModel.insert(any(ExternalSyncState.class)))
        .thenReturn(Try.failure(new UniqueConstraintViolationException("concurrent insert")));
    service = new InMemorySyncStateService(dataModel);

    assertThat(service.claim("adapter-1", "conn-1", "node-a", 60)).isEmpty();

    verify(dataModel, times(1)).get(any(), anyMap());
  }

  private TypedDataModel<String, ExternalSyncState> mockDataModel(Map<String, ExternalSyncState> states) {
    TypedDataModel<String, ExternalSyncState> dataModel = mock(TypedDataModel.class);
    DataModel meta = mock(DataModel.class);
    when(dataModel.getDataModel()).thenReturn(meta);
    when(meta.getFields()).thenReturn(java.util.List.of());
    when(dataModel.insert(any(ExternalSyncState.class))).thenAnswer(invocation -> {
      ExternalSyncState state = invocation.getArgument(0);
      states.put(state.getId(), state);
      return Try.success(state);
    });
    when(dataModel.get(any(), anyMap())).thenAnswer(invocation ->
        Try.success(copyOf(states.get(invocation.getArgument(0)))));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(invocation -> {
      Map<String, Object> where = invocation.getArgument(0);
      Map<String, Object> data = invocation.getArgument(1);
      long rows = 0;
      for (ExternalSyncState state : states.values()) {
        if (matches(state, where)) {
          apply(state, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dataModel;
  }

  private boolean matches(ExternalSyncState state, Map<String, Object> where) {
    for (Map.Entry<String, Object> entry : where.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> condition)) {
        continue;
      }
      Object actual = switch (entry.getKey()) {
        case "id" -> state.getId();
        case "claimToken" -> state.getClaimToken();
        case "claimUntil" -> state.getClaimUntil();
        case "updatedAt" -> state.getUpdatedAt();
        default -> null;
      };
      if (!Objects.equals(condition.get("="), actual)) {
        return false;
      }
    }
    return true;
  }

  private void apply(ExternalSyncState state, Map<String, Object> data) {
    data.forEach((field, value) -> {
      switch (field) {
        case "lastSyncAt" -> state.setLastSyncAt((LocalDateTime) value);
        case "runFrom" -> state.setRunFrom((LocalDateTime) value);
        case "runUntil" -> state.setRunUntil((LocalDateTime) value);
        case "cursor" -> state.setCursor((String) value);
        case "claimOwner" -> state.setClaimOwner((String) value);
        case "claimToken" -> state.setClaimToken((String) value);
        case "claimUntil" -> state.setClaimUntil((LocalDateTime) value);
        case "updatedAt" -> state.setUpdatedAt((LocalDateTime) value);
        default -> {
        }
      }
    });
  }

  private ExternalSyncState copyOf(ExternalSyncState source) {
    if (source == null) {
      return null;
    }
    ExternalSyncState copy = new ExternalSyncState();
    copy.setId(source.getId());
    copy.setAdapterId(source.getAdapterId());
    copy.setConnectionId(source.getConnectionId());
    copy.setLastSyncAt(source.getLastSyncAt());
    copy.setRunFrom(source.getRunFrom());
    copy.setRunUntil(source.getRunUntil());
    copy.setCursor(source.getCursor());
    copy.setClaimOwner(source.getClaimOwner());
    copy.setClaimToken(source.getClaimToken());
    copy.setClaimUntil(source.getClaimUntil());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }

  private static final class InMemorySyncStateService extends ExternalSyncStateService {
    private final TypedDataModel<String, ExternalSyncState> dataModel;

    private InMemorySyncStateService(TypedDataModel<String, ExternalSyncState> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalSyncState> getDataModel() {
      return dataModel;
    }
  }
}
