package com.ouroboros.workflow.approval.external.runtime.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

import io.vavr.control.Try;

/**
 * M3 映射聚合状态机与唯一性测试（内存 mock 数据模型，遵循 WorkflowModelServiceImplTest 模式）。
 */
class WorkflowEngineMappingServiceImplTest {

  private InMemoryMappingService service;
  private WorkflowEngineMappingValidator validator;
  private Map<String, WorkflowEngineMapping> store;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    service = new InMemoryMappingService(mockDataModel(store), store);
    validator = mock(WorkflowEngineMappingValidator.class);
    when(validator.run(any())).thenReturn(Try.success(null));
    service.validator = validator;
  }

  @Test
  void createSetsDraftDefaults() {
    Try<WorkflowEngineMapping> result = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1"));

    assertThat(result.isSuccess()).isTrue();
    WorkflowEngineMapping created = result.get();
    assertThat(created.getId()).isNotBlank();
    assertThat(created.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);
    assertThat(created.getVersion()).isEqualTo(1);
    assertThat(created.getCreatedAt()).isNotNull();
    assertThat(created.getUpdatedAt()).isNotNull();
  }

  @Test
  void activateRequiresValidatedState() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();

    Try<WorkflowEngineMapping> activated = service.activate("app-1", draft.getId());

    assertThat(activated.isFailure()).isTrue();
    assertThat(activated.getCause().getMessage()).contains(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE);
  }

  @Test
  void activateRetiresExistingActiveMapping() {
    WorkflowEngineMapping a = service.create(mapping("m-a", "app-1", "flow-a", "adapter-1")).get();
    service.validate("app-1", a.getId());
    service.activate("app-1", a.getId());

    WorkflowEngineMapping b = service.create(mapping("m-b", "app-1", "flow-a", "adapter-1")).get();
    service.validate("app-1", b.getId());
    service.activate("app-1", b.getId());

    assertThat(service.findActive("app-1", "flow-a")).isPresent();
    assertThat(service.findActive("app-1", "flow-a").orElseThrow().getId()).isEqualTo(b.getId());
    assertThat(store.get(a.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_RETIRED);
    assertThat(store.get(a.getId()).getActiveRouteKey())
        .isNotEqualTo(ExternalRouteKeys.of("mapping-active", "app-1", "flow-a"));
    assertThat(store.get(b.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_ACTIVE);
    assertThat(store.get(b.getId()).getActiveRouteKey())
        .isEqualTo(ExternalRouteKeys.of("mapping-active", "app-1", "flow-a"));
  }

  @Test
  void validateFailureMarksInvalidWithMessage() {
    when(validator.run(any())).thenReturn(Try.failure(new com.ouroboros.exception.BusinessException("[EX] 校验失败")));
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();

    Try<WorkflowEngineMapping> result = service.validate("app-1", draft.getId());

    assertThat(result.isFailure()).isTrue();
    assertThat(store.get(draft.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_INVALID);
    assertThat(store.get(draft.getId()).getValidationMessage()).contains("校验失败");
  }

  @Test
  void validateSuccessMarksValidating() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();

    Try<WorkflowEngineMapping> result = service.validate("app-1", draft.getId());

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get(draft.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATED);
    assertThat(store.get(draft.getId()).getValidationMessage()).isBlank();
  }

  @Test
  void mappingCannotBeEditedWhileValidationIsRunning() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    when(validator.run(any())).thenAnswer(invocation -> {
      WorkflowEngineMapping validating = invocation.getArgument(0);
      assertThat(validating.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATING);
      WorkflowEngineMapping changes = mapping("ignored", "app-1", "flow-a", "adapter-2");
      assertThat(service.updateDraft("app-1", validating.getId(), changes).isFailure()).isTrue();
      assertThat(service.createDraftWithChanges("app-1", validating.getId(), changes).isFailure()).isTrue();
      return Try.success(null);
    });

    assertThat(service.validate("app-1", draft.getId()).isSuccess()).isTrue();
    assertThat(store.get(draft.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATED);
    assertThat(store.get(draft.getId()).getAdapterId()).isEqualTo("adapter-1");
  }

  @Test
  void updateDraftUsesRevisionCasAndClearsPriorValidationMetadata() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    draft.setValidationMessage("old failure");
    draft.setExternalDefinitionValidatedAt(java.time.LocalDateTime.now());
    WorkflowEngineMapping changes = mapping("ignored", "ignored-scope", "flow-b", "adapter-2");

    Try<WorkflowEngineMapping> result = service.updateDraft("app-1", draft.getId(), changes);

    assertThat(result.isSuccess()).isTrue();
    WorkflowEngineMapping updated = result.get();
    assertThat(updated.getFlowKey()).isEqualTo("flow-b");
    assertThat(updated.getAdapterId()).isEqualTo("adapter-2");
    assertThat(updated.getValidationMessage()).isEmpty();
    assertThat(updated.getExternalDefinitionValidatedAt()).isNull();
    assertThat(updated.getVersion()).isEqualTo(draft.getVersion());
    assertThat(updated.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);
  }

  @Test
  void pauseRequiresActiveState() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    assertThat(service.pause("app-1", draft.getId()).isFailure()).isTrue();

    service.validate("app-1", draft.getId());
    service.activate("app-1", draft.getId());
    assertThat(service.pause("app-1", draft.getId()).isSuccess()).isTrue();
    assertThat(store.get(draft.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_PAUSED);
  }

  @Test
  void retireRejectsDraftAndAcceptsPaused() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    assertThat(service.retire("app-1", draft.getId()).isFailure()).isTrue();

    service.validate("app-1", draft.getId());
    service.activate("app-1", draft.getId());
    service.pause("app-1", draft.getId());
    assertThat(service.retire("app-1", draft.getId()).isSuccess()).isTrue();
    assertThat(store.get(draft.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_RETIRED);
  }

  @Test
  void createDraftWithChangesIncrementsVersionAppliesChangesAndKeepsOriginal() {
    WorkflowEngineMapping original = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    service.validate("app-1", original.getId());
    service.activate("app-1", original.getId());

    WorkflowEngineMapping changes = mapping("ignored", "other-scope", "flow-b", "adapter-2");
    changes.setConnectionId("conn-2");
    changes.setExternalDefinitionNamespace("ns-2");
    changes.setExternalDefinitionId("DEF-2");
    Try<WorkflowEngineMapping> draftTry = service.createDraftWithChanges("app-1", original.getId(), changes);

    assertThat(draftTry.isSuccess()).isTrue();
    WorkflowEngineMapping draft = draftTry.get();
    assertThat(draft.getVersion()).isEqualTo(2);
    assertThat(draft.getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_DRAFT);
    assertThat(draft.getId()).isNotEqualTo(original.getId());
    assertThat(draft.getScope()).isEqualTo("app-1");
    assertThat(draft.getFlowKey()).isEqualTo("flow-b");
    assertThat(draft.getAdapterId()).isEqualTo("adapter-2");
    assertThat(draft.getConnectionId()).isEqualTo("conn-2");
    assertThat(draft.getExternalDefinitionNamespace()).isEqualTo("ns-2");
    assertThat(draft.getExternalDefinitionId()).isEqualTo("DEF-2");
    assertThat(store.get(original.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_ACTIVE);
    assertThat(store.get(original.getId()).getVersion()).isEqualTo(1);
  }

  @Test
  void scopeFilteredApisCannotSeeOrMutateOtherScopeMappingByKnownId() {
    WorkflowEngineMapping appOne = service.create(mapping("m-app-1", "app-1", "flow-a", "adapter-1")).get();
    WorkflowEngineMapping appTwo = service.create(mapping("m-app-2", "app-2", "flow-a", "adapter-1")).get();
    service.validate("app-2", appTwo.getId());

    assertThat(service.search("app-1", null, null, 0, 50))
        .extracting(WorkflowEngineMapping::getId)
        .containsExactly(appOne.getId());
    assertThat(service.findById("app-1", appTwo.getId())).isEmpty();
    assertThat(service.validate("app-1", appTwo.getId()).isFailure()).isTrue();
    assertThat(service.activate("app-1", appTwo.getId()).isFailure()).isTrue();
    assertThat(service.pause("app-1", appTwo.getId()).isFailure()).isTrue();
    assertThat(service.retire("app-1", appTwo.getId()).isFailure()).isTrue();

    assertThat(store.get(appTwo.getId()).getStatus()).isEqualTo(WorkflowEngineMapping.STATUS_VALIDATED);
  }

  @Test
  void findActiveIgnoresNonActiveMappings() {
    WorkflowEngineMapping draft = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    assertThat(service.findActive("app-1", "flow-a")).isEmpty();

    service.validate("app-1", draft.getId());
    service.activate("app-1", draft.getId());
    assertThat(service.findActive("app-1", "flow-a")).isPresent();
    assertThat(service.findActive("app-2", "flow-a")).isEmpty();
  }

  @Test
  void findActiveFailsFastWhenMappingQueryFails() {
    TypedDataModel<String, WorkflowEngineMapping> dataModel = mock(MappingDataModel.class);
    when(dataModel.query(anyMap())).thenReturn(Try.failure(new InternalException("db down")));
    InMemoryMappingService failingService = new InMemoryMappingService(dataModel, new LinkedHashMap<>());

    assertThatThrownBy(() -> failingService.findActive("app-1", "flow-a"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED)
        .hasMessageContaining("flow-a");
  }

  @Test
  void conditionalDeleteRequiresCurrentBindingReferenceEpoch() {
    WorkflowEngineMapping mapping = service.create(mapping("m-1", "app-1", "flow-a", "adapter-1")).get();
    assertThat(service.acquireBindingReferenceLock(mapping.getId())).isTrue();
    assertThat(service.deleteMappingIfBindingReferenceEpoch(mapping.getId(), 0L)).isFalse();
    assertThat(service.findByIdForSystem(mapping.getId())).isPresent();
    assertThat(service.deleteMappingIfBindingReferenceEpoch(mapping.getId(), 1L)).isTrue();
    assertThat(service.findByIdForSystem(mapping.getId())).isEmpty();
  }

  static WorkflowEngineMapping mapping(String id, String scope, String flowKey, String adapterId) {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId(id);
    m.setScope(scope);
    m.setFlowKey(flowKey);
    m.setAdapterId(adapterId);
    m.setConnectionId("conn-1");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setExternalDefinitionId("DEF-1");
    return m;
  }

  static TypedDataModel<String, WorkflowEngineMapping> mockDataModel(Map<String, WorkflowEngineMapping> store) {
    TypedDataModel<String, WorkflowEngineMapping> dm = mock(MappingDataModel.class);
    when(dm.insert(any(WorkflowEngineMapping.class))).thenAnswer(inv -> {
      WorkflowEngineMapping m = inv.getArgument(0);
      store.put(m.getId(), m);
      return Try.success(m);
    });
    when(dm.query(anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      return Try.success(new ArrayList<>(store.values().stream()
          .filter(m -> matchesStatement(m, statement))
          .toList()));
    });
    when(dm.update(anyMap(), anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      long rows = 0;
      for (WorkflowEngineMapping mapping : store.values()) {
        if (matchesStatement(mapping, statement)) {
          data.forEach((field, value) -> {
            switch (field) {
              case "status" -> mapping.setStatus((String) value);
              case "validationMessage" -> mapping.setValidationMessage((String) value);
              case "updatedAt" -> mapping.setUpdatedAt((java.time.LocalDateTime) value);
              case "bindingReferenceEpoch" -> mapping.setBindingReferenceEpoch((Long) value);
              default -> {
              }
            }
          });
          rows++;
        }
      }
      return Try.success(rows);
    });
    when(dm.delete(anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      List<String> ids = store.values().stream()
          .filter(mapping -> matchesStatement(mapping, statement))
          .map(WorkflowEngineMapping::getId)
          .toList();
      ids.forEach(store::remove);
      return Try.success((long) ids.size());
    });
    return dm;
  }

  /** 按语句字段（scope/flowKey/status 等）过滤内存存储。 */
  static boolean matchesStatement(WorkflowEngineMapping m, Map<String, Object> statement) {
    Object whereValue = statement.get("WHERE");
    Map<String, Object> where = whereValue instanceof Map<?, ?> nested
        ? nested.entrySet().stream().collect(java.util.stream.Collectors.toMap(
            entry -> String.valueOf(entry.getKey()), Map.Entry::getValue,
            (left, right) -> right, LinkedHashMap::new))
        : statement;
    for (Map.Entry<String, Object> entry : where.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> condition)) {
        continue;
      }
      Object expected = condition.get("=");
      Object actual = switch (entry.getKey()) {
        case "scope" -> m.getScope();
        case "id" -> m.getId();
        case "flowKey" -> m.getFlowKey();
        case "status" -> m.getStatus();
        case "version" -> m.getVersion();
        case "updatedAt" -> m.getUpdatedAt();
        case "bindingReferenceEpoch" -> m.getBindingReferenceEpoch();
        case "businessRecordType" -> null;
        case "businessRecordId" -> null;
        default -> null;
      };
      if (!java.util.Objects.equals(expected, actual)) {
        return false;
      }
    }
    return true;
  }

  interface MappingDataModel extends TypedDataModel<String, WorkflowEngineMapping> {
  }

  static final class InMemoryMappingService extends WorkflowEngineMappingServiceImpl {
    private final TypedDataModel<String, WorkflowEngineMapping> dataModel;
    private final Map<String, WorkflowEngineMapping> store;

    InMemoryMappingService(TypedDataModel<String, WorkflowEngineMapping> dataModel, Map<String, WorkflowEngineMapping> store) {
      this.dataModel = dataModel;
      this.store = store;
    }

    @Override
    protected TypedDataModel<String, WorkflowEngineMapping> getDataModel() {
      return dataModel;
    }

    @Override
    public Boolean update(String id, WorkflowEngineMapping model) {
      store.put(id, model);
      return true;
    }

    @Override
    public java.util.Optional<WorkflowEngineMapping> findById(String scope, String mappingId) {
      WorkflowEngineMapping mapping = store.get(mappingId);
      if (mapping == null || !java.util.Objects.equals(scope, mapping.getScope())) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(mapping);
    }

    @Override
    public java.util.Optional<WorkflowEngineMapping> findByIdForSystem(String mappingId) {
      return java.util.Optional.ofNullable(store.get(mappingId));
    }
  }
}
