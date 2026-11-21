package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.api.InitTaskFormAPI;
import com.ouroboros.businessmodel.projection.workflow.api.StartProcessAPI;
import com.ouroboros.businessmodel.projection.workflow.api.SubmitTaskAPI;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowBusinessActionContractTest {
  @Test
  void disabledLegacyFlatBindingRetainsDeliveryContractsWithoutReenablingCommands() {
    var meta = source(false);
    meta.setExtraProp("workflow", Map.of("isEnabled", false, "processId", "documentApproval", "statusField", "status"));
    var model = new DefaultBusinessModel(meta, provider());
    assertFalse(model.getBehavior().getActions().containsKey("startProcess"));
    assertTrue(model.getBehavior().getEvents().containsKey("workflowApproved"));
    var context = new ModelProjectionPipelineContext<>(model);
    new com.ouroboros.businessmodel.projection.workflow.logicflow.ProcessCallbackLogic().process(context);
    assertEquals("documentApproval", context.getModel(com.ouroboros.workflow.approval.callback.CallbackBinding.class, "cases.Document")
        .orElseThrow().flowKey());
    assertNotNull(context.getModel(com.ouroboros.logicflow.model.FlowModelMeta.class, "cases.Document.processCallback").orElseThrow());
  }

  @Test
  void workflowConfigurationPublishesActionsAndFactsWithoutChangingLegacyMetadata() {
    var source = source(true);
    var model = new DefaultBusinessModel(source, provider());
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    var behavior = model.getBehavior();
    for (String name : List.of("startProcess", "submitTask", "initTaskForm")) {
      var action = behavior.getActions().get(name);
      assertNotNull(action);
      assertEquals("cases.Document." + name, action.getBinding().getFlowName());
      assertEquals("cases.Document.workflow", behavior.executionAuthorityName(name));
      assertEquals("initTaskForm".equals(name), action.getReadOnly());
      assertTrue(action.getEmits().isEmpty());
      var params = "startProcess".equals(name) ? Map.of("record", Map.of("id", "42")) : "submitTask".equals(name)
              ? Map.of("record", Map.of("id", "42"), "instanceId", "instance", "taskId", "task", "action", "PASS")
              : Map.of("record", Map.of("id", "42"), "instanceId", "instance", "taskId", "task");
      var input = behavior.inputPlan(name).prepare("initTaskForm".equals(name)
          ? com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(params)
          : com.ouroboros.businessmodel.action.BusinessActionInput.of(params, Map.of("formData", Map.of("title", "changed"))));
      var flow = behavior.inputPlan(name).flowInput(input, Map.of("request", Map.of("marker", "original")));
      assertEquals("42", ((Map<?, ?>) flow.get("params")).get("id"));
      if ("submitTask".equals(name)) { assertEquals("PASS", ((Map<?, ?>) flow.get("params")).get("ACTION")); }
      assertNull(behavior.mutationEventPlan(name));
    }
    assertEquals(14, behavior.getEvents().size());
    behavior.getEvents().values().stream().filter(event -> event.getName().startsWith("workflow")).forEach(event -> {
      assertEquals("instance", event.getSubject());
      assertEquals(List.of("workflow"), event.getAllowedProducerKinds());
    });
    assertTrue(source.getActions().isEmpty());
    assertTrue(source.getEvents().isEmpty());
    assertNull(source.getFormatVersion());
  }

  @Test
  void existingWorkflowUrlsUseTheSameLazyActionMappingAndDisableTogether() {
    var model = new DefaultBusinessModel(source(true), provider());
    var context = new ModelProjectionPipelineContext<>(model);
    new StartProcessAPI().process(context);
    new SubmitTaskAPI().process(context);
    new InitTaskFormAPI().process(context);
    var apis = context.getModelMap(WebAPIModel.class);
    assertEquals(3, apis.size());
    context.setAttribute("hasDataModel", true);
    new com.ouroboros.businessmodel.projection.authority.WorkflowMPS().process(context);
    new com.ouroboros.businessmodel.projection.authority.BehaviorActionMPS().process(context);
    var authorities = context.getModelMap(com.ouroboros.security.authority.Authority.class);
    assertEquals(java.util.Set.of("cases.Document.workflow", "cases.Document.behaviors.inspect"), authorities.keySet());
    for (String action : List.of("startProcess", "submitTask", "initTaskForm")) {
      assertTrue(authorities.get("cases.Document.workflow").getApis().contains(
          "POST /api/business-model-behaviors/cases.Document/actions/" + action + "/invoke"));
    }
    apis.values().forEach(api -> {
      assertEquals(HandlerType.BUSINESS_ACTION, api.getHandlerType());
      assertEquals("cases.Document", api.getExtraProp(String.class, "businessModel").orElseThrow());
      assertEquals("2.0", api.getExtraProp(String.class, "formatVersion").orElseThrow());
      assertEquals("${ request.pathVariables['id'] }", api.getExtraProp(Map.class, "actionInputMapping").orElseThrow().get("params.record.id"));
    });
    assertTrue(apis.keySet().stream().anyMatch(key -> key.endsWith("/{id}/start-process")));
    assertTrue(apis.keySet().stream().anyMatch(key -> key.endsWith("/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}")));
    var disabled = new DefaultBusinessModel(source(false), provider());
    assertFalse(disabled.getBehavior().getActions().containsKey("startProcess"));
    assertTrue(disabled.getBehavior().getEvents().containsKey("workflowStarted"));
    var disabledContext = new ModelProjectionPipelineContext<>(disabled);
    new StartProcessAPI().process(disabledContext);
    assertTrue(disabledContext.getModelMap(WebAPIModel.class).isEmpty());
    new com.ouroboros.businessmodel.projection.workflow.logicflow.ProcessCallbackLogic().process(disabledContext);
    assertNotNull(disabledContext.getModelMap(com.ouroboros.logicflow.model.FlowModelMeta.class).get("cases.Document.processCallback"));
    assertEquals("documentApproval", disabledContext.getModelMap(com.ouroboros.workflow.approval.callback.CallbackBinding.class)
        .get("cases.Document").flowKey());
  }

  private DefaultBusinessModelMeta source(boolean enabled) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("cases");
    meta.setName("Document");
    meta.setTitle("审批单据");
    meta.setIsEnabled(true);
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("单行文本");
    id.setIsPrimaryKey(true);
    var statusField = new BusinessModelFieldMeta();
    statusField.setName("status");
    statusField.setType("单行文本");
    meta.setFields(List.of(id, statusField));
    var status = new java.util.LinkedHashMap<String, Object>();
    for (String name : List.of("draft", "submitted", "pending", "processing", "suspended", "withdrawn", "approved", "veto", "canceled", "terminated")) {
      status.put(name, Map.of("code", name, "label", name));
    }
    meta.setExtraProp("workflow", Map.of("isEnabled", enabled, "bindings", List.of(Map.of(
        "key", "default", "processId", "documentApproval", "statusField", "status", "statusMap", status, "trigger", "manual"))));
    return meta;
  }

  private BusinessModelProvider provider() {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
  }
}
