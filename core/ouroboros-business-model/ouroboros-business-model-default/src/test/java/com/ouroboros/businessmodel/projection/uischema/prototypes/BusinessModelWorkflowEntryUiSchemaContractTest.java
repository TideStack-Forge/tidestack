package com.ouroboros.businessmodel.projection.uischema.prototypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.uischema.schema.ActionNode;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.event.EventActionConfig;

class BusinessModelWorkflowEntryUiSchemaContractTest {

  @Test
  void generatedProcessDetailUsesExecutionCapabilitiesBeforeMountingViewers() throws Exception {
    var form = new FormNode();
    form.setBody(List.of());
    var schema = com.ouroboros.businessmodel.util.PrototypeTemplate.generate(
        "ui-schema/workflow/processDetail",
        Map.of("title", "订单", "formSchema", form, "actions", List.of()), FormNode.class);
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var json = mapper.valueToTree(schema);
    var output = java.nio.file.Path.of("target/generated-test-schemas/process-detail.json");
    java.nio.file.Files.createDirectories(output.getParent());
    mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), schema);
    assertEquals("/api/approval-workflow/instance/${instanceId}/info", json.path("initApi").path("url").asText());
    var recordTab = json.path("body").get(1).path("items").get(1).path("body");
    assertEquals("${!!externalUrl}", recordTab.get(1).path("visibleOn").asText());
    assertEquals("${diagramAvailable === true}", recordTab.get(2).path("visibleOn").asText());
    assertEquals("${timelineAvailable === true}", recordTab.get(3).path("visibleOn").asText());

  }

  @Test
  void workflowSubmitButtonShouldAutoStartBusinessModelProcessAfterSave() {
    var model = createWorkflowCustomerModel();
    var config = workflowFormConfig("auto");

    FormNode form = FormUiSchemaModelPrototype.get(model)
        .configure(config)
        .getModel();

    var submit = requiredAction(form.getProp("actions"), "提交");
    var actions = clickActions(submit);
    var saveIndex = indexOfOutputVar(actions, "saveResult");
    var requirementsIndex = indexOfOutputVar(actions, WorkflowStartActionBuilder.REQUIREMENTS_RESULT);
    var assigneeDialogIndex = indexOfOutputVar(actions, WorkflowStartActionBuilder.ASSIGNEE_SELECTION);
    var startIndex = indexOfOutputVar(actions, "startProcessResult");

    assertEquals(Boolean.TRUE, submit.getProp(Boolean.class, "disabledOnAction").orElse(null));
    assertTrue(saveIndex >= 0, "submit action must save the business record first");
    assertTrue(requirementsIndex > saveIndex, "start requirements must run after saveResult is available");
    assertTrue(assigneeDialogIndex > requirementsIndex, "assignee dialog must use runtime requirements result");
    assertTrue(startIndex > assigneeDialogIndex, "start-process must run after optional assignee selection");

    var requirementsAction = actions.get(requirementsIndex);
    assertEquals("ajax", requirementsAction.getActionType());
    var requirementsApi = requiredApi(requirementsAction.getProp("api"));
    assertEquals("post", requirementsApi.getMethod());
    assertEquals("/api/approval-workflow/instance/customer-approval/start-requirements", requirementsApi.getUrl());
    assertEquals(Collections.emptyMap(), requirementsApi.getData());
    assertRequestAdaptorBuildsFormDataFrom(requirementsApi, "context.saveResult");

    var dialogAction = actions.get(assigneeDialogIndex);
    assertEquals("dialog", dialogAction.getActionType());
    assertEquals("${startRequirementsResult.responseData.requiresInput}", dialogAction.getExpression());
    assertEquals(Boolean.TRUE, dialogAction.getProp(Boolean.class, "waitForAction").orElse(null));

    var startAction = actions.get(startIndex);
    assertEquals("ajax", startAction.getActionType());
    var api = requiredApi(startAction.getProp("api"));
    assertEquals("post", api.getMethod());
    assertEquals("/api/demo/customer/${saveResult.id}/start-process", api.getUrl());
    assertEquals(Collections.emptyMap(), api.getData());
    assertRequestAdaptorBuildsTaskAssignee(api);
    assertEquals(Boolean.TRUE, api.getProp(Boolean.class, "silent").orElse(null));
  }

  @Test
  void workflowListOperationsShouldExposeManualStartAndBusinessModelTaskPages() {
    var model = createWorkflowCustomerModel();
    var config = workflowListConfig();

    new CrudUiSchemaActionProcessor(model, config).preprocessOperations();

    var operations = requiredNodeList(config.get("operations"));
    var start = requiredOperation(operations, "start-process-operation");
    var process = requiredOperation(operations, "process-operation");
    var detail = requiredOperation(operations, "process-detail-operation");

    assertEquals("发起流程", start.getLabel());
    assertTrue(start.getVisibleOn().contains("ARRAYINCLUDES(["));
    assertTrue(start.getVisibleOn().contains("'SUBMITTED'"));
    assertTrue(start.getVisibleOn().contains("'VETO'"));
    assertTrue(start.getVisibleOn().contains("!(processInfo && processInfo.instanceId)"));
    var startActions = clickActions(start);
    var requirementsIndex = indexOfOutputVar(startActions, WorkflowStartActionBuilder.REQUIREMENTS_RESULT);
    var assigneeDialogIndex = indexOfOutputVar(startActions, WorkflowStartActionBuilder.ASSIGNEE_SELECTION);
    var startIndex = indexOfOutputVar(startActions, WorkflowStartActionBuilder.START_RESULT);

    assertTrue(requirementsIndex >= 0, "manual start must query runtime requirements first");
    assertTrue(assigneeDialogIndex > requirementsIndex, "manual start must open optional assignee dialog after requirements");
    assertTrue(startIndex > assigneeDialogIndex, "manual start-process must run after optional assignee selection");

    var requirementsApi = requiredApi(startActions.get(requirementsIndex).getProp("api"));
    assertEquals("post", requirementsApi.getMethod());
    assertEquals("/api/approval-workflow/instance/customer-approval/start-requirements", requirementsApi.getUrl());
    assertEquals(Collections.emptyMap(), requirementsApi.getData());
    assertRequestAdaptorBuildsFormDataFrom(requirementsApi, "context");

    var startApi = requiredApi(startActions.get(startIndex).getProp("api"));
    assertEquals("post", startApi.getMethod());
    assertEquals("/api/demo/customer/${id}/start-process", startApi.getUrl());
    assertEquals(Collections.emptyMap(), startApi.getData());
    assertRequestAdaptorBuildsTaskAssignee(startApi);

    assertEquals("处理", process.getLabel());
    assertEquals("dialog", process.getActionType());
    assertTrue(process.getVisibleOn().contains("processInfo.processable"));
    assertTrue(process.getVisibleOn().contains("processInfo.processableTaskId"));
    var processDialog = process.getProp(SchemaNode.class, "dialog").orElseThrow();
    assertEquals(
        "/demo/customer/${id}/approval?instanceId=${processInfo.instanceId}&taskId=${processInfo.processableTaskId}",
        processDialog.getProp(String.class, "url").orElse(null)
    );

    assertEquals("详情", detail.getLabel());
    assertEquals("dialog", detail.getActionType());
    assertEquals("${processInfo && processInfo.instanceId}", detail.getVisibleOn());
    var detailDialog = detail.getProp(SchemaNode.class, "dialog").orElseThrow();
    assertEquals(
        "/demo/customer/${id}/process-detail?instanceId=${processInfo.instanceId}",
        detailDialog.getProp(String.class, "url").orElse(null)
    );
  }

  private static Map<String, Object> workflowFormConfig(String startMode) {
    return stringMap(
        "workflowEnabled", true,
        "statusField", "status",
        "draftStatus", "DRAFT",
        "submittedStatus", "SUBMITTED",
        "startMode", startMode,
        "fields", List.of(stringMap("type", "field", "name", "name", "title", "客户名称"))
    );
  }

  private static Map<String, Object> workflowListConfig() {
    return stringMap(
        "showOperations", true,
        "workflowEnabled", true,
        "showProcessButton", true,
        "statusField", "status",
        "submittedStatus", "SUBMITTED"
    );
  }

  private static ActionNode requiredAction(Object rawActions, String label) {
    for (Object item : requiredNodeList(rawActions)) {
      var action = assertInstanceOf(ActionNode.class, item);
      if (Objects.equals(label, action.getLabel())) {
        return action;
      }
    }
    throw new AssertionError("missing action " + label);
  }

  private static ApiConfig requiredApi(Object rawApi) {
    assertNotNull(rawApi, "api should exist");
    if (rawApi instanceof ApiConfig api) {
      return api;
    }
    var map = assertInstanceOf(Map.class, rawApi);
    var copy = new LinkedHashMap<String, Object>();
    for (Object entryObject : map.entrySet()) {
      var entry = assertInstanceOf(Map.Entry.class, entryObject);
      copy.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return new ApiConfig(copy);
  }

  private static void assertRequestAdaptorBuildsFormDataFrom(ApiConfig api, String sourceExpression) {
    String requestAdaptor = api.getProp(String.class, "requestAdaptor").orElse("");
    assertTrue(requestAdaptor.contains(sourceExpression), requestAdaptor);
    assertTrue(requestAdaptor.contains("delete formData.processInfo"), requestAdaptor);
    assertTrue(requestAdaptor.contains("api.data = { formData: formData }"), requestAdaptor);
  }

  private static void assertRequestAdaptorBuildsTaskAssignee(ApiConfig api) {
    String requestAdaptor = api.getProp(String.class, "requestAdaptor").orElse("");
    assertTrue(requestAdaptor.contains("context.startAssigneeSelection"), requestAdaptor);
    assertTrue(requestAdaptor.contains("var taskAssignee = value && value.taskAssignee"), requestAdaptor);
    assertTrue(requestAdaptor.contains("api.data = taskAssignee ? { taskAssignee: taskAssignee } : {}"), requestAdaptor);
  }

  private static List<?> requiredNodeList(Object rawActions) {
    assertNotNull(rawActions, "actions should exist");
    return assertInstanceOf(List.class, rawActions);
  }

  private static List<EventActionConfig> clickActions(ActionNode action) {
    var onEvent = action.getOnEvent();
    assertNotNull(onEvent, "onEvent should exist");
    var click = onEvent.getOnEventMap().get("click");
    assertNotNull(click, "click actions should exist");
    return click.getActions();
  }

  private static int indexOfOutputVar(List<EventActionConfig> actions, String outputVar) {
    for (int i = 0; i < actions.size(); i++) {
      if (Objects.equals(outputVar, actions.get(i).getOutputVar())) {
        return i;
      }
    }
    return -1;
  }

  private static ActionNode requiredOperation(List<?> operations, String className) {
    for (Object item : operations) {
      var action = assertInstanceOf(ActionNode.class, item);
      for (String token : Objects.toString(action.getClassName(), "").split("\\s+")) {
        if (Objects.equals(className, token)) {
          return action;
        }
      }
    }
    throw new AssertionError("missing operation " + className);
  }

  private static DefaultBusinessModel createWorkflowCustomerModel() {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "客户 ID", "雪花主键", true, true));
    fields.add(field("name", "客户名称", "单行文本", true, false));
    fields.add(field("status", "审批状态", "单行文本", false, false));

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户");
    modelMeta.setFields(fields);
    modelMeta.setRelations(Collections.emptyList());
    modelMeta.setExtraProp("workflow", workflowConfig());

    return new DefaultBusinessModel(modelMeta, emptyProvider());
  }

  private static BusinessModelFieldMeta field(String name, String title, String type, boolean required, boolean primaryKey) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName(name);
    fieldMeta.setTitle(title);
    fieldMeta.setType(type);
    fieldMeta.setIsRequired(required);
    fieldMeta.setIsPrimaryKey(primaryKey);
    fieldMeta.setExtraProps(Collections.emptyMap());
    return fieldMeta;
  }

  private static Map<String, Object> workflowConfig() {
    var statusMap = new LinkedHashMap<String, Object>();
    statusMap.put("draft", status("DRAFT", "草稿"));
    statusMap.put("submitted", status("SUBMITTED", "已提交"));
    statusMap.put("pending", status("PENDING", "待处理"));
    statusMap.put("processing", status("PROCESSING", "处理中"));
    statusMap.put("suspended", status("SUSPENDED", "已暂停"));
    statusMap.put("withdrawn", status("WITHDRAWN", "已撤回"));
    statusMap.put("veto", status("VETO", "已否决"));
    statusMap.put("approved", status("APPROVED", "已通过"));
    statusMap.put("canceled", status("CANCELED", "已撤销"));
    statusMap.put("terminated", status("TERMINATED", "已终止"));

    return stringMap(
        "isEnabled", true,
        "restartableStatus", List.of("veto"),
        "bindings", List.of(stringMap(
            "key", "default",
            "processId", "customer-approval",
            "statusField", "status",
            "statusMap", statusMap,
            "trigger", "auto"
        ))
    );
  }

  private static Map<String, Object> status(String code, String label) {
    return stringMap("code", code, "label", label);
  }

  private static BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String fullName) {
        return Optional.empty();
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return Collections.emptyMap();
      }
    };
  }

  private static Map<String, Object> stringMap(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }
}
