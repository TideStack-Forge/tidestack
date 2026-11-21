package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.web.user.CandidateUserResolverRegistry;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

import io.vavr.control.Try;

class ApprovalWorkflowVersionHistoryControllerTest {

  @Test
  void shouldExposeDefinitionVersionHistoryEndpointWithContractFields() {
    ApprovalWorkflowDefinitionController controller = newDefinitionController();
    FlowDefinitionVersion latest = definitionVersion(2, "flow-a:2:200", "deployment-200", "bob");
    FlowDefinitionVersion older = definitionVersion(1, "flow-a:1:100", "deployment-100", "alice");

    when(controller.flowDefinitionService.listFlowDefinitionVersions("flow-a"))
        .thenReturn(Try.success(Arrays.asList(latest, older)));

    WebApiResult<?> result = controller.listVersions("flow-a");
    JsonNode json = toJson(result);

    assertEquals(0, json.path("code").asInt());
    assertTrue(json.path("data").isArray());
    JsonNode first = json.path("data").get(0);
    assertTrue(first.has("version"));
    assertTrue(first.has("processDefinitionId"));
    assertTrue(first.has("deploymentId"));
    assertTrue(first.has("deploymentTime"));
    assertTrue(first.has("deployedBy"));
  }

  @Test
  void shouldExposeDefinitionVersionDetailEndpointWithContractFields() {
    ApprovalWorkflowDefinitionController controller = newDefinitionController();
    FlowDefinitionVersion version = definitionVersion(1, "flow-a:1:100", "deployment-100", "alice");
    version.setDefinition(v2FlowJson());

    when(controller.flowDefinitionService.getFlowDefinitionVersion("flow-a", 1)).thenReturn(Try.success(version));
    when(controller.flowDefinitionService.listFlowDefinitionVersions("flow-a"))
        .thenReturn(Try.success(Collections.singletonList(version)));

    WebApiResult<?> result = controller.getVersion("flow-a", 1);
    JsonNode json = toJson(result);

    assertEquals(0, json.path("code").asInt());
    JsonNode data = json.path("data");
    assertTrue(data.has("version"));
    assertTrue(data.has("processDefinitionId"));
    assertTrue(data.has("definition"));
  }

  @Test
  void shouldExposeDefinitionDiagramEndpointWithContractFields() {
    ApprovalWorkflowDefinitionController controller = newDefinitionController();
    JsonNode definition = Json.toBean(JsonNode.class, v2FlowJson());

    when(controller.flowDefinitionService.getFlowDefinitionV2("flow-a", 1)).thenReturn(Try.success(definition));

    WebApiResult<?> result = controller.getDefinitionDiagram("flow-a", 1);
    JsonNode json = toJson(result);

    assertEquals(0, json.path("code").asInt());
    JsonNode data = json.path("data");
    assertTrue(data.has("definition"));
    assertTrue(data.path("nodes").isArray());
    assertTrue(data.path("edges").isArray());
  }

  @Test
  void shouldExposeInstanceDiagramEndpointWithContractFields() {
    ApprovalWorkflowInstanceController controller = newInstanceController();
    JsonNode definition = Json.toBean(JsonNode.class, v2FlowJson());

    when(controller.flowInstanceService.getInstanceFlowDefinitionV2("instance-a")).thenReturn(Try.success(definition));
    when(controller.flowInstanceService.getActiveActivityIds("instance-a")).thenReturn(Collections.singletonList("approve-a"));

    WebApiResult<?> result = controller.instanceDiagram("instance-a");
    JsonNode json = toJson(result);

    assertEquals(0, json.path("code").asInt());
    JsonNode data = json.path("data");
    assertTrue(data.has("definition"));
    assertTrue(data.path("nodes").isArray());
    assertTrue(data.path("edges").isArray());
    assertTrue(data.path("activeNodeIds").isArray());
  }

  private ApprovalWorkflowDefinitionController newDefinitionController() {
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.flowDefinitionService = mock(FlowDefinitionService.class);
    return controller;
  }

  private ApprovalWorkflowInstanceController newInstanceController() {
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = mock(FlowInstanceService.class);
    controller.flowDefinitionService = mock(FlowDefinitionService.class);
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    return controller;
  }

  private FlowDefinitionVersion definitionVersion(int version, String processDefinitionId, String deploymentId, String deployedBy) {
    FlowDefinitionVersion result = new FlowDefinitionVersion();
    result.setVersion(version);
    result.setProcessDefinitionId(processDefinitionId);
    result.setDeploymentId(deploymentId);
    result.setDeploymentTime(LocalDateTime.of(2026, 5, 1, 10, 30).plusHours(version));
    result.setDeployedBy(deployedBy);
    return result;
  }

  private JsonNode toJson(Object value) {
    return Json.toBean(JsonNode.class, Json.toJsonString(value));
  }

  private String v2FlowJson() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },",
        "    { \"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"end-a\", \"end\": { \"content\": \"done\", \"channel\": [\"message\"], \"candidates\": [] } }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }
}
