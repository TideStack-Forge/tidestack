package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport;
import com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport;

/** Workflow configuration owns these contracts; normalized definitions are never written to source metadata. */
final class WorkflowBusinessActionNormalizer {
  private static final List<String> ACTIONS = List.of("startProcess", "submitTask", "initTaskForm");
  private static final List<String> EVENTS = List.of("workflowStarted", "workflowApproved", "workflowRejected",
      "workflowWithdrawn", "workflowCancelled", "workflowSuspended", "workflowResumed", "workflowFailed",
      "workflowTaskActionCompleted", "workflowTaskCreated", "workflowTaskTimeoutReminded");

  private WorkflowBusinessActionNormalizer() { }

  static LegacyBusinessActionNormalizer.NormalizedBehavior normalize(DefaultBusinessModelMeta meta, DefaultBusinessModel model) {
    boolean enabled = meta.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false);
    boolean bound = meta.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId")
        .filter(value -> !value.isBlank()).isPresent();
    if (!enabled && !bound) {
      return new LegacyBusinessActionNormalizer.NormalizedBehavior(List.of(), List.of(), Map.of(), Map.of());
    }
    var actions = new ArrayList<ActionDefinition>();
    var authorities = new LinkedHashMap<String, String>();
    for (String name : enabled ? ACTIONS : List.<String>of()) {
      var action = new ActionDefinition();
      action.setName(name);
      action.setTitle(switch (name) {
        case "startProcess" -> "发起审批流程";
        case "submitTask" -> "办理审批任务";
        default -> "初始化审批任务表单";
      });
      action.setIsEnabled(true);
      action.setReadOnly("initTaskForm".equals(name));
      action.setInputSchema(inputSchema(meta, model, name));
      var binding = new ActionBinding();
      binding.setKind("logicflow");
      binding.setFlowName(meta.getFullName() + "." + name);
      var mapping = new LinkedHashMap<String, Object>();
      mapping.put("params.id", "${ input.params.record.id }");
      mapping.put("context", "${ context }");
      if (!"startProcess".equals(name)) {
        mapping.put("params.instanceId", "${ input.params.instanceId }");
        mapping.put("params.taskId", "${ input.params.taskId }");
      }
      if ("submitTask".equals(name)) { mapping.put("params.ACTION", "${ input.params.action }"); }
      if (!"initTaskForm".equals(name)) { mapping.put("data", "${ input.data }"); }
      binding.setInputMapping(mapping);
      action.setBinding(binding);
      actions.add(action);
      authorities.put(name, meta.getFullName() + ".workflow");
    }
    var sources = new ArrayList<com.ouroboros.businessmodel.behavior.BehaviorMemberSource>();
    actions.forEach(action -> sources.add(LegacyBusinessActionNormalizer.generatedSource(meta, "action", action.getName(), "workflow")));
    var events = new ArrayList<EventDefinition>();
    for (String name : EVENTS) {
      var event = new EventDefinition();
      event.setName(name);
      event.setTitle(name);
      event.setSubject("instance");
      event.setAllowedProducerKinds(List.of("workflow"));
      event.setOccurrenceCondition("正式审批源事实提交后，必要业务状态同步与事实接收成功；不是 Action 返回即产生");
      event.setPayloadSchema(payloadSchema(name));
      events.add(event);
      sources.add(LegacyBusinessActionNormalizer.generatedSource(meta, "event", name, "workflow.bindings"));
      if (bound) {
        String flowKey = meta.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElseThrow();
        sources.add(new com.ouroboros.businessmodel.behavior.BehaviorMemberSource("event", name, "producer", "generated",
            new com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource("approval-workflow-definition:" + flowKey,
                "process", "2.0"), "审批源事实提交后，经必要状态同步和可靠投影产生；也可由审批中心或系统流程触发，非每次 Action 调用必发"));
      }
    }
    return new LegacyBusinessActionNormalizer.NormalizedBehavior(List.copyOf(actions), List.copyOf(events), Map.of(),
        Map.copyOf(authorities), List.copyOf(sources));
  }

  private static Map<String, Object> inputSchema(DefaultBusinessModelMeta meta, DefaultBusinessModel model, String name) {
    var id = SchemaModelJsonSchemaSupport.toJsonSchemaMap(BusinessModelSchemaSupport.primaryKeySchema(model));
    var reference = Map.of("type", "object", "x-business-object-ref", meta.getFullName(), "required", List.of("id"),
        "additionalProperties", false, "properties", Map.of("id", id));
    var params = new LinkedHashMap<String, Object>();
    var required = new ArrayList<String>();
    params.put("record", reference);
    required.add("record");
    if (!"startProcess".equals(name)) {
      for (String key : List.of("instanceId", "taskId")) {
        params.put(key, Map.of("type", "string", "minLength", 1));
        required.add(key);
      }
    }
    if ("submitTask".equals(name)) {
      params.put("action", Map.of("type", "string", "minLength", 1));
      required.add("action");
    }
    var properties = new LinkedHashMap<String, Object>();
    properties.put("params", Map.of("type", "object", "required", required, "additionalProperties", false,
        "properties", params));
    if (!"initTaskForm".equals(name)) { properties.put("data", Map.of("type", "object")); }
    return Map.of("type", "object", "required", List.of("params"), "additionalProperties", false, "properties", properties);
  }

  private static Map<String, Object> payloadSchema(String name) {
    var properties = new LinkedHashMap<String, Object>();
    for (String field : List.of("eventFactId", "eventTime", "flowKey", "instanceId", "taskId", "action", "outcome", "approvalCaseId")) {
      properties.put(field, Map.of("type", "string"));
    }
    properties.put("submissionRound", Map.of("type", "integer", "minimum", 1));
    properties.put("stateSequence", Map.of("type", "integer", "minimum", 1));
    var required = new ArrayList<>(List.of("eventFactId", "eventTime", "flowKey", "instanceId"));
    if (name.startsWith("workflowTask")) { required.add("taskId"); }
    else { required.add("stateSequence"); }
    if ("workflowTaskActionCompleted".equals(name)) { required.add("action"); }
    return Map.of("type", "object", "additionalProperties", false, "properties", properties, "required", required);
  }
}
