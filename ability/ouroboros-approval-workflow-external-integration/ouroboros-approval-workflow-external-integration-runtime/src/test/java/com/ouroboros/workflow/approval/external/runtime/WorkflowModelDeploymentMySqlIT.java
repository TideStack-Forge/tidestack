package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowDeploymentResult;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.service.impl.WorkflowModelServiceImpl;

import io.vavr.control.Try;

/**
 * MySQL gate for the WorkflowModel deployment result and persistence transaction boundary.
 *
 * <p>The deployment port is deterministic, while the model table, service path and Spring
 * transaction manager are real. This isolates the common atomicity contract from a live engine.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowModelDeploymentMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private SqlDataStation station;
  private String tableName;

  @BeforeAll
  void migrateWorkflowModel() {
    dataSource = new DriverManagerDataSource(
        requireProperty(URL_PROPERTY),
        requireProperty(USER_PROPERTY),
        requireEnvironment(PASSWORD_ENVIRONMENT));

    tableName = "t_awf_it_workflow_model_" + UUID.randomUUID().toString().replace("-", "");
    Map<String, DataModelMeta> metadata = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.manage.entity");
    DataModelMeta source = metadata.get("WorkflowModel");
    assertThat(source).as("WorkflowModel metadata").isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName("WorkflowModelDeploymentMySqlIT");
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);

    station = new SqlDataStation(
        "mysql-workflow-model-deployment-integration",
        dataSource,
        List.of(copy),
        MigrationStrategy.AUTO,
        "MySQL");
    var migrated = station.migrate();
    assertThat(migrated.isRight()).isTrue();
    assertThat(migrated.get()).isTrue();
  }

  @AfterAll
  void dropTestTable() throws SQLException {
    if (dataSource == null || tableName == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS `" + tableName + "`");
    }
  }

  @Test
  void externalDeploymentResultAndRuntimeIdentityAreCommittedTogether() {
    String id = "workflow-external-" + UUID.randomUUID();
    FlowManageService flowManageService = mock(FlowManageService.class);
    AtomicBoolean deploymentObservedTransaction = new AtomicBoolean();
    when(flowManageService.deployForRuntime(anyString(), anyString())).thenAnswer(invocation -> {
      deploymentObservedTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
      return Try.success(new FlowDeploymentResult(
          "EXTERNAL", "FULL_PROCESS", "EXTERNAL_ENGINE", "test-adapter",
          "mapping-1", 3, null, "tenant:definition-1"));
    });

    try (AnnotationConfigApplicationContext context = context(flowManageService, false)) {
      TransactionalWorkflowModelService service = context.getBean(TransactionalWorkflowModelService.class);
      service.add(draft(id));

      service.deploy(id);

      WorkflowModel persisted = service.getByPrimaryKey(id);
      assertThat(deploymentObservedTransaction).isTrue();
      assertThat(persisted.getStatus()).isEqualTo(WorkflowModel.STATUS_DEPLOYED);
      assertThat(persisted.getProcessDefinitionId()).isNull();
      assertThat(persisted.getExecutionMode()).isEqualTo("EXTERNAL");
      assertThat(persisted.getIntegrationMode()).isEqualTo("FULL_PROCESS");
      assertThat(persisted.getEngineAdapterId()).isEqualTo("test-adapter");
      assertThat(persisted.getEngineMappingId()).isEqualTo("mapping-1");
      assertThat(persisted.getEngineMappingVersion()).isEqualTo(3);
      assertThat(persisted.getExternalDefinitionRef()).isEqualTo("tenant:definition-1");
    }
  }

  @Test
  void modelUpdateFailureRollsBackDeploymentIdentityAndStatus() {
    String id = "workflow-rollback-" + UUID.randomUUID();
    FlowManageService flowManageService = mock(FlowManageService.class);
    when(flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.success(FlowDeploymentResult.builtIn("process-rollback")));

    try (AnnotationConfigApplicationContext context = context(flowManageService, true)) {
      TransactionalWorkflowModelService service = context.getBean(TransactionalWorkflowModelService.class);
      service.add(draft(id));

      assertThatThrownBy(() -> service.deploy(id))
          .isInstanceOf(BusinessException.class)
          .hasMessage("controlled model update failure");

      WorkflowModel persisted = service.getByPrimaryKey(id);
      assertThat(persisted.getStatus()).isEqualTo(WorkflowModel.STATUS_DRAFT);
      assertThat(persisted.getProcessDefinitionId()).isEmpty();
      assertThat(persisted.getExecutionMode()).isEmpty();
      assertThat(persisted.getExternalDefinitionRef()).isEmpty();
    }
  }

  private AnnotationConfigApplicationContext context(
      FlowManageService flowManageService,
      boolean failAfterPartialUpdate) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(TransactionConfiguration.class);
    context.registerBean(PlatformTransactionManager.class,
        () -> new DataSourceTransactionManager(dataSource));
    context.registerBean(FlowManageService.class, () -> flowManageService);
    TypedDataModel<String, WorkflowModel> model = new BaseTypedDataModel<>(station
        .getDataModel("WorkflowModelDeploymentMySqlIT").orElseThrow().withoutPlugins(), WorkflowModel.class);
    context.registerBean(TransactionalWorkflowModelService.class,
        () -> new TransactionalWorkflowModelService(model, failAfterPartialUpdate));
    context.refresh();
    return context;
  }

  private WorkflowModel draft(String id) {
    WorkflowModel model = new WorkflowModel();
    model.setId(id);
    model.setName("MySQL deployment test");
    model.setStatus(WorkflowModel.STATUS_DRAFT);
    model.setJson("""
        {
          "formatVersion": 1,
          "name": "workflow-test",
          "title": "Workflow test",
          "description": "transaction gate",
          "version": 1,
          "enabled": true,
          "form": {"formType": "default", "formId": "form-1"},
          "nodes": [
            {"type": "发起节点", "name": "发起节点", "id": "start", "permission": []},
            {"type": "结束节点", "name": "结束", "id": "end"}
          ],
          "edges": [{"id": "edge-1", "type": "SEQUENCE", "sourceId": "start", "targetId": "end"}]
        }
        """);
    model.setCreator("mysql-it");
    model.setSaveAt(LocalDateTime.now());
    model.setProcessDefinitionId("");
    model.setDescription("transaction gate");
    model.setIcon("");
    model.setCategory("");
    model.setAdopted(false);
    model.setSource(WorkflowModel.SOURCE_INSTANCE);
    model.setExecutionMode("");
    model.setIntegrationMode("");
    model.setEngineAdapterId("");
    model.setEngineMappingId("");
    model.setEngineMappingVersion(0);
    model.setExternalDefinitionRef("");
    return model;
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

  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfiguration {
  }

  static class TransactionalWorkflowModelService extends WorkflowModelServiceImpl {
    private final TypedDataModel<String, WorkflowModel> dataModel;
    private final boolean failAfterPartialUpdate;

    TransactionalWorkflowModelService(
        TypedDataModel<String, WorkflowModel> dataModel,
        boolean failAfterPartialUpdate) {
      this.dataModel = dataModel;
      this.failAfterPartialUpdate = failAfterPartialUpdate;
    }

    @Override
    protected TypedDataModel<String, WorkflowModel> getDataModel() {
      return dataModel;
    }

    @Override
    protected Boolean partialUpdateRuntimeModel(String id, Map<String, Object> map)
        throws com.ouroboros.exception.OuroborosException {
      Boolean updated = super.partialUpdateRuntimeModel(id, map);
      if (failAfterPartialUpdate) {
        throw new BusinessException("controlled model update failure");
      }
      return updated;
    }
  }
}
