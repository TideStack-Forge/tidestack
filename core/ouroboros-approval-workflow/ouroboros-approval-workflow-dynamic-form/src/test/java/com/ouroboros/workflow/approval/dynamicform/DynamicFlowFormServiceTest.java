package com.ouroboros.workflow.approval.dynamicform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.record.Record;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.dynamicform.schema.DynamicFormFieldSchemaBuilder;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.dynamicform.service.DynamicFormWorkflowRecordService;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowBinding;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowHistory;

import io.vavr.control.Either;
import io.vavr.control.Try;

class DynamicFlowFormServiceTest {

  @Test
  void getInitialFormSchema_preservesPermissionFilteringAfterSchemaBuilderInjection() {
    var service = new DynamicFlowFormService();
    service.dynamicFormDefineService = new StubDynamicFormDefineService();
    service.dynamicFormFieldSchemaBuilder = new DynamicFormFieldSchemaBuilder(new StubFieldTypeCatalog());

    var permission = new NodeFormFieldPermission();
    permission.setName("title");
    permission.setVisible(true);
    permission.setWritable(false);
    permission.setRequired(true);

    var result = service.getInitialFormSchema("demo", Collections.singletonList(permission));

    assertTrue(result.isSuccess());
    var container = (com.ouroboros.uischema.schema.CommonContainer) result.get();
    assertEquals(1, container.getBody().size());
    var item = (com.ouroboros.uischema.schema.FormItemNode) container.getBody().get(0);
    assertEquals("title", item.getName());
    assertTrue(item.getRequired());
    assertTrue(item.getDisabled());
  }

  @Test
  void submitInitialFormRecordsBindingAndStartHistory() {
    var service = new DynamicFlowFormService();
    DataModel dataModel = mock(DataModel.class);
    service.dynamicFormDataService = name -> Optional.of(dataModel);
    var recordService = new RecordingDynamicFormWorkflowRecordService();
    service.dynamicFormWorkflowRecordService = recordService;

    when(dataModel.insert(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> inserted = new LinkedHashMap<>(invocation.getArgument(0));
      inserted.put("id", "form-1");
      return Try.success(Record.of(inserted));
    });

    var permission = editableRequiredPermission("title", "标题");

    var result = service.submitInitialForm(
        "instance-1",
        "expense",
        "starter-1",
        Collections.singletonList(permission),
        Collections.singletonMap("title", "报销申请"));

    assertTrue(result.isSuccess());
    assertEquals("form-1", result.get());
    assertEquals("instance-1", recordService.initialInstanceId);
    assertEquals("expense", recordService.initialFormKey);
    assertEquals("form-1", recordService.initialFormId);
    assertEquals("starter-1", recordService.initialUserId);
    assertEquals("报销申请", recordService.initialAfterData.get("title"));
    assertFalse(recordService.initialAfterData.containsKey("createdBy"));
  }

  @Test
  void submitInitialFormDelegatesPrimaryKeyGenerationToDataModelInsert() {
    var service = new DynamicFlowFormService();
    DataModel dataModel = mock(DataModel.class);
    var submittedData = new AtomicReference<Map<String, Object>>();

    service.dynamicFormDataService = name -> Optional.of(dataModel);
    when(dataModel.insert(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> submitted = invocation.getArgument(0);
      submittedData.set(new LinkedHashMap<>(submitted));
      Map<String, Object> inserted = new LinkedHashMap<>(submitted);
      inserted.put("id", "snowflake-1001");
      return Try.success(Record.of(inserted));
    });

    var permission = editableRequiredPermission("title", "标题");

    var result = service.submitInitialForm(
        "instance-1",
        "expense",
        "starter-1",
        Collections.singletonList(permission),
        Collections.singletonMap("title", "报销申请"));

    assertTrue(result.isSuccess());
    assertEquals("snowflake-1001", result.get());
    assertFalse(submittedData.get().containsKey("id"));
    assertEquals("报销申请", submittedData.get().get("title"));
  }

