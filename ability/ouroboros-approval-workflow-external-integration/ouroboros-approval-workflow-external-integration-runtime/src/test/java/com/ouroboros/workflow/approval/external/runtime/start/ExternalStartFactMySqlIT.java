package com.ouroboros.workflow.approval.external.runtime.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;

/** MySQL integration gate for the durable external start state machine. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalStartFactMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private SqlDataStation station;
  private String tableName;

  @BeforeAll
  void migrateStartFactModel() {
    dataSource = new DriverManagerDataSource(
        requireProperty(URL_PROPERTY), requireProperty(USER_PROPERTY), requireEnvironment(PASSWORD_ENVIRONMENT));
    tableName = "t_awf_it_start_fact_" + UUID.randomUUID().toString().replace("-", "");
    Map<String, DataModelMeta> metadata = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime.start");
    DataModelMeta source = metadata.get("ExternalStartFact");
    assertThat(source).isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName("ExternalStartFactMySqlIT");
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);
    station = new SqlDataStation(
        "external-start-fact-mysql-integration", dataSource, List.of(copy), MigrationStrategy.AUTO, "MySQL");
    var migrated = station.migrate();
    assertTrue(migrated.isRight(), () -> migrated.isLeft() ? migrated.getLeft().getMessage() : "");
    assertTrue(migrated.get(), "start fact model migration must execute");
  }

  @AfterAll
  void dropStartFactTable() throws SQLException {
    if (dataSource == null || tableName == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS `" + tableName + "`");
    }
  }

  @Test
  void sagaIntentSurvivesBusinessRollbackWithoutWeakeningTheBusinessResourceGuard() {
    var service = service();
    var manager = new DataSourceTransactionManager(dataSource);
    var source = new TransactionTemplate(manager);
    var independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    String factId = source.execute(status -> {
      var connection = DataSourceUtils.getConnection(dataSource);
      DataModelWriteGuard guard = new DataModelWriteGuard() {
        @Override
        public void beforeWrite(DataModel model) {
          assertThat(model.getAdapter().requireTransactionResource().get()).isSameAs(connection);
        }
        @Override
        public void markRollbackOnly() { status.setRollbackOnly(); }
      };
      return DataModelExecutionContext.withWriteGuard(guard, () -> {
        var reservation = independent.execute(inner -> service.reserve(
            "default", mapping("mapping-guard-" + UUID.randomUUID()), "supplier", "record-" + UUID.randomUUID(),
            "idem-" + UUID.randomUUID(), "fingerprint-guard", ExternalStartFact.MODE_RESULT_ONLY,
            "tracking-" + UUID.randomUUID(), "actor", null, "{}").get());
        assertThat(reservation).isNotNull();
        var fact = reservation.fact();
        Boolean marked = independent.execute(inner -> service.markInFlight(fact.getId(), fact.getClaimToken()));
        assertThat(marked).isTrue();
        assertThat(DataModelExecutionContext.getWriteGuard()).isSameAs(guard);
        DataModelExecutionContext.beforeWrite(station.getDataModel("ExternalStartFactMySqlIT").get());
        status.setRollbackOnly();
        DataSourceUtils.releaseConnection(connection, dataSource);
        return fact.getId();
      });
    });
    assertThat(service.findById(factId)).get().extracting(ExternalStartFact::getRemoteCallPhase)
        .isEqualTo(ExternalStartFact.PHASE_IN_FLIGHT);
    assertThat(DataModelExecutionContext.getWriteGuard()).isNull();
  }

  @Test
  void concurrentReserveOfOneBusinessAndIdempotencyIdentityProducesOneFact() throws Exception {
    ExternalStartFactService service = service();
    WorkflowEngineMapping mapping = mapping("mapping-concurrent-" + UUID.randomUUID());
    String recordId = "record-" + UUID.randomUUID();
    String idempotencyKey = "idem-" + UUID.randomUUID();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      var first = workers.submit(() -> reserveWhenReleased(
          service, mapping, recordId, idempotencyKey, ready, start));
      var second = workers.submit(() -> reserveWhenReleased(
          service, mapping, recordId, idempotencyKey, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      ExternalStartFactService.Reservation firstReservation = first.get(10, TimeUnit.SECONDS);
      ExternalStartFactService.Reservation secondReservation = second.get(10, TimeUnit.SECONDS);

      assertThat(firstReservation.fact().getId()).isEqualTo(secondReservation.fact().getId());
      assertThat(List.of(firstReservation.claimed(), secondReservation.claimed()))
          .containsExactlyInAnyOrder(true, false);
      assertThat(service.findById(firstReservation.fact().getId())).isPresent();
      assertThat(service.findByBusinessStartRouteKey(firstReservation.fact().getBusinessStartRouteKey()))
          .get()
          .extracting(ExternalStartFact::getId)
          .isEqualTo(firstReservation.fact().getId());
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void recoveryFencesOldWorkerAndAdoptsLateResponseOnTheSameFact() {
    ExternalStartFactService service = service();
    WorkflowEngineMapping mapping = mapping("mapping-recovery-" + UUID.randomUUID());
    String recordId = "record-" + UUID.randomUUID();
    ExternalStartFactService.Reservation initial = service.reserve(
        "default", mapping, "supplier", recordId, "idem-" + UUID.randomUUID(),
        "fingerprint-recovery-" + UUID.randomUUID(), ExternalStartFact.MODE_RESULT_ONLY,
        "tracking-" + UUID.randomUUID(), "actor", null, "{}").get();
    ExternalStartFact fact = initial.fact();
    expire(fact.getId());

    ExternalStartFactService.RecoveryClaim recovery = service.claimForRecovery(fact.getId(), 300).orElseThrow();
    assertThat(recovery.claimToken()).isNotEqualTo(initial.fact().getClaimToken());
    assertThat(service.markReplied(
        fact.getId(), initial.fact().getClaimToken(), "external-old", "https://external.invalid/old"))
        .isFalse();

    ExternalStartFactService.RecoveryClaim adopted = service.adoptLateResponse(
        fact.getId(), fact.getRequestFingerprint(), "external-late", "https://external.invalid/late", 300)
        .orElseThrow();
    assertThat(adopted.claimToken()).isNotEqualTo(recovery.claimToken());
    assertThat(service.markStarted(fact.getId(), adopted.claimToken(), fact.getTrackingId())).isTrue();

    ExternalStartFact persisted = service.findById(fact.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(ExternalStartFact.STATUS_STARTED);
    assertThat(persisted.getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_REPLIED);
    assertThat(persisted.getExternalInstanceId()).isEqualTo("external-late");
    assertThat(persisted.getClaimToken()).isNull();
  }

  @Test
  void unknownFactCanBeClaimedForInspectionButHasNoImplicitRemoteRetry() {
    ExternalStartFactService service = service();
    WorkflowEngineMapping mapping = mapping("mapping-unknown-" + UUID.randomUUID());
    ExternalStartFactService.Reservation reservation = service.reserve(
        "default", mapping, "supplier", "record-" + UUID.randomUUID(), "idem-" + UUID.randomUUID(),
        "fingerprint-unknown-" + UUID.randomUUID(), ExternalStartFact.MODE_RESULT_ONLY,
        "tracking-" + UUID.randomUUID(), "actor", null, "{}").get();
    assertThat(service.markUnknown(
        reservation.fact().getId(), reservation.fact().getClaimToken(), null, null,
        "provider recovery capability unavailable")).isTrue();

    ExternalStartFactService.RecoveryClaim claim = service.claimForRecovery(
        reservation.fact().getId(), 300).orElseThrow();
    assertThat(claim.fact().getStatus()).isEqualTo(ExternalStartFact.STATUS_STARTING);
    assertThat(claim.fact().getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_IN_FLIGHT);
    assertThat(claim.fact().getExternalInstanceId()).isNull();
    // The fact is only claimed for a provider-specific recovery decision; this repository method never starts remotely.
    assertThat(service.findById(reservation.fact().getId()).orElseThrow().getStatus())
        .isEqualTo(ExternalStartFact.STATUS_STARTING);
  }

  private ExternalStartFactService.Reservation reserveWhenReleased(
      ExternalStartFactService service,
      WorkflowEngineMapping mapping,
      String recordId,
      String idempotencyKey,
      CountDownLatch ready,
      CountDownLatch start) {
    ready.countDown();
    try {
      assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
      return service.reserve(
          "default", mapping, "supplier", recordId, idempotencyKey,
          "fingerprint-concurrent", ExternalStartFact.MODE_RESULT_ONLY,
          "tracking-concurrent", "actor", null, "{}").get();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("start fact reserve worker interrupted", ex);
    }
  }

  private ExternalStartFactService service() {
    TypedDataModel<String, ExternalStartFact> model = new BaseTypedDataModel<>(
        station.getDataModel("ExternalStartFactMySqlIT").orElseThrow().withoutPlugins(), ExternalStartFact.class);
    return new TestExternalStartFactService(model);
  }

  private void expire(String factId) {
    TypedDataModel<String, ExternalStartFact> model = new BaseTypedDataModel<>(
        station.getDataModel("ExternalStartFactMySqlIT").orElseThrow().withoutPlugins(), ExternalStartFact.class);
    var result = model.update(Map.of("id", Map.of("=", factId)),
        Map.of("claimUntil", LocalDateTime.now().minusSeconds(1)));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo(1L);
  }

  private WorkflowEngineMapping mapping(String id) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setVersion(1);
    mapping.setFlowKey("flow");
    mapping.setAdapterId("fake");
    mapping.setConnectionId("connection");
    mapping.setExternalDefinitionId("definition");
    return mapping;
  }

  private String requireProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("mysql-integration requires -D" + name);
    }
    return value;
  }

  private String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("mysql-integration requires environment variable " + name);
    }
    return value;
  }

  static final class TestExternalStartFactService extends ExternalStartFactService {
    private final TypedDataModel<String, ExternalStartFact> dataModel;

    TestExternalStartFactService(TypedDataModel<String, ExternalStartFact> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalStartFact> getDataModel() {
      return dataModel;
    }
  }
}
