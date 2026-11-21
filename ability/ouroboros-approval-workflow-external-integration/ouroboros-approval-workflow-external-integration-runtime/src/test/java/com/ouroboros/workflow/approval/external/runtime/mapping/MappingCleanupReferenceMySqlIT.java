package com.ouroboros.workflow.approval.external.runtime.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;

import io.vavr.control.Try;

/**
 * MySQL concurrency gate for mapping cleanup and late runtime references.
 *
 * <p>The test deliberately uses the production typed services and two independent transaction
 * templates. It does not rely on an application process or a live provider; the provider side is
 * represented by the binding transaction that races with cleanup.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MappingCleanupReferenceMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private SqlDataStation station;
  private String mappingTableName;
  private String instanceBindingTableName;
  private TypedDataModel<String, WorkflowEngineMapping> mappingModel;
  private TypedDataModel<String, ExternalInstanceBinding> instanceBindingModel;
  private PlatformTransactionManager transactionManager;

  @BeforeAll
  void migrateModels() {
    dataSource = new DriverManagerDataSource(
        requireProperty(URL_PROPERTY), requireProperty(USER_PROPERTY), requireEnvironment(PASSWORD_ENVIRONMENT));
    String suffix = UUID.randomUUID().toString().replace("-", "");
    mappingTableName = "t_awf_it_mapping_cleanup_" + suffix;
    instanceBindingTableName = "t_awf_it_instance_cleanup_" + suffix;

    Map<String, DataModelMeta> mappingMetadata = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime.mapping");
    Map<String, DataModelMeta> bindingMetadata = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime.binding");
    DataModelMeta mapping = copyForIntegration(
        mappingMetadata, "WorkflowEngineMapping", "MappingCleanupReferenceMySqlIT", mappingTableName);
    DataModelMeta instanceBinding = copyForIntegration(
        bindingMetadata, "ExternalInstanceBinding", "InstanceBindingCleanupReferenceMySqlIT", instanceBindingTableName);
    station = new SqlDataStation(
        "mapping-cleanup-reference-mysql-integration",
        dataSource,
        List.of(mapping, instanceBinding),
        MigrationStrategy.AUTO,
        "MySQL");
    var migrated = station.migrate();
    assertThat(migrated.isRight()).isTrue();
    assertThat(migrated.get()).isTrue();

    mappingModel = new BaseTypedDataModel<>(
        station.getDataModel("MappingCleanupReferenceMySqlIT").orElseThrow().withoutPlugins(),
        WorkflowEngineMapping.class);
    instanceBindingModel = new BaseTypedDataModel<>(
        station.getDataModel("InstanceBindingCleanupReferenceMySqlIT").orElseThrow().withoutPlugins(),
        ExternalInstanceBinding.class);
    transactionManager = new DataSourceTransactionManager(dataSource);
  }

  @AfterAll
  void dropTables() throws SQLException {
    if (dataSource == null || mappingTableName == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS `" + instanceBindingTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + mappingTableName + "`");
    }
  }

  @Test
  void bindingReferenceEpochCasParticipatesInOuterTransaction() {
    String mappingId = "mapping-epoch-transaction-" + UUID.randomUUID();
    insertExpiredMapping(mappingId);
    WorkflowEngineMappingService mappingService = new WorkflowEngineMappingServiceImpl() {
      @Override
      protected TypedDataModel<String, WorkflowEngineMapping> getDataModel() {
        return mappingModel;
      }
    };

    transactionTemplate(TransactionDefinition.ISOLATION_READ_COMMITTED).executeWithoutResult(status -> {
      assertThat(mappingService.acquireBindingReferenceLock(mappingId)).isTrue();
      status.setRollbackOnly();
    });

    assertThat(mappingService.findByIdForSystem(mappingId))
        .hasValueSatisfying(current -> assertThat(current.getBindingReferenceEpoch()).isEqualTo(0L));
  }

  @Test
  void committedBindingAfterCandidateEnumerationIsVisibleAndProtectsMapping() throws Exception {
    String mappingId = "mapping-binding-first-" + UUID.randomUUID();
    WorkflowEngineMapping mapping = insertExpiredMapping(mappingId);
    HookedMappingService mappingService = new HookedMappingService(mappingModel);
    TestExternalInstanceBindingService bindingService = bindingService(mappingService);
    MappingVersionCleanupService cleanupService = cleanupService(mappingService, bindingService);

    CountDownLatch candidateEnumerated = new CountDownLatch(1);
    CountDownLatch allowCleanup = new CountDownLatch(1);
    mappingService.pauseAfterFirstDraftEnumeration(candidateEnumerated, allowCleanup);
    TransactionTemplate cleanupTransaction = transactionTemplate(TransactionDefinition.ISOLATION_READ_COMMITTED);
    TransactionTemplate bindingTransaction = transactionTemplate(TransactionDefinition.ISOLATION_DEFAULT);
    ExternalInstanceBinding binding = instanceBinding(mappingId, "late-binding-" + UUID.randomUUID());

    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<MappingVersionCleanupService.CleanupReport> cleanup = workers.submit(
          () -> cleanupTransaction.execute(status -> cleanupService.cleanup(
              LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(30))));
      assertThat(candidateEnumerated.await(10, TimeUnit.SECONDS)).isTrue();

      Future<Try<ExternalInstanceBinding>> bindingCreate = workers.submit(
          () -> bindingTransaction.execute(status -> bindingService.create(binding)));
      Try<ExternalInstanceBinding> bindingResult = bindingCreate.get(10, TimeUnit.SECONDS);
      assertThat(bindingResult.isSuccess()).isTrue();
      allowCleanup.countDown();

      MappingVersionCleanupService.CleanupReport report = cleanup.get(10, TimeUnit.SECONDS);
      assertThat(report.deleted()).isZero();
      assertThat(report.skippedDueToBinding()).isEqualTo(1);
      assertThat(mappingService.findByIdForSystem(mappingId)).isPresent();
      assertThat(bindingService.findByLocalId(binding.getLocalInstanceId())).isPresent();
    } finally {
      allowCleanup.countDown();
      workers.shutdownNow();
    }
    assertThat(mapping.getId()).isEqualTo(mappingId);
  }

  @Test
  void cleanupAndLateBindingAreSerializationSafe() throws Exception {
    String mappingId = "mapping-cleanup-first-" + UUID.randomUUID();
    insertExpiredMapping(mappingId);
    LockGatedMappingService mappingService = new LockGatedMappingService(mappingModel, dataSource);
    TestExternalInstanceBindingService bindingService = bindingService(mappingService);
    MappingVersionCleanupService cleanupService = cleanupService(mappingService, bindingService);
    CountDownLatch mappingLocked = new CountDownLatch(1);
    CountDownLatch releaseCleanup = new CountDownLatch(1);
    mappingService.pauseAfterFirstLock(mappingLocked, releaseCleanup);

    TransactionTemplate cleanupTransaction = transactionTemplate(TransactionDefinition.ISOLATION_READ_COMMITTED);
    TransactionTemplate bindingTransaction = transactionTemplate(TransactionDefinition.ISOLATION_DEFAULT);
    ExternalInstanceBinding binding = instanceBinding(mappingId, "rejected-binding-" + UUID.randomUUID());
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<MappingVersionCleanupService.CleanupReport> cleanup = workers.submit(
          () -> cleanupTransaction.execute(status -> cleanupService.cleanup(
              LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(30))));
      assertThat(mappingLocked.await(10, TimeUnit.SECONDS)).isTrue();

      Future<Try<ExternalInstanceBinding>> bindingCreate = workers.submit(
          () -> bindingTransaction.execute(status -> bindingService.create(binding)));
      releaseCleanup.countDown();

      MappingVersionCleanupService.CleanupReport report = cleanup.get(10, TimeUnit.SECONDS);
      Try<ExternalInstanceBinding> bindingResult = bindingCreate.get(10, TimeUnit.SECONDS);
      if (bindingResult.isSuccess()) {
        // The reference transaction won the race. The epoch-conditional delete must then
        // affect zero rows, leaving both the mapping and the binding durable.
        assertThat(report.deleted()).isZero();
        assertThat(report.skippedDueToBinding()).isEqualTo(1);
        assertThat(mappingService.findByIdForSystem(mappingId)).isPresent();
        assertThat(bindingService.findByLocalId(binding.getLocalInstanceId())).isPresent();
      } else {
        // Cleanup won the race. The mapping is deleted before the late reference can acquire
        // its epoch, so the binding transaction must be rejected and rolled back.
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(bindingService.findByLocalId(binding.getLocalInstanceId())).isEmpty();
        assertThat(mappingService.findByIdForSystem(mappingId)).isEmpty();
      }
    } finally {
      releaseCleanup.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void candidateChangedAfterEnumerationIsNotDeleted() throws Exception {
    String mappingId = "mapping-candidate-changed-" + UUID.randomUUID();
    WorkflowEngineMapping mapping = insertExpiredMapping(mappingId);
    HookedMappingService mappingService = new HookedMappingService(mappingModel);
    MappingVersionCleanupService cleanupService = cleanupService(
        mappingService, mock(ExternalInstanceBindingService.class));
    CountDownLatch candidateEnumerated = new CountDownLatch(1);
    CountDownLatch allowCleanup = new CountDownLatch(1);
    mappingService.pauseAfterFirstDraftEnumeration(candidateEnumerated, allowCleanup);

    TransactionTemplate cleanupTransaction = transactionTemplate(TransactionDefinition.ISOLATION_READ_COMMITTED);
    ExecutorService workers = Executors.newSingleThreadExecutor();
    try {
      Future<MappingVersionCleanupService.CleanupReport> cleanup = workers.submit(
          () -> cleanupTransaction.execute(status -> cleanupService.cleanup(
              LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(30))));
      assertThat(candidateEnumerated.await(10, TimeUnit.SECONDS)).isTrue();

      LocalDateTime changedAt = LocalDateTime.now().withNano(0);
      var update = mappingModel.update(
          Map.of("id", Map.of("=", mappingId)),
          Map.of(
              "status", WorkflowEngineMapping.STATUS_INVALID,
              "activeRouteKey", ExternalRouteKeys.of("mapping-inactive", mappingId),
              "updatedAt", changedAt));
      assertThat(update.isSuccess()).isTrue();
      assertThat(update.get()).isEqualTo(1L);
      allowCleanup.countDown();

      MappingVersionCleanupService.CleanupReport report = cleanup.get(10, TimeUnit.SECONDS);
      assertThat(report.deleted()).isZero();
      assertThat(report.skippedDueToBinding()).isEqualTo(1);
      assertThat(mappingService.findByIdForSystem(mappingId))
          .hasValueSatisfying(current -> assertThat(current.getStatus())
              .isEqualTo(WorkflowEngineMapping.STATUS_INVALID));
    } finally {
      allowCleanup.countDown();
      workers.shutdownNow();
    }
    assertThat(mapping.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);
  }

  private MappingVersionCleanupService cleanupService(
      WorkflowEngineMappingService mappingService,
      ExternalInstanceBindingService instances) {
    ExternalResultBindingService results = mock(ExternalResultBindingService.class);
    ExternalTaskBindingService tasks = mock(ExternalTaskBindingService.class);
    if (mockingDetails(instances).isMock()) {
      when(instances.countByMappingId(anyString())).thenReturn(0L);
    }
    when(results.countByMappingId(anyString())).thenReturn(0L);
    when(tasks.countByMappingId(anyString())).thenReturn(0L);
    return new MappingVersionCleanupService(mappingService, results, instances, tasks);
  }

  private TestExternalInstanceBindingService bindingService(WorkflowEngineMappingService mappingService) {
    TestExternalInstanceBindingService service = new TestExternalInstanceBindingService(instanceBindingModel);
    ReflectionTestUtils.setField(service, "mappingService", mappingService);
    return service;
  }

  private TransactionTemplate transactionTemplate(int isolationLevel) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(isolationLevel);
    return template;
  }

  private WorkflowEngineMapping insertExpiredMapping(String mappingId) {
    LocalDateTime old = LocalDateTime.of(2020, 1, 1, 0, 0);
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(mappingId);
    mapping.setScope("scope");
    mapping.setFlowKey("flow-" + mappingId);
    mapping.setAdapterId("fake");
    mapping.setConnectionId("connection");
    mapping.setExternalDefinitionNamespace("");
    mapping.setExternalDefinitionId("definition");
    mapping.setExternalDefinitionVersion("v1");
    mapping.setExternalDefinitionSchemaHash("schema");
    mapping.setExternalDefinitionValidatedAt(old);
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setResultBindingJson("{}");
    mapping.setStartOptionsJson("{}");
    mapping.setStatus(WorkflowEngineMapping.STATUS_DRAFT);
    mapping.setActiveRouteKey(ExternalRouteKeys.of("mapping-inactive", mappingId));
    mapping.setVersion(1);
    mapping.setBindingReferenceEpoch(0L);
    mapping.setActionMappingsJson("");
    mapping.setResultMappingsJson("{}");
    mapping.setCallbackJson("{}");
    mapping.setValidationMessage("");
    mapping.setCreatedBy("mysql-it");
    mapping.setCreatedAt(old);
    mapping.setUpdatedAt(old);
    var inserted = mappingModel.insert(mapping);
    assertThat(inserted.isSuccess()).isTrue();
    return mapping;
  }

  private ExternalInstanceBinding instanceBinding(String mappingId, String localInstanceId) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localInstanceId);
    binding.setFlowKey("flow-" + mappingId);
    binding.setScope("scope");
    binding.setMappingId(mappingId);
    binding.setMappingVersion(1);
    binding.setApprovalCaseId("case-" + localInstanceId);
    binding.setSubmissionRound(1);
    binding.setAdapterId("fake");
    binding.setConnectionId("connection");
    binding.setExternalInstanceId("external-" + localInstanceId);
    binding.setExternalDefinitionId("definition");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    binding.setOutcome("");
    binding.setExternalRevision("revision");
    binding.setExternalEventTime(LocalDateTime.now().withNano(0));
    binding.setLastExternalEventId("event-" + localInstanceId);
    binding.setExternalUrl("https://example.invalid/" + localInstanceId);
    binding.setBusinessRecordType("record");
    binding.setBusinessRecordId("record-" + localInstanceId);
    binding.setCallbackFlow("callback");
    return binding;
  }

  private DataModelMeta copyForIntegration(
      Map<String, DataModelMeta> models, String sourceName, String testName, String tableName) {
    DataModelMeta source = models.get(sourceName);
    assertThat(source).as("metadata for %s", sourceName).isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName(testName);
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);
    return copy;
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

  static class TestExternalInstanceBindingService extends ExternalInstanceBindingService {
    private final TypedDataModel<String, ExternalInstanceBinding> dataModel;

    TestExternalInstanceBindingService(TypedDataModel<String, ExternalInstanceBinding> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalInstanceBinding> getDataModel() {
      return dataModel;
    }
  }

  static class HookedMappingService extends WorkflowEngineMappingServiceImpl {
    private final TypedDataModel<String, WorkflowEngineMapping> dataModel;
    private final AtomicBoolean enumerationHooked = new AtomicBoolean();
    private CountDownLatch enumerated;
    private CountDownLatch allowEnumeration;

    HookedMappingService(TypedDataModel<String, WorkflowEngineMapping> dataModel) {
      this.dataModel = dataModel;
    }

    void pauseAfterFirstDraftEnumeration(CountDownLatch enumerated, CountDownLatch allowEnumeration) {
      this.enumerated = enumerated;
      this.allowEnumeration = allowEnumeration;
    }

    @Override
    protected TypedDataModel<String, WorkflowEngineMapping> getDataModel() {
      return dataModel;
    }

    @Override
    public List<WorkflowEngineMapping> findByStatusForSystem(String status) {
      List<WorkflowEngineMapping> result = super.findByStatusForSystem(status);
      if (WorkflowEngineMapping.STATUS_DRAFT.equals(status)
          && enumerationHooked.compareAndSet(false, true)) {
        enumerated.countDown();
        await(allowEnumeration, "mapping cleanup enumeration release");
      }
      return result;
    }
  }

  static class LockGatedMappingService extends WorkflowEngineMappingServiceImpl {
    private final TypedDataModel<String, WorkflowEngineMapping> dataModel;
    private final DriverManagerDataSource dataSource;
    private final AtomicBoolean lockHooked = new AtomicBoolean();
    private CountDownLatch locked;
    private CountDownLatch release;

    LockGatedMappingService(TypedDataModel<String, WorkflowEngineMapping> dataModel,
        DriverManagerDataSource dataSource) {
      this.dataModel = dataModel;
      this.dataSource = dataSource;
    }

    void pauseAfterFirstLock(CountDownLatch locked, CountDownLatch release) {
      this.locked = locked;
      this.release = release;
    }

    @Override
    protected TypedDataModel<String, WorkflowEngineMapping> getDataModel() {
      return dataModel;
    }

    @Override
    public boolean acquireBindingReferenceLock(String mappingId) {
      boolean acquired = super.acquireBindingReferenceLock(mappingId);
      if (acquired && lockHooked.compareAndSet(false, true)) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        var connection = DataSourceUtils.getConnection(dataSource);
        assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)).isTrue();
        locked.countDown();
        await(release, "mapping cleanup lock release");
      }
      return acquired;
    }
  }

  private static void await(CountDownLatch latch, String description) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException(description + " timed out");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(description + " interrupted", ex);
    }
  }
}
