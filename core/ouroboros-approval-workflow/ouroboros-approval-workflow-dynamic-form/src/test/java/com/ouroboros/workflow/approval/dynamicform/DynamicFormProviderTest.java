package com.ouroboros.workflow.approval.dynamicform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;

class DynamicFormProviderTest {

  @Test
  void getKeepsDynamicFormFieldTypesForWorkflowFieldSource() throws Exception {
    var provider = new DynamicFormProvider();
    bindDynamicFormDefineService(provider, new StubDynamicFormDefineService(dynamicForm()));

    var flowForm = provider.get("purchase");

    assertTrue(flowForm.isPresent());
    assertEquals("purchase", flowForm.get().getName());
    assertEquals("采购", flowForm.get().getTitle());
    assertEquals("number", flowForm.get().getFields().get(0).getType());
    assertEquals("员工", flowForm.get().getFields().get(1).getType());
  }

  @Test
  void searchDoesNotRequireProjectedFieldListForRuntimeCreateEntry() throws Exception {
    var provider = new DynamicFormProvider();
    var dynamicForm = dynamicForm();
    dynamicForm.setFields(null);
    bindDynamicFormDefineService(provider, new StubDynamicFormDefineService(dynamicForm));

    var result = provider.search("purchase", 1, 20);

    assertEquals(1L, result._1());
    assertEquals(1, result._2().size());
    assertEquals("purchase", result._2().get(0).getName());
    assertEquals(Collections.emptyList(), result._2().get(0).getFields());
    assertEquals(Collections.emptyList(), result._2().get(0).getExpressionVariables());
  }

  @Test
  void searchShouldKeepDeployedFilterKeywordPatternProjectionAndPagination() throws Exception {
    var provider = new DynamicFormProvider();
    var service = new StubDynamicFormDefineService(dynamicForm());
    bindDynamicFormDefineService(provider, service);

    provider.search("purchase%", 2, 5);

    assertEquals(Map.of("status", "已部署", "name", Map.of("$like", "purchase%")), service.lastWhere);
    assertEquals(service.lastWhere, service.lastQuery.get("WHERE"));
    assertEquals(List.of("name", "title"), service.lastQuery.get("SELECT"));
    assertEquals(5, service.lastQuery.get("OFFSET"));
    assertEquals(5, service.lastQuery.get("LIMIT"));

    provider.search("");
    assertEquals(Map.of("status", "已部署"), service.lastQuery.get("WHERE"));
    assertTrue(!service.lastQuery.containsKey("LIMIT"));
  }

  @Test
  void workflowHistorySnapshotFieldsUseJsonValueType() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.dynamicform.model");
    DataModelMeta meta = metas.get("DynamicFormWorkflowHistory");

    assertEquals("Json", fieldType(meta, "beforeData"));
    assertEquals("Json", fieldType(meta, "afterData"));
    assertEquals("Json", fieldType(meta, "changedData"));
  }

  private static String fieldType(DataModelMeta meta, String fieldName) {
    return meta.getFields().stream()
        .filter(field -> fieldName.equals(field.getName()))
        .findFirst()
        .orElseThrow(AssertionError::new)
        .getType();
  }

  private static void bindDynamicFormDefineService(
      DynamicFormProvider provider,
      DynamicFormDefineService dynamicFormDefineService) throws Exception {
    Field field = DynamicFormProvider.class.getDeclaredField("dynamicFormDefineService");
    field.setAccessible(true);
    field.set(provider, dynamicFormDefineService);
  }

  private static DynamicForm dynamicForm() {
    var amount = new FormField();
    amount.setName("amount");
    amount.setLabel("金额");
    amount.setType("number");

    var reviewer = new FormField();
    reviewer.setName("reviewer");
    reviewer.setLabel("复核人");
    reviewer.setType("员工");

    var form = new DynamicForm();
    form.setName("purchase");
    form.setTitle("采购");
    form.setFields(Arrays.asList(amount, reviewer));
    return form;
  }

  private static class StubDynamicFormDefineService implements DynamicFormDefineService {
    private final DynamicForm dynamicForm;
    private Map<String, Object> lastWhere;
    private Map<String, Object> lastQuery;

    private StubDynamicFormDefineService(DynamicForm dynamicForm) {
      this.dynamicForm = dynamicForm;
    }

    @Override
    public Optional<DynamicForm> getDynamicFormByName(String name) {
      return dynamicForm.getName().equals(name) ? Optional.of(dynamicForm) : Optional.empty();
    }

    @Override
    public Optional<DynamicForm> getDynamicFormById(Integer id) {
      return Optional.empty();
    }

    @Override
    public Long getTotal(Map<String, Object> where) {
      lastWhere = where;
      return 1L;
    }

    @Override
    public List<DynamicForm> search(Map<String, Object> query) {
      lastQuery = query;
      return Collections.singletonList(dynamicForm);
    }

    @Override
    public List<DataModelMeta> getModelMetaList(Map<String, Object> where) {
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
}
