package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
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

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;

import io.vavr.control.Try;

/**
 * MySQL integration gate for the runtime fact schema.
 *
 * <p>This class is run only by the mysql-integration Failsafe profile. The
 * connection must point to a dedicated test schema supplied by the caller.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalIntegrationMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private String receiptTableName;
  private String instanceBindingTableName;
  private String taskBindingTableName;
  private String resultBindingTableName;
  private SqlDataStation station;

  private record InsertOutcome(boolean inserted, String sqlState, int errorCode, String message) {}

  @FunctionalInterface
  private interface SqlInsert {
    void execute(String id) throws SQLException;
  }

  @BeforeAll
  void migrateRuntimeModels() {
    String url = requireProperty(URL_PROPERTY);
    String user = requireProperty(USER_PROPERTY);
    String password = requireEnvironment(PASSWORD_ENVIRONMENT);
    dataSource = new DriverManagerDataSource(url, user, password);

    String tableSuffix = UUID.randomUUID().toString().replace("-", "");
    receiptTableName = "t_awf_it_receipt_" + tableSuffix;
    instanceBindingTableName = "t_awf_it_instance_" + tableSuffix;
    taskBindingTableName = "t_awf_it_task_" + tableSuffix;
    resultBindingTableName = "t_awf_it_result_" + tableSuffix;
    var runtimeModels = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime");
    List<DataModelMeta> models = List.of(
        copyForIntegration(runtimeModels, "ExternalCallbackReceipt", receiptTableName),
        copyForIntegration(runtimeModels, "ExternalInstanceBinding", instanceBindingTableName),
        copyForIntegration(runtimeModels, "ExternalTaskBinding", taskBindingTableName),
        copyForIntegration(runtimeModels, "ExternalResultBinding", resultBindingTableName));

    station = new SqlDataStation(
        "mysql-integration",
        dataSource,
        models,
        MigrationStrategy.AUTO,
        "MySQL");
    var migrated = station.migrate();
    assertTrue(migrated.isRight(), () -> migrated.isLeft() ? migrated.getLeft().getMessage() : "");
    assertTrue(migrated.get(), "runtime model migration must execute");
  }

  @AfterAll
  void dropTestTable() throws SQLException {
    if (dataSource == null || receiptTableName == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS `" + receiptTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + instanceBindingTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + taskBindingTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + resultBindingTableName + "`");
    }
  }

  @Test
  void runtimeReceiptMigrationCreatesExpectedUniqueIndex() throws SQLException {
    assertUniqueIndex(receiptTableName, "uk_awf_ext_callback_receipt_nonce");
  }

  @Test
  void runtimeReceiptUniqueIndexRejectsConcurrentDuplicateNonce() throws Exception {
    assertConcurrentDuplicateIsRejected("receipt nonce", this::insertReceipt);
  }

  @Test
  void runtimeBindingMigrationsCreateExpectedUniqueIndexes() throws SQLException {
    assertUniqueIndex(instanceBindingTableName, "uk_awf_ext_instance_external");
    assertUniqueIndex(taskBindingTableName, "uk_awf_ext_task_external");
    assertUniqueIndex(resultBindingTableName, "uk_awf_ext_result_external_instance");
    assertUniqueIndex(resultBindingTableName, "uk_awf_ext_result_idempotency");
    assertUniqueIndex(resultBindingTableName, "uk_awf_ext_result_running_record");
  }

  @Test
  void runtimeBindingUniqueIndexesRejectConcurrentDuplicateExternalKeys() throws Exception {
    assertConcurrentDuplicateIsRejected("instance external route", this::insertInstanceBinding);
    assertConcurrentDuplicateIsRejected("task external route", this::insertTaskBinding);
    assertConcurrentDuplicateIsRejected("result external route", this::insertResultBinding);
  }

  @Test
  void resultBindingServiceConvergesOrRejectsAfterRealDatabaseDuplicateKey() throws Exception {
    String sameExternalInstanceId = "external-same-" + UUID.randomUUID();
    ExternalResultBinding firstSameFact = resultBinding(
        "tracking-same", "record-same", sameExternalInstanceId, "idempotency-same");
    ExternalResultBinding secondSameFact = resultBinding(
        "tracking-same", "record-same", sameExternalInstanceId, "idempotency-same");

    List<Try<ExternalResultBinding>> sameFactOutcomes = concurrentlyCreate(
        firstSameFact, secondSameFact);

    assertThat(sameFactOutcomes).allMatch(Try::isSuccess);
    assertThat(sameFactOutcomes).extracting(outcome -> outcome.get().getTrackingId())
        .containsOnly("tracking-same");

    String externalInstanceId = "external-conflict-" + UUID.randomUUID();
    assertConflictPreservesExistingBinding(
        resultBinding("tracking-external-first", "record-external-first", externalInstanceId, "idempotency-external-first"),
        resultBinding("tracking-external-second", "record-external-second", externalInstanceId, "idempotency-external-second"));
    String idempotencyKey = "idempotency-conflict-" + UUID.randomUUID();
    assertConflictPreservesExistingBinding(
        resultBinding("tracking-idempotency-first", "record-idempotency-first", "external-idempotency-first-" + UUID.randomUUID(),
            idempotencyKey),
        resultBinding("tracking-idempotency-second", "record-idempotency-second", "external-idempotency-second-" + UUID.randomUUID(),
            idempotencyKey));
    String businessRecordId = "record-conflict-" + UUID.randomUUID();
    assertConflictPreservesExistingBinding(
        resultBinding("tracking-record-first", businessRecordId, "external-record-first-" + UUID.randomUUID(),
            "idempotency-record-first"),
        resultBinding("tracking-record-second", businessRecordId, "external-record-second-" + UUID.randomUUID(),
            "idempotency-record-second"));

    String roundExternalInstanceId = "external-round-" + UUID.randomUUID();
    ExternalResultBinding firstRound = resultBinding(
        "tracking-round", "record-round", roundExternalInstanceId, "idempotency-round");
    ExternalResultBinding nextRound = resultBinding(
        "tracking-round", "record-round", roundExternalInstanceId, "idempotency-round");
    nextRound.setSubmissionRound(2);
    assertConflictPreservesExistingBinding(firstRound, nextRound);
  }

  @Test
  void instanceBindingServiceConvergesOrRejectsAfterRealDatabaseDuplicateKey() {
    TestExternalInstanceBindingService service = instanceBindingService();
    assertThat(service.create(instanceBinding(
        "instance-unrelated", "record-unrelated", "instance-unrelated-" + UUID.randomUUID())).isSuccess())
        .isTrue();

    String externalInstanceId = "instance-conflict-" + UUID.randomUUID();
    ExternalInstanceBinding existing = instanceBinding(
        "instance-existing", "record-existing", externalInstanceId);
    ExternalInstanceBinding sameFact = instanceBinding(
        "instance-existing", "record-existing", externalInstanceId);
    ExternalInstanceBinding conflicting = instanceBinding(
        "instance-conflicting", "record-conflicting", externalInstanceId);
    assertThat(service.create(existing).isSuccess()).isTrue();

    Try<ExternalInstanceBinding> idempotent = service.create(sameFact);
    assertThat(idempotent.isSuccess()).isTrue();
    assertThat(idempotent.get().getLocalInstanceId()).isEqualTo(existing.getLocalInstanceId());

    Try<ExternalInstanceBinding> conflict = service.create(conflicting);
    assertThat(conflict.isFailure()).isTrue();
    assertThat(conflict.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    ExternalInstanceBinding persisted = service.findByExternalInstance(
        "adapter", "connection", externalInstanceId).orElseThrow();
    assertThat(persisted.getLocalInstanceId()).isEqualTo(existing.getLocalInstanceId());
    assertThat(persisted.getBusinessRecordId()).isEqualTo(existing.getBusinessRecordId());

    String roundExternalInstanceId = "instance-round-" + UUID.randomUUID();
    ExternalInstanceBinding firstRound = instanceBinding(
        "instance-round", "record-round", roundExternalInstanceId);
    ExternalInstanceBinding nextRound = instanceBinding(
        "instance-round", "record-round", roundExternalInstanceId);
    nextRound.setSubmissionRound(2);
    assertThat(service.create(firstRound).isSuccess()).isTrue();
    Try<ExternalInstanceBinding> roundConflict = service.create(nextRound);
    assertThat(roundConflict.isFailure()).isTrue();
    assertThat(roundConflict.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    ExternalInstanceBinding persistedRound = service.findByExternalInstance(
        "adapter", "connection", roundExternalInstanceId).orElseThrow();
    assertThat(persistedRound.getSubmissionRound()).isEqualTo(1);
  }

  @Test
  void taskBindingServiceConvergesOrRejectsAfterRealDatabaseDuplicateKey() {
    TestExternalTaskBindingService service = taskBindingService();
    assertThat(service.create(taskBinding(
        "task-unrelated", "instance-unrelated", "task-unrelated-" + UUID.randomUUID())).isSuccess())
        .isTrue();

    String externalTaskId = "task-conflict-" + UUID.randomUUID();
    ExternalTaskBinding existing = taskBinding("task-existing", "instance-existing", externalTaskId);
    ExternalTaskBinding sameFact = taskBinding("task-existing", "instance-existing", externalTaskId);
    ExternalTaskBinding conflicting = taskBinding("task-conflicting", "instance-conflicting", externalTaskId);
    assertThat(service.create(existing).isSuccess()).isTrue();

    Try<ExternalTaskBinding> idempotent = service.create(sameFact);
    assertThat(idempotent.isSuccess()).isTrue();
    assertThat(idempotent.get().getLocalTaskId()).isEqualTo(existing.getLocalTaskId());

    Try<ExternalTaskBinding> conflict = service.create(conflicting);
    assertThat(conflict.isFailure()).isTrue();
    assertThat(conflict.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    ExternalTaskBinding persisted = service.findByExternalTask(
        "adapter", "connection", externalTaskId).orElseThrow();
    assertThat(persisted.getLocalTaskId()).isEqualTo(existing.getLocalTaskId());
    assertThat(persisted.getLocalInstanceId()).isEqualTo(existing.getLocalInstanceId());
  }

  private void assertConflictPreservesExistingBinding(
      ExternalResultBinding existing,
      ExternalResultBinding conflicting) {
    TestExternalResultBindingService service = resultBindingService();
    assertThat(service.create(existing).isSuccess()).isTrue();

    Try<ExternalResultBinding> conflict = service.create(conflicting);

    assertThat(conflict.isFailure()).isTrue();
    assertThat(conflict.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    ExternalResultBinding persisted = service.findByExternalInstance(
        "adapter", "connection", existing.getExternalInstanceId()).orElseThrow();
    assertThat(persisted.getTrackingId()).isEqualTo(existing.getTrackingId());
    assertThat(persisted.getBusinessRecordId()).isEqualTo(existing.getBusinessRecordId());
    assertThat(persisted.getIdempotencyKey()).isEqualTo(existing.getIdempotencyKey());
  }

  private List<Try<ExternalResultBinding>> concurrentlyCreate(
      ExternalResultBinding first, ExternalResultBinding second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var firstFuture = executor.submit(() -> createAfterStart(first, ready, start));
      var secondFuture = executor.submit(() -> createAfterStart(second, ready, start));
      assertTrue(ready.await(10, TimeUnit.SECONDS), "binding workers did not become ready");
      start.countDown();
      return List.of(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private Try<ExternalResultBinding> createAfterStart(
      ExternalResultBinding binding, CountDownLatch ready, CountDownLatch start) {
    try {
      ready.countDown();
      start.await(10, TimeUnit.SECONDS);
      return resultBindingService().create(binding);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("binding create worker interrupted", ex);
    }
  }

  private TestExternalResultBindingService resultBindingService() {
    // Bypass only the advisory duplicate checker so MySQL is the source of the duplicate-key failure.
    var model = station.getDataModel("ExternalResultBindingMySqlIT").orElseThrow().withoutPlugins();
    return new TestExternalResultBindingService(new BaseTypedDataModel<>(model, ExternalResultBinding.class));
  }

  private TestExternalInstanceBindingService instanceBindingService() {
    var model = station.getDataModel("ExternalInstanceBindingMySqlIT").orElseThrow().withoutPlugins();
    return new TestExternalInstanceBindingService(new BaseTypedDataModel<>(model, ExternalInstanceBinding.class));
  }

  private TestExternalTaskBindingService taskBindingService() {
    var model = station.getDataModel("ExternalTaskBindingMySqlIT").orElseThrow().withoutPlugins();
    return new TestExternalTaskBindingService(new BaseTypedDataModel<>(model, ExternalTaskBinding.class));
  }

  private ExternalInstanceBinding instanceBinding(
      String localInstanceId, String businessRecordId, String externalInstanceId) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localInstanceId);
    binding.setFlowKey("flow");
    binding.setScope("scope");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setApprovalCaseId("case");
    binding.setSubmissionRound(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setExternalInstanceId(externalInstanceId);
    binding.setExternalDefinitionId("definition");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    binding.setOutcome("");
    binding.setExternalRevision("revision");
    binding.setExternalEventTime(LocalDateTime.now());
    binding.setLastExternalEventId("event-" + localInstanceId);
    binding.setExternalUrl("https://example.invalid/instance/" + localInstanceId);
    binding.setBusinessRecordType("record");
    binding.setBusinessRecordId(businessRecordId);
    binding.setCallbackFlow("callback");
    return binding;
  }

  private ExternalTaskBinding taskBinding(
      String localTaskId, String localInstanceId, String externalTaskId) {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId(localTaskId);
    binding.setLocalInstanceId(localInstanceId);
    binding.setScope("scope");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setExternalTaskId(externalTaskId);
    binding.setNodeId("node");
    binding.setAssignee("assignee");
    binding.setStatus(ExternalTaskBinding.STATUS_CREATED);
    binding.setExternalRevision("revision");
    binding.setExternalEventTime(LocalDateTime.now());
    binding.setLastExternalEventId("event-" + localTaskId);
    binding.setExternalUrl("https://example.invalid/task/" + localTaskId);
    return binding;
  }

  private ExternalResultBinding resultBinding(
      String trackingId, String businessRecordId, String externalInstanceId, String idempotencyKey) {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(trackingId);
    binding.setScope("scope");
    binding.setFlowKey("flow");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setApprovalCaseId("case");
    binding.setSubmissionRound(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setBusinessRecordType("record");
    binding.setBusinessRecordId(businessRecordId);
    binding.setExternalInstanceId(externalInstanceId);
    binding.setIdempotencyKey(idempotencyKey);
    binding.setExternalDefinitionId("definition");
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    binding.setOutcome("");
    binding.setExternalRevision("revision");
    binding.setExternalEventTime(LocalDateTime.now());
    binding.setLastExternalEventId("event-" + trackingId);
    binding.setFinalResultAt(LocalDateTime.now());
    binding.setExternalUrl("https://example.invalid/result/" + trackingId);
    return binding;
  }

  private DataModelMeta copyForIntegration(
      java.util.Map<String, DataModelMeta> runtimeModels, String modelName, String tableName) {
    DataModelMeta source = runtimeModels.get(modelName);
    assertThat(source).as("runtime metadata for %s", modelName).isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName(modelName + "MySqlIT");
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);
    return copy;
  }

  private void assertUniqueIndex(String tableName, String indexName) throws SQLException {
    try (var connection = dataSource.getConnection();
         var statement = connection.prepareStatement(
             "SELECT COUNT(*) FROM information_schema.statistics "
                 + "WHERE table_schema = DATABASE() AND table_name = ? "
                 + "AND index_name = ? AND non_unique = 0")) {
      statement.setString(1, tableName);
      statement.setString(2, indexName);
      try (var result = statement.executeQuery()) {
        assertTrue(result.next());
        assertThat(result.getInt(1)).as("unique index %s", indexName).isGreaterThan(0);
      }
    }
  }

  private void assertConcurrentDuplicateIsRejected(String factName, SqlInsert insert) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> executeInsert(insert, "fact-a", ready, start));
      var second = executor.submit(() -> executeInsert(insert, "fact-b", ready, start));
      assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not become ready");
      start.countDown();
      List<InsertOutcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(outcomes.stream().filter(InsertOutcome::inserted).count())
          .as("%s insert outcomes: %s", factName, outcomes)
          .isEqualTo(1);
      InsertOutcome duplicate = outcomes.stream()
          .filter(outcome -> !outcome.inserted())
          .findFirst()
          .orElseThrow();
      assertThat(duplicate.sqlState()).isEqualTo("23000");
      assertThat(duplicate.errorCode()).isEqualTo(1062);
    } finally {
      executor.shutdownNow();
    }
  }

  private InsertOutcome executeInsert(
      SqlInsert insert, String id, CountDownLatch ready, CountDownLatch start) {
    try {
      ready.countDown();
      start.await(10, TimeUnit.SECONDS);
      insert.execute(id);
      return new InsertOutcome(true, null, 0, null);
    } catch (SQLException ex) {
      return new InsertOutcome(false, ex.getSQLState(), ex.getErrorCode(), ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("receipt insert worker interrupted", ex);
    }
  }

  private void insertReceipt(String id) throws SQLException {
    String sql = "INSERT INTO `" + receiptTableName
        + "` (id, adapter_id, connection_id, nonce, nonce_route_key, payload_digest, event_count, status, "
        + "claim_token, claim_until, last_error, expires_at, created_at, updated_at, completed_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, "adapter");
      statement.setString(3, "connection");
      statement.setString(4, "nonce");
      statement.setString(5, "adapter:connection:nonce");
      statement.setString(6, "digest");
      statement.setInt(7, 1);
      statement.setString(8, "RECEIVING");
      statement.setString(9, "claim-" + id);
      statement.setString(10, "");
      statement.executeUpdate();
    }
  }

  private void insertInstanceBinding(String id) throws SQLException {
    String sql = "INSERT INTO `" + instanceBindingTableName
        + "` (local_instance_id, flow_key, scope, mapping_id, mapping_version, approval_case_id, submission_round, "
        + "adapter_id, connection_id, "
        + "external_instance_id, external_instance_route_key, external_definition_id, status, outcome, "
        + "external_revision, external_event_time, last_external_event_id, external_url, business_record_type, "
        + "business_record_id, callback_flow, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, "
        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, "flow");
      statement.setString(3, "scope");
      statement.setString(4, "mapping");
      statement.setInt(5, 1);
      statement.setString(6, "case");
      statement.setInt(7, 1);
      statement.setString(8, "adapter");
      statement.setString(9, "connection");
      statement.setString(10, "external-instance");
      statement.setString(11, "adapter:connection:external-instance");
      statement.setString(12, "definition");
      statement.setString(13, "RUNNING");
      statement.setString(14, "");
      statement.setString(15, "revision");
      statement.setString(16, "event-" + id);
      statement.setString(17, "https://example.invalid/instance");
      statement.setString(18, "record");
      statement.setString(19, id);
      statement.setString(20, "callback");
      statement.executeUpdate();
    }
  }

  private void insertTaskBinding(String id) throws SQLException {
    String sql = "INSERT INTO `" + taskBindingTableName
        + "` (local_task_id, local_instance_id, scope, mapping_id, mapping_version, adapter_id, connection_id, "
        + "external_task_id, external_task_route_key, node_id, assignee, status, external_revision, "
        + "external_event_time, last_external_event_id, external_url, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP)";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, "instance-" + id);
      statement.setString(3, "scope");
      statement.setString(4, "mapping");
      statement.setInt(5, 1);
      statement.setString(6, "adapter");
      statement.setString(7, "connection");
      statement.setString(8, "external-task");
      statement.setString(9, "adapter:connection:external-task");
      statement.setString(10, "node");
      statement.setString(11, "assignee");
      statement.setString(12, "CREATED");
      statement.setString(13, "revision");
      statement.setString(14, "event-" + id);
      statement.setString(15, "https://example.invalid/task");
      statement.executeUpdate();
    }
  }

  private void insertResultBinding(String id) throws SQLException {
    String sql = "INSERT INTO `" + resultBindingTableName
        + "` (tracking_id, scope, flow_key, mapping_id, mapping_version, approval_case_id, submission_round, "
        + "adapter_id, connection_id, "
        + "business_record_type, business_record_id, external_instance_id, idempotency_key, "
        + "external_instance_route_key, idempotency_route_key, business_running_route_key, external_definition_id, "
        + "status, outcome, external_revision, external_event_time, last_external_event_id, final_result_at, "
        + "external_url, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, "
        + "CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, "scope");
      statement.setString(3, "flow");
      statement.setString(4, "mapping");
      statement.setInt(5, 1);
      statement.setString(6, "case");
      statement.setInt(7, 1);
      statement.setString(8, "adapter");
      statement.setString(9, "connection");
      statement.setString(10, "record");
      statement.setString(11, id);
      statement.setString(12, "external-result");
      statement.setString(13, "idempotency-" + id);
      statement.setString(14, "adapter:connection:external-result");
      statement.setString(15, "adapter:connection:idempotency-" + id);
      statement.setString(16, "scope:flow:record:" + id);
      statement.setString(17, "definition");
      statement.setString(18, "RUNNING");
      statement.setString(19, "");
      statement.setString(20, "revision");
      statement.setString(21, "event-" + id);
      statement.setString(22, "https://example.invalid/result");
      statement.executeUpdate();
    }
  }

  private String requireProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "mysql-integration requires -D" + name + " and a dedicated test schema");
    }
    return value;
  }

  private String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "mysql-integration requires environment variable " + name + " for its dedicated test schema");
    }
    return value;
  }

  private static final class TestExternalResultBindingService extends ExternalResultBindingServiceImpl {
    private final TypedDataModel<String, ExternalResultBinding> dataModel;

    private TestExternalResultBindingService(TypedDataModel<String, ExternalResultBinding> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalResultBinding> getDataModel() {
      return dataModel;
    }
  }

  private static final class TestExternalInstanceBindingService extends ExternalInstanceBindingService {
    private final TypedDataModel<String, ExternalInstanceBinding> dataModel;

    private TestExternalInstanceBindingService(TypedDataModel<String, ExternalInstanceBinding> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalInstanceBinding> getDataModel() {
      return dataModel;
    }
  }

  private static final class TestExternalTaskBindingService extends ExternalTaskBindingService {
    private final TypedDataModel<String, ExternalTaskBinding> dataModel;

    private TestExternalTaskBindingService(TypedDataModel<String, ExternalTaskBinding> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalTaskBinding> getDataModel() {
      return dataModel;
    }
  }
}