  @Test
  void submitInitialFormFailsBeforeWriteWhenDynamicFormIsNotWritable() {
    var service = new DynamicFlowFormService();
    DataModel dataModel = mock(DataModel.class);
    service.dynamicFormDataService = new DynamicFormDataService() {
      @Override
      public Optional<DataModel> getDataModel(String name) {
        return Optional.of(dataModel);
      }

      @Override
      public DataModel getWritableDataModel(String name) {
        throw new PlatformException("当前表单状态不支持此操作");
      }
    };

    var result = service.submitInitialForm(
        "instance-1",
        "expense",
        "starter-1",
        Collections.singletonList(editableRequiredPermission("title", "标题")),
        Collections.singletonMap("title", "报销申请"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("当前表单状态不支持此操作"));
    verifyNoInteractions(dataModel);
  }

  @Test
  void submitTaskFormRecordsBeforeAfterAndChangedFields() {
    var service = new DynamicFlowFormService();
    DataModel dataModel = mock(DataModel.class);
    service.dynamicFormDataService = name -> Optional.of(dataModel);
    var recordService = new RecordingDynamicFormWorkflowRecordService();
    service.dynamicFormWorkflowRecordService = recordService;

    when(dataModel.get("form-1"))
        .thenReturn(Try.success(Record.of(new LinkedHashMap<String, Object>() {{
          put("id", "form-1");
          put("amount", 100);
          put("title", "报销申请");
        }})))
        .thenReturn(Try.success(Record.of(new LinkedHashMap<String, Object>() {{
          put("id", "form-1");
          put("amount", 200);
          put("title", "报销申请");
        }})));
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));

    var permission = editableRequiredPermission("amount", "金额");

    var result = service.submitTaskForm(
        "instance-1",
        "task-1",
        "expense",
        "form-1",
        "approver-1",
        Collections.singletonList(permission),
        Collections.singletonMap("amount", 200));

