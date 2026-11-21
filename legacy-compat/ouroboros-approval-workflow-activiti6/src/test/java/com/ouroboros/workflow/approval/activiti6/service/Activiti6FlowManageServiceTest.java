package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;

class Activiti6FlowManageServiceTest {
  @Test
  void offlineSuspendsProcessDefinitionWithoutSuspendingRunningInstances() {
    Activiti6FlowManageService service = new Activiti6FlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    service.repositoryService = repositoryService;

    assertTrue(service.offline("process-definition-existing").isSuccess());

    verify(repositoryService).suspendProcessDefinitionById(eq("process-definition-existing"), eq(false), isNull());
  }

  @Test
  void deployReusesLatestDefinitionWhenRawJsonOnlyDiffersByTrailingWhitespace() {
    Activiti6FlowManageService service = new Activiti6FlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream((trimTrailingWhitespace(v2FlowJson()) + "   ").getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployReusesLatestDefinitionWhenSemanticV2JsonOnlyDiffersByFieldOrder() {
    Activiti6FlowManageService service = new Activiti6FlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream(v2FlowJsonWithDifferentFieldOrder().getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService, never()).createDeployment();
  }

  @Test
  void deployReactivatesSuspendedDefinitionWhenReusingUnchangedLatestDefinition() {
    Activiti6FlowManageService service = new Activiti6FlowManageService();
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery latestDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery deploymentDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition latestDefinition = mock(ProcessDefinition.class);
    ProcessDefinition deployedDefinition = mock(ProcessDefinition.class);

    service.repositoryService = repositoryService;

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(latestDefinitionQuery, deploymentDefinitionQuery);
    when(latestDefinitionQuery.processDefinitionKey("flow-a")).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.orderByProcessDefinitionVersion()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.desc()).thenReturn(latestDefinitionQuery);
    when(latestDefinitionQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestDefinition));
    when(latestDefinition.getDeploymentId()).thenReturn("deployment-existing");
    when(repositoryService.getResourceAsStream("deployment-existing", "flowDefinition"))
        .thenReturn(new ByteArrayInputStream((trimTrailingWhitespace(v2FlowJson()) + "   ").getBytes(StandardCharsets.UTF_8)));
    when(deploymentDefinitionQuery.deploymentId("deployment-existing")).thenReturn(deploymentDefinitionQuery);
    when(deploymentDefinitionQuery.singleResult()).thenReturn(deployedDefinition);
    when(deployedDefinition.getId()).thenReturn("process-definition-existing");
    when(deployedDefinition.isSuspended()).thenReturn(true);

    String processDefinitionId = service.deploy("flow-a", v2FlowJson()).get();

    assertEquals("process-definition-existing", processDefinitionId);
    verify(repositoryService).activateProcessDefinitionById(eq("process-definition-existing"), eq(false), isNull());
    verify(repositoryService, never()).createDeployment();
  }

  private String v2FlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private String v2FlowJsonWithDifferentFieldOrder() {
    return """
        {
          "name": "采购审批",
          "formatVersion": 2,
          "id": "flow-a",
          "formKey": "purchase-form",
          "formType": "default",
          "description": "",
          "icon": "shenpi",
          "category": "",
          "nodes": [
            { "id": "start-a", "permission": [], "type": "发起节点", "name": "发起节点" },
            { "name": "经理审批", "actions": ["reject"], "subType": "单签", "permission": [], "candidates": [], "id": "approve-a", "type": "审批节点" }
          ],
          "edges": [
            { "targetId": "approve-a", "sourceId": "start-a", "type": "SEQUENCE", "id": "e1" }
          ]
        }
        """;
  }

  private String trimTrailingWhitespace(String value) {
    return value.replaceFirst("\\s+$", "");
  }
}
