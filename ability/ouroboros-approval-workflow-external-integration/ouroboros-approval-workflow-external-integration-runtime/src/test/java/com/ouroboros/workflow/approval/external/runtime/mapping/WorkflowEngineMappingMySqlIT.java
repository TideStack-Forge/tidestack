package com.ouroboros.workflow.approval.external.runtime.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

import io.vavr.control.Try;

/**
 * MySQL integration gate for the mapping revision and validation state machine.
 *
 * <p>The test uses the production mapping service and a real MySQL table. The validator is
 * replaced only with a deterministic test double so that the database CAS boundaries can be
 * exercised without requiring a live external engine.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowEngineMappingMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private String tableName;
  private SqlDataStation station;

  @BeforeAll
  void migrateMappingModel() {
    String url = requireProperty(URL_PROPERTY);
    String user = requireProperty(USER_PROPERTY);
    String password = requireEnvironment(PASSWORD_ENVIRONMENT);
    dataSource = new DriverManagerDataSource(url, user, password);

    tableName = "t_awf_it_mapping_" + UUID.randomUUID().toString().replace("-", "");
    Map<String, DataModelMeta> metadata = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime.mapping");
    DataModelMeta source = metadata.get("WorkflowEngineMapping");
    assertThat(source).as("mapping model metadata").isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName("WorkflowEngineMappingMySqlIT");
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);

    station = new SqlDataStation(
        "mysql-mapping-integration",
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
  void validationCasAndActivationUsePersistedRevisionState() {
    TestMappingService service = mappingService();
    WorkflowEngineMappingValidator validator = mock(WorkflowEngineMappingValidator.class);
    when(validator.run(any())).thenAnswer(invocation -> {
      WorkflowEngineMapping validating = invocation.getArgument(0);
      assertThat(validating.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATING);

      WorkflowEngineMapping attemptedEdit = mapping("ignored", "conn-edited", "flow-edited");
      Try<WorkflowEngineMapping> edit = service.updateDraft(
          "scope-a", validating.getId(), attemptedEdit);
      assertThat(edit.isFailure()).as("editing a leased validation row must be rejected").isTrue();
      return Try.success(null);
    });
    service.setValidator(validator);

    Try<WorkflowEngineMapping> created = service.create(mapping(
        "mapping-" + UUID.randomUUID(), "connection-v1", "flow-a"));
    assertThat(created.isSuccess()).withFailMessage(() -> "mapping create failed: "
        + created.getCause()).isTrue();
    WorkflowEngineMapping draft = created.get();
    assertThat(draft.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);

    Try<WorkflowEngineMapping> validation = service.validate("scope-a", draft.getId());
    assertThat(validation.isSuccess()).withFailMessage(() -> "validation failed: "
        + validation.getCause()).isTrue();

    WorkflowEngineMapping persisted = service.findById("scope-a", draft.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATED);
    assertThat(persisted.getVersion()).isEqualTo(1);
    assertThat(persisted.getActiveRouteKey())
        .isEqualTo(ExternalRouteKeys.of("mapping-inactive", draft.getId()));

    Try<WorkflowEngineMapping> activated = service.activate("scope-a", draft.getId());
    assertThat(activated.isSuccess()).isTrue();
    WorkflowEngineMapping active = service.findById("scope-a", draft.getId()).orElseThrow();
    assertThat(active.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_ACTIVE);
    assertThat(active.getActiveRouteKey())
        .isEqualTo(ExternalRouteKeys.of("mapping-active", "scope-a", "flow-a"));
  }

  @Test
  void activeRevisionIsRetainedWhenPersistedDraftVersionIsCreated() {
    TestMappingService service = mappingService();
    WorkflowEngineMappingValidator validator = mock(WorkflowEngineMappingValidator.class);
    when(validator.run(any())).thenReturn(Try.success(null));
    service.setValidator(validator);

    Try<WorkflowEngineMapping> created = service.create(mapping(
        "mapping-" + UUID.randomUUID(), "connection-v1", "flow-b"));
    assertThat(created.isSuccess()).withFailMessage(() -> "mapping create failed: "
        + created.getCause()).isTrue();
    WorkflowEngineMapping original = created.get();
    Try<WorkflowEngineMapping> originalValidation = service.validate("scope-a", original.getId());
    assertThat(originalValidation.isSuccess()).withFailMessage(() -> "validation failed: "
        + originalValidation.getCause()).isTrue();
    assertThat(service.activate("scope-a", original.getId()).isSuccess()).isTrue();

    WorkflowEngineMapping changes = mapping("ignored", "connection-v2", "flow-b");
    changes.setExternalDefinitionId("definition-v2");
    WorkflowEngineMapping nextDraft = service.createDraftWithChanges(
        "scope-a", original.getId(), changes).get();

    assertThat(nextDraft.getId()).isNotEqualTo(original.getId());
    assertThat(nextDraft.getVersion()).isEqualTo(2);
    assertThat(nextDraft.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);
    assertThat(nextDraft.getConnectionId()).isEqualTo("connection-v2");
    assertThat(service.findById("scope-a", original.getId()).orElseThrow().getStatus())
        .isEqualTo(WorkflowEngineMapping.STATUS_ACTIVE);

    assertThat(service.validate("scope-a", nextDraft.getId()).isSuccess()).isTrue();
    assertThat(service.activate("scope-a", nextDraft.getId()).isSuccess()).isTrue();

    WorkflowEngineMapping persistedOriginal = service.findById("scope-a", original.getId()).orElseThrow();
    WorkflowEngineMapping persistedNext = service.findById("scope-a", nextDraft.getId()).orElseThrow();
    assertThat(persistedOriginal.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_RETIRED);
    assertThat(persistedOriginal.getVersion()).isEqualTo(1);
    assertThat(persistedNext.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_ACTIVE);
    assertThat(persistedNext.getVersion()).isEqualTo(2);
    assertThat(service.findActive("scope-a", "flow-b"))
        .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(persistedNext.getId()));
  }

  @Test
  void mirrorMappingPersistsWithItsIntentionallyEmptyExecutionModes() {
    TestMappingService service = mappingService();
    WorkflowEngineMapping mirror = mapping(
        "mapping-" + UUID.randomUUID(), "connection-mirror", "flow-mirror");
    mirror.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    mirror.setStartOptionsJson(null);

    Try<WorkflowEngineMapping> created = service.create(mirror);
    assertThat(created.isSuccess()).withFailMessage(() -> "mirror mapping create failed: "
        + describe(created.getCause())).isTrue();
    WorkflowEngineMapping persisted = service.findById("scope-a", mirror.getId()).orElseThrow();
    assertThat(persisted.getAuthority()).isEqualTo(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    assertThat(persisted.getStartOptionsJson()).isNull();
  }

  private TestMappingService mappingService() {
    var model = station.getDataModel("WorkflowEngineMappingMySqlIT").orElseThrow().withoutPlugins();
    return new TestMappingService(new BaseTypedDataModel<>(model, WorkflowEngineMapping.class));
  }

  private WorkflowEngineMapping mapping(String id, String connectionId, String flowKey) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setScope("scope-a");
    mapping.setFlowKey(flowKey);
    mapping.setAdapterId("adapter");
    mapping.setConnectionId(connectionId);
    mapping.setExternalDefinitionNamespace("");
    mapping.setExternalDefinitionId("definition-v1");
    mapping.setExternalDefinitionVersion("v1");
    mapping.setExternalDefinitionSchemaHash("schema-v1");
    mapping.setExternalDefinitionValidatedAt(LocalDateTime.now());
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setResultBindingJson("{}");
    mapping.setStartOptionsJson("{}");
    mapping.setActionMappingsJson("");
    mapping.setResultMappingsJson("{}");
    mapping.setCallbackJson("{}");
    mapping.setValidationMessage("");
    mapping.setCreatedBy("mysql-it");
    mapping.setCreatedAt(LocalDateTime.now());
    mapping.setUpdatedAt(LocalDateTime.now());
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

  private String describe(Throwable failure) {
    StringBuilder description = new StringBuilder();
    Throwable current = failure;
    while (current != null) {
      description.append(current.getClass().getSimpleName()).append(":")
          .append(current.getMessage()).append("; ");
      if (current instanceof com.ouroboros.exception.ValidationException validation) {
        description.append(validation.getErrors()).append("; ");
      }
      current = current.getCause();
    }
    return description.toString();
  }

  private static final class TestMappingService extends WorkflowEngineMappingServiceImpl {
    private final TypedDataModel<String, WorkflowEngineMapping> dataModel;

    private TestMappingService(TypedDataModel<String, WorkflowEngineMapping> dataModel) {
      this.dataModel = dataModel;
    }

    private void setValidator(WorkflowEngineMappingValidator validator) {
      this.validator = validator;
    }

    @Override
    protected TypedDataModel<String, WorkflowEngineMapping> getDataModel() {
      return dataModel;
    }
  }
}