    assertTrue(result.isSuccess());
    assertEquals("instance-1", recordService.updateInstanceId);
    assertEquals("task-1", recordService.updateTaskId);
    assertEquals("expense", recordService.updateFormKey);
    assertEquals("form-1", recordService.updateFormId);
    assertEquals("approver-1", recordService.updateUserId);
    assertEquals(100, recordService.updateBeforeData.get("amount"));
    assertEquals(200, recordService.updateAfterData.get("amount"));
    assertEquals(Collections.singleton("amount"), recordService.updateSubmittedFieldNames);
  }

  @Test
  void workflowRecordServiceStoresBindingAndChangedHistoryInCentralTables() {
    var service = new InMemoryDynamicFormWorkflowRecordService();

    service.recordInitialForm(
        "instance-1",
        "expense",
        "form-1",
        "starter-1",
        new LinkedHashMap<String, Object>() {{
          put("id", "form-1");
          put("amount", 100);
          put("title", "报销申请");
        }});
    DynamicFormWorkflowHistory history = service.recordTaskFormUpdate(
        "instance-1",
        "task-1",
        "expense",
        "form-1",
        "approver-1",
        new LinkedHashMap<String, Object>() {{
          put("id", "form-1");
          put("amount", 100);
          put("title", "报销申请");
        }},
        new LinkedHashMap<String, Object>() {{
          put("id", "form-1");
          put("amount", 200);
          put("title", "报销申请");
        }},
        new LinkedHashSet<>(Arrays.asList("amount", "title")));

    assertEquals(1, service.listBindings("instance-1", "expense", "form-1").size());
    assertEquals(2, service.listHistory("instance-1", null, "expense", "form-1").size());
    assertEquals(DynamicFormWorkflowHistory.ACTION_UPDATE, history.getAction());
    assertEquals(Collections.singletonMap("amount", 200), history.getChangedData());
    assertEquals(1, service.listHistory("instance-1", "task-1", "expense", "form-1").size());
  }

  @Test
  void workflowRecordServiceRejectsUnscopedQueryAndAppliesPageLimit() {
    var service = new InMemoryDynamicFormWorkflowRecordService();
    service.recordInitialForm("instance-1", "expense", "form-1", "starter-1", Collections.singletonMap("id", "form-1"));
    service.recordInitialForm("instance-1", "expense", "form-2", "starter-1", Collections.singletonMap("id", "form-2"));

    assertThrows(PlatformException.class, () -> service.listBindings(null, null, null));
    assertEquals(2L, service.countBindings("instance-1", null, null));
    assertEquals("form-2", service.listBindings("instance-1", null, null, 1, 1).get(0).getFormId());
  }

  @Test
  void workflowRecordServiceReusesExistingBindingWhenCreateHitsUniqueConflict() {
    var service = new UniqueConflictOnFirstInsertRecordService();

    service.recordInitialForm("instance-1", "expense", "form-1", "starter-1", Collections.singletonMap("id", "form-1"));

    assertEquals(1L, service.countBindings("instance-1", "expense", "form-1"));
    var history = service.listHistory("instance-1", null, "expense", "form-1").get(0);
    assertEquals("binding-1", history.getBindingId());
  }

  private NodeFormFieldPermission editableRequiredPermission(String name, String label) {
    var permission = new NodeFormFieldPermission();
    permission.setName(name);
    permission.setLabel(label);
    permission.setVisible(true);
    permission.setWritable(true);
    permission.setRequired(true);
    return permission;
  }

  private static class StubDynamicFormDefineService implements DynamicFormDefineService {
    @Override
    public Optional<DynamicForm> getDynamicFormByName(String name) {
      var field = new FormField();
      field.setName("title");
      field.setLabel("标题");
      field.setType("单行文本");

      var form = new DynamicForm();
      form.setName("demo");
      form.setTitle("演示表单");
      form.setFields(Collections.singletonList(field));
      return Optional.of(form);
    }

    @Override
    public Optional<DynamicForm> getDynamicFormById(Integer id) {
      return Optional.empty();
    }

    @Override
    public Long getTotal(Map<String, Object> where) {
      return 0L;
    }

    @Override
    public List<DynamicForm> search(Map<String, Object> query) {
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.data.model.DataModelMeta> getModelMetaList(Map<String, Object> where) {
      return Collections.emptyList();
    }

    @Override
    public Optional<DynamicForm> add(DynamicForm dynamicForm) {
      return Optional.empty();
    }

    @Override
    public Boolean update(DynamicForm dynamicForm) {
      return false;
    }

    @Override
    public Boolean delete(Integer id) {
      return false;
    }

    @Override
    public Boolean deploy(Integer id) {
      return false;
    }

    @Override
    public Boolean takeDown(Integer id) {
      return false;
    }

    @Override
    public Boolean reDeploy(Integer id) {
      return false;
    }

    @Override
    public Boolean archive(Integer id) {
      return false;
    }

    @Override
    public void refreshModels() {
    }
  }

  private static class StubFieldTypeCatalog implements FieldTypeCatalog {
    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      return Either.right(new CompiledFieldType() {
        @Override
        public com.ouroboros.data.model.DataModelFieldMeta toDataFieldMeta() {
          return new com.ouroboros.data.model.DataModelFieldMeta();
        }

        @Override
        public com.ouroboros.uischema.schema.FormItemNode toInputFormItem() {
          var item = new com.ouroboros.uischema.schema.FormItemNode();
          item.setType("input-text");
          return item;
        }

        @Override
        public com.ouroboros.uischema.schema.FormItemNode toDisplayFormItem() {
          var item = new com.ouroboros.uischema.schema.FormItemNode();
          item.setType("static-text");
          return item;
        }

        @Override
        public String getPrimaryKeyGenerator() {
          return null;
        }
      });
    }
  }

  private static class RecordingDynamicFormWorkflowRecordService extends DynamicFormWorkflowRecordService {
    private String initialInstanceId;
    private String initialFormKey;
    private String initialFormId;
    private String initialUserId;
    private Map<String, Object> initialAfterData;
    private String updateInstanceId;
    private String updateTaskId;
    private String updateFormKey;
    private String updateFormId;
    private String updateUserId;
    private Map<String, Object> updateBeforeData;
    private Map<String, Object> updateAfterData;
    private Set<String> updateSubmittedFieldNames;

    @Override
    public DynamicFormWorkflowBinding recordInitialForm(String instanceId, String formKey, String formId,
                                                        String userId, Map<String, Object> afterData) {
      this.initialInstanceId = instanceId;
      this.initialFormKey = formKey;
      this.initialFormId = formId;
      this.initialUserId = userId;
      this.initialAfterData = new LinkedHashMap<>(afterData);
      var binding = new DynamicFormWorkflowBinding();
      binding.setId("binding-1");
      binding.setInstanceId(instanceId);
      binding.setFormKey(formKey);
      binding.setFormId(formId);
      return binding;
    }

    @Override
    public DynamicFormWorkflowHistory recordTaskFormUpdate(String instanceId, String taskId, String formKey,
                                                           String formId, String userId,
                                                           Map<String, Object> beforeData,
                                                           Map<String, Object> afterData,
                                                           Set<String> submittedFieldNames) {
      this.updateInstanceId = instanceId;
      this.updateTaskId = taskId;
      this.updateFormKey = formKey;
      this.updateFormId = formId;
      this.updateUserId = userId;
      this.updateBeforeData = new LinkedHashMap<>(beforeData);
      this.updateAfterData = new LinkedHashMap<>(afterData);
      this.updateSubmittedFieldNames = new LinkedHashSet<>(submittedFieldNames);
      var history = new DynamicFormWorkflowHistory();
      history.setId("history-1");
      return history;
    }
  }

  private static class InMemoryDynamicFormWorkflowRecordService extends DynamicFormWorkflowRecordService {
    private final List<DynamicFormWorkflowBinding> bindings = new ArrayList<>();
    private final List<DynamicFormWorkflowHistory> histories = new ArrayList<>();

    @Override
    protected DynamicFormWorkflowBinding insertBinding(DynamicFormWorkflowBinding binding) {
      binding.setId("binding-" + (bindings.size() + 1));
      bindings.add(binding);
      return binding;
    }

    @Override
    protected DynamicFormWorkflowHistory insertHistory(DynamicFormWorkflowHistory history) {
      history.setId("history-" + (histories.size() + 1));
      histories.add(history);
      return history;
    }

    @Override
    protected void updateBinding(String bindingId, String userId) {
      bindings.stream()
          .filter(binding -> Objects.equals(bindingId, binding.getId()))
          .findFirst()
          .ifPresent(binding -> binding.setUpdatedBy(userId));
    }

    private List<DynamicFormWorkflowBinding> matchingBindings(Map<String, Object> where) {
      return bindings.stream()
          .filter(binding -> matches(where, binding.getInstanceId(), null, binding.getFormKey(), binding.getFormId()))
          .toList();
    }

    @Override
    protected List<DynamicFormWorkflowBinding> queryBindings(Map<String, Object> where, Integer offset, Integer limit) {
      var matched = matchingBindings(where);
      int from = Math.min(offset, matched.size());
      int to = Math.min(from + limit, matched.size());
      return matched.subList(from, to);
    }

    @Override
    protected Long countBindings(Map<String, Object> where) {
      return (long) matchingBindings(where).size();
    }

    private List<DynamicFormWorkflowHistory> matchingHistory(Map<String, Object> where) {
      return histories.stream()
          .filter(history -> matches(where, history.getInstanceId(), history.getTaskId(), history.getFormKey(), history.getFormId()))
          .toList();
    }

    @Override
    protected List<DynamicFormWorkflowHistory> queryHistory(Map<String, Object> where, Integer offset, Integer limit) {
      var matched = matchingHistory(where);
      int from = Math.min(offset, matched.size());
      int to = Math.min(from + limit, matched.size());
      return matched.subList(from, to);
    }

    @Override
    protected Long countHistory(Map<String, Object> where) {
      return (long) matchingHistory(where).size();
    }

    private boolean matches(Map<String, Object> where, String instanceId, String taskId, String formKey, String formId) {
      return matches(where, "instanceId", instanceId)
          && matches(where, "taskId", taskId)
          && matches(where, "formKey", formKey)
          && matches(where, "formId", formId);
    }

    private boolean matches(Map<String, Object> where, String name, String value) {
      return !where.containsKey(name) || Objects.equals(where.get(name), value);
    }
  }

  private static class UniqueConflictOnFirstInsertRecordService extends InMemoryDynamicFormWorkflowRecordService {
    private boolean conflict = true;

    @Override
    protected DynamicFormWorkflowBinding insertBinding(DynamicFormWorkflowBinding binding) {
      if (conflict) {
        conflict = false;
        super.insertBinding(binding);
        throw new PlatformException("存在重复绑定", new UniqueConstraintViolationException("duplicate binding"));
      }
      return super.insertBinding(binding);
    }
  }
}
