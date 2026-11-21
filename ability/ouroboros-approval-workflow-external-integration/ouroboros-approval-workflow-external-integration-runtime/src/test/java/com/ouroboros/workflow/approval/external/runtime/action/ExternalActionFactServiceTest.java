package com.ouroboros.workflow.approval.external.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.data.model.DataModelExecutionContext;

import io.vavr.control.Try;

class ExternalActionFactServiceTest {

  private Map<String, ExternalActionFact> store;
  private ExternalActionFactService service;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    service = new InMemoryActionFactService(mockDataModel(store));
  }

  @Test
  void sagaBookkeepingDoesNotInheritBusinessGuardAndRestoresItForBusinessWrites() {
    DataModelWriteGuard guard = new DataModelWriteGuard() {
      @Override
      public void beforeWrite(DataModel model) { throw new IllegalStateException("business resource rejected"); }
      @Override
      public void markRollbackOnly() { }
    };
    DataModelExecutionContext.withWriteGuard(guard, () -> {
      var reservation = service.reserve(actionFact("fact-scoped")).get();
      assertThat(service.markInFlight(reservation.fact().getId(), reservation.fact().getClaimToken())).isTrue();
      assertThat(service.markUnknown(reservation.fact().getId(), reservation.fact().getClaimToken(), "lost response")).isTrue();
      assertThat(DataModelExecutionContext.getWriteGuard()).isSameAs(guard);
      assertThatThrownBy(() -> DataModelExecutionContext.beforeWrite(null))
          .hasMessage("business resource rejected");
      return null;
    });
    assertThat(DataModelExecutionContext.getWriteGuard()).isNull();
    assertThat(store.get("fact-scoped").getStatus()).isEqualTo(ExternalActionFact.STATUS_UNKNOWN);
  }

  @Test
  void terminalFactKeepsBusinessIdempotencyReservationWhileReleasingActiveTarget() {
    ExternalActionFact first = actionFact("fact-1");
    first.setCanonicalAction("approve"); first.setTargetStatus("APPROVED");
    ExternalActionFactService.Reservation firstReservation = service.reserve(first).get();
    String activeRouteKey = first.getActiveRouteKey();
    String idempotencyRouteKey = first.getIdempotencyRouteKey();

    assertThat(service.markSucceeded(first.getId(), firstReservation.fact().getClaimToken())).isTrue();
    assertThat(store.get(first.getId()).getStatus()).isEqualTo(ExternalActionFact.STATUS_SUCCEEDED);
    assertThat(store.get(first.getId()).getActiveRouteKey()).isNotEqualTo(activeRouteKey);
    assertThat(store.get(first.getId()).getIdempotencyRouteKey()).isEqualTo(idempotencyRouteKey);

    var repeated = actionFact("fact-2"); repeated.setCanonicalAction("approve"); repeated.setTargetStatus("APPROVED");
    ExternalActionFactService.Reservation secondReservation = service.reserve(repeated).get();

    assertThat(secondReservation.claimed()).isFalse();
    assertThat(secondReservation.fact().getId()).isEqualTo("fact-1");
    assertThat(store).containsOnlyKeys("fact-1");
  }

  @Test
  void repeatedSuccessfulSameStateTaskCommandsAreIndependentWhileRecoveryKeepsTheOriginalFact() {
    var first = service.reserve(actionFact("fact-remind-1")).get();
    assertThat(service.markReplied(first.fact().getId(), first.fact().getClaimToken(), "{}")).isTrue();
    var repliedAt = first.fact().getRepliedAt();
    assertThat(repliedAt).isNotNull();
    assertThat(service.renewClaim(first.fact().getId(), first.fact().getClaimToken(), 600)).isTrue();
    assertThat(first.fact().getRepliedAt()).isEqualTo(repliedAt);
    assertThat(service.markSucceeded(first.fact().getId(), first.fact().getClaimToken())).isTrue();
    var next = service.reserve(actionFact("fact-remind-2")).get();
    assertThat(next.claimed()).isTrue();
    assertThat(next.fact().getIdempotencyRouteKey()).isNotEqualTo(first.fact().getIdempotencyRouteKey());
    assertThat(store).containsOnlyKeys("fact-remind-1", "fact-remind-2");
  }

  @Test
  void aDifferentCommandCannotReuseAnUnfinishedTaskCommand() {
    assertThat(service.reserve(actionFact("fact-1")).get().claimed()).isTrue();
    var other = actionFact("fact-2"); other.setRequestFingerprint("different");
    assertThat(service.reserve(other).isFailure()).isTrue();
    assertThat(store).containsOnlyKeys("fact-1");
  }

  @Test
  void duplicateRequestWhileActionIsActiveReusesCurrentFact() {
    ExternalActionFact first = actionFact("fact-1");
    assertThat(service.reserve(first).get().claimed()).isTrue();

    ExternalActionFactService.Reservation duplicate = service.reserve(actionFact("fact-2")).get();

    assertThat(duplicate.claimed()).isFalse();
    assertThat(duplicate.fact().getId()).isEqualTo("fact-1");
    assertThat(store).containsOnlyKeys("fact-1");
  }

  @Test
  void leaseRenewalRequiresCurrentUnexpiredClaim() {
    ExternalActionFact fact = actionFact("fact-1");
    ExternalActionFactService.Reservation reservation = service.reserve(fact).get();
    LocalDateTime previous = fact.getClaimUntil();

    assertThat(service.renewClaim(fact.getId(), reservation.fact().getClaimToken(), 600)).isTrue();
    assertThat(fact.getClaimUntil()).isAfter(previous);

    fact.setClaimUntil(LocalDateTime.now().minusSeconds(1));
    assertThat(service.renewClaim(fact.getId(), reservation.fact().getClaimToken(), 600)).isFalse();
  }

  private ExternalActionFact actionFact(String id) {
    ExternalActionFact fact = new ExternalActionFact();
    fact.setId(id);
    fact.setTargetType(ExternalActionFact.TARGET_TASK);
    fact.setTargetLocalId("task-1");
    fact.setSourceState("CREATED");
    fact.setTargetStatus("CREATED");
    fact.setCanonicalAction("remind");
    fact.setRequestFingerprint("same-request");
    fact.setAdapterId("adapter-1");
    fact.setConnectionId("connection-1");
    fact.setMappingVersion(1);
    fact.setCommandJson("{}");
    return fact;
  }

  static TypedDataModel<String, ExternalActionFact> mockDataModel(Map<String, ExternalActionFact> store) {
    TypedDataModel<String, ExternalActionFact> dataModel = mock(TypedDataModel.class);
    when(dataModel.insert(any(ExternalActionFact.class))).thenAnswer(invocation -> {
      DataModelExecutionContext.beforeWrite(null);
      ExternalActionFact fact = invocation.getArgument(0);
      boolean duplicate = store.values().stream().anyMatch(existing ->
          Objects.equals(existing.getActiveRouteKey(), fact.getActiveRouteKey())
              || Objects.equals(existing.getIdempotencyRouteKey(), fact.getIdempotencyRouteKey()));
      if (duplicate) {
        return Try.failure(new UniqueConstraintViolationException("duplicate action fact"));
      }
      store.put(fact.getId(), fact);
      return Try.success(fact);
    });
    when(dataModel.query(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> statement = invocation.getArgument(0);
      return Try.success(new ArrayList<>(store.values().stream()
          .filter(fact -> matches(fact, statement))
          .toList()));
    });
    when(dataModel.get(any(), anyMap())).thenAnswer(invocation ->
        Try.success(store.get(invocation.getArgument(0))));
    when(dataModel.update(anyMap(), anyMap())).thenAnswer(invocation -> {
      DataModelExecutionContext.beforeWrite(null);
      Map<String, Object> statement = invocation.getArgument(0);
      Map<String, Object> data = invocation.getArgument(1);
      long rows = 0;
      for (ExternalActionFact fact : store.values()) {
        if (matches(fact, statement)) {
          apply(fact, data);
          rows++;
        }
      }
      return Try.success(rows);
    });
    return dataModel;
  }

  private static boolean matches(ExternalActionFact fact, Map<String, Object> statement) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> condition)) {
        continue;
      }
      Object actual = switch (entry.getKey()) {
        case "id" -> fact.getId();
        case "activeRouteKey" -> fact.getActiveRouteKey();
        case "idempotencyRouteKey" -> fact.getIdempotencyRouteKey();
        case "status" -> fact.getStatus();
        case "claimToken" -> fact.getClaimToken();
        case "claimUntil" -> fact.getClaimUntil();
        default -> null;
      };
      if (condition.containsKey("=") && !Objects.equals(condition.get("="), actual)) {
        return false;
      }
      if (condition.containsKey(">=")
          && (actual == null || ((LocalDateTime) actual)
              .isBefore((LocalDateTime) condition.get(">=")))) {
        return false;
      }
    }
    return true;
  }

  private static void apply(ExternalActionFact fact, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> fact.setStatus((String) value);
        case "activeRouteKey" -> fact.setActiveRouteKey((String) value);
        case "idempotencyRouteKey" -> fact.setIdempotencyRouteKey((String) value);
        case "claimToken" -> fact.setClaimToken((String) value);
        case "claimUntil" -> fact.setClaimUntil((LocalDateTime) value);
        case "lastError" -> fact.setLastError((String) value);
        case "updatedAt" -> fact.setUpdatedAt((LocalDateTime) value);
        case "repliedAt" -> fact.setRepliedAt((LocalDateTime) value);
        case "remoteCallPhase" -> fact.setRemoteCallPhase((String) value);
        default -> {
        }
      }
    });
  }

  static final class InMemoryActionFactService extends ExternalActionFactService {
    private final TypedDataModel<String, ExternalActionFact> dataModel;

    InMemoryActionFactService(TypedDataModel<String, ExternalActionFact> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalActionFact> getDataModel() {
      return dataModel;
    }
  }
}
