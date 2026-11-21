package com.ouroboros.workflow.approval.web.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata.FormBinding;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeType;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

class WorkflowModelResultTest {

  @Test
  void resultSerializesCanonicalDefinitionFieldsAndRuntimeManagementFields() {
    var model = new WorkflowModel();
    model.setId("purchase-approval");
    model.setName("采购审批");
    model.setStatus("draft");
    model.setProcessDefinitionId("process-1");
    model.setSource(WorkflowModel.SOURCE_METADATA_LAYER);
    model.setAdopted(true);
    model.setCreator("alice");
    model.setSaveAt(LocalDateTime.of(2026, 8, 13, 9, 30));
    model.setCategory("finance");
    model.setIcon("approval");
    ApprovalWorkflowDefinitionMetadata metadata = new ApprovalWorkflowDefinitionMetadata();
    metadata.setName("purchase-approval");
    metadata.setTitle("采购审批");
    metadata.setDescription("采购申请审批");
    metadata.setVersion(3);
    metadata.setEnabled(false);
    FormBinding form = new FormBinding();
    form.setFormType("default");
    form.setFormId("purchase-form");
    metadata.setForm(form);
    Notification notification = new Notification();
    notification.setTaskCreatedTemplate("task-created-template");
    metadata.setNotification(notification);
    metadata.setEditorLayout(Json.toBean(JsonNode.class, "{\"canvas\":{\"zoom\":1.25}}"));
    StartNode startNode = new StartNode();
    startNode.setId("start");
    startNode.setName("开始");
    metadata.setNodes(List.of(startNode));
    FlowEdgeV2 edge = new FlowEdgeV2();
    edge.setId("edge-1");
    edge.setType(FlowEdgeType.SEQUENCE);
    edge.setSourceId("start");
    edge.setTargetId("end");
    metadata.setEdges(List.of(edge));

    var result = new WorkflowModelResult(model, metadata);
    JsonNode json = Json.toBean(JsonNode.class, Json.toJsonString(result));

    Set.of(
        "formatVersion", "name", "title", "description", "version", "enabled", "form",
        "notification", "editorLayout", "nodes", "edges"
    ).forEach(field -> assertTrue(json.has(field), "missing canonical field: " + field));
    Set.of(
        "status", "statusName", "processDefinitionId", "source", "adopted", "creator", "saveAt",
        "category", "icon"
    ).forEach(field -> assertTrue(json.has(field), "missing runtime field: " + field));

    assertEquals("purchase-approval", json.path("name").asText());
    assertEquals("采购审批", json.path("title").asText());
    assertEquals(3, json.path("version").asInt());
    assertFalse(json.path("enabled").asBoolean());
    assertEquals("default", json.path("form").path("formType").asText());
    assertEquals("purchase-form", json.path("form").path("formId").asText());
    assertEquals("task-created-template", json.path("notification").path("taskCreatedTemplate").asText());
    assertEquals(1.25, json.path("editorLayout").path("canvas").path("zoom").asDouble());
    assertEquals("start", json.path("nodes").get(0).path("id").asText());
    assertEquals("edge-1", json.path("edges").get(0).path("id").asText());
    assertEquals("start", json.path("edges").get(0).path("sourceId").asText());
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, json.path("source").asText());
    assertTrue(json.path("adopted").asBoolean());

    assertFalse(json.has("id"));
    assertFalse(json.has("flowKey"));
    assertFalse(json.has("formType"));
    assertFalse(json.has("formId"));
    assertFalse(json.has("formKey"));
  }
}
