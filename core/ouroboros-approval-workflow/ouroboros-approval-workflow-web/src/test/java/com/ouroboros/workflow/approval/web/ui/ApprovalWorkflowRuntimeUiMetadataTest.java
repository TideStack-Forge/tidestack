package com.ouroboros.workflow.approval.web.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

class ApprovalWorkflowRuntimeUiMetadataTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void runtimeDefinitionPagesExposeManagementSurfacesAndCreateAction() {
    String listSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition.ui-schema.json");
    String uiModel =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition.ui-model.json");
    String menuModel =
        readResource("META-INF/ouroboros/metadata/systemSetting/approvalWorkflowDefinition.menu.json");
    String appModules =
        readResource("META-INF/ouroboros/metadata/app-modules.json");
    String detailSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/detail.ui-schema.json");
    String detailUiModel =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/detail.ui-model.json");
    String editorSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/{id}.ui-schema.json");
    String addSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/add.ui-schema.json");
    String addUiModel =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/add.ui-model.json");
    String versionDetailSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/version-detail.ui-schema.json");
    String instanceDetailSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/instance-details/detail.ui-schema.json");
    String statisticsSchema =
        readResource("META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/statistics.ui-schema.json");
    String definitionAuthority =
        readResource("META-INF/ouroboros/metadata/systemSetting/approvalWorkflow/definition.authority.json");
    String statisticsAuthority =
        readResource("META-INF/ouroboros/metadata/systemSetting/approvalWorkflow/definition/statistics.authority.json");

    assertTrue(uiModel.contains("\"path\": \"/system-setting/approval-workflow-definition\""));
    assertTrue(uiModel.contains("\"title\": \"审批流\""));
    assertTrue(menuModel.contains("\"title\": \"审批流\""));
    assertTrue(menuModel.contains("\"icon\": \"icon-approval-workflow\""));
    assertTrue(appModules.contains("\"fullName\": \"systemSetting.approvalWorkflowDefinition\""));
    assertTrue(appModules.contains("\"title\": \"审批流\""));
    assertTrue(appModules.contains("\"icon\": \"icon-approval-workflow\""));
    assertTrue(listSchema.contains("\"label\": \"审批流统计\""));
    assertTrue(listSchema.contains("\"label\": \"新建审批流\""));
    assertTrue(listSchema.contains("/system-setting/approval-workflow-definition/add"));
    assertTrue(listSchema.contains("\"value\": \"updated\""));
    assertTrue(listSchema.contains("/system-setting/approval-workflow-definition/${name}"));
    assertTrue(listSchema.contains("/system-setting/approval-workflow-definition/detail?id=${name}"));
    assertTrue(listSchema.contains("\"api\": \"post:/api/approval-workflow/definition/${name}/deploy\""));
    assertTrue(listSchema.contains("\"disabledOn\": \"this.enabled === false\""));
    assertTrue(listSchema.contains("\"icon\": \"fa fa-upload\""));
    assertTrue(listSchema.contains("\"icon\": \"fa fa-power-off\""));
    assertTrue(listSchema.contains("\"icon\": \"fa fa-undo\""));
    assertTrue(listSchema.contains("post:/api/approval-workflow/definition/${name}/reset"));
    assertTrue(definitionAuthority.contains("POST /api/approval-workflow/definition/{id}/reset"));
    assertFalse(listSchema.contains("reset-to-design-time"));
    assertFalse(definitionAuthority.contains("reset-to-design-time"));
    assertFalse(listSchema.contains("来源元数据"));
    assertFalse(listSchema.contains("元数据层"));
    assertFalse(listSchema.contains("metadata-layer"));
    assertFalse(listSchema.contains("load-more"));
    assertFalse(listSchema.contains("\"label\": \"部署\""));
    assertFalse(listSchema.contains("\"label\": \"下线\""));
    JsonNode listSchemaJson = readJsonResource(
        "META-INF/ouroboros/metadata/system-setting/approval-workflow-definition.ui-schema.json");
    JsonNode createAction = findActionByLabel(listSchemaJson, "新建审批流");
    assertFalse(createAction.has("actionType"));
    JsonNode createActions = createAction.path("onEvent").path("click").path("actions");
    assertTrue(createActions.isArray());
    assertEquals("openTab", createActions.get(0).path("actionType").asText());
    assertEquals(
        "/system-setting/approval-workflow-definition/add",
        createActions.get(0).path("args").path("url").asText());
    JsonNode deployButton = findOperationButtonByApi(
        listSchemaJson,
        "post:/api/approval-workflow/definition/${name}/deploy");
    assertEquals("link", deployButton.path("level").asText());
    assertEquals("var(--color-success)", deployButton.path("style").path("color").asText());
    JsonNode offlineButton = findOperationButtonByApi(
        listSchemaJson,
        "post:/api/approval-workflow/definition/${name}/offline");
    assertEquals("link", offlineButton.path("level").asText());
    assertEquals("var(--color-warning)", offlineButton.path("style").path("color").asText());
    JsonNode resetButton = findOperationButtonByApi(
        listSchemaJson,
        "post:/api/approval-workflow/definition/${name}/reset");
    assertEquals("link", resetButton.path("level").asText());
    assertEquals("this.canReset === true", resetButton.path("visibleOn").asText());
    JsonNode versionHistoryButton = findOperationButtonByIcon(listSchemaJson, "fa fa-history");
    JsonNode versionHistoryActions = versionHistoryButton.path("drawer").path("actions");
    assertTrue(versionHistoryActions.isArray());
    assertEquals("close", versionHistoryActions.get(0).path("actionType").asText());
    assertEquals("关闭", versionHistoryActions.get(0).path("label").asText());
    assertFalse(listSchema.contains("deploy=true"));
    assertFalse(listSchema.contains("/system-setting/approval-workflow-definition/editor?id=${id}"));
    assertFalse(listSchema.contains("/system-setting/approval-workflow-definition/editor?mode=preview&id=${id}"));

    assertTrue(addUiModel.contains("\"path\": \"/system-setting/approval-workflow-definition/add\""));
    assertTrue(addUiModel.contains("\"title\": \"新建审批流\""));
    assertTrue(addSchema.contains("\"url\": \"/api/approval-workflow/definition\""));
    assertTrue(addSchema.contains("\"method\": \"post\""));
    JsonNode createForm = readJsonResource(
        "META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/add.ui-schema.json")
        .path("schema").path("body").get(0);
    JsonNode formType = createForm.findParents("name").stream()
        .filter(item -> "formType".equals(item.path("name").asText()))
        .findFirst().orElseThrow();
    JsonNode formId = createForm.findParents("name").stream()
        .filter(item -> "formId".equals(item.path("name").asText()))
        .findFirst().orElseThrow();
    assertEquals("default", formType.path("value").asText());
    assertEquals("/api/approval-workflow/base-info/form-types", formType.path("source").path("url").asText());
    assertEquals("/api/approval-workflow/base-info/form/${formType}", formId.path("source").path("url").asText());
    assertEquals("${!!formType}", formId.path("source").path("sendOn").asText());
    assertEquals("${formType}", createForm.path("api").path("data").path("form").path("formType").asText());
    assertEquals("${formId}", createForm.path("api").path("data").path("form").path("formId").asText());
    assertTrue(addSchema.contains("/system-setting/approval-workflow-definition/${name}"));

    assertTrue(detailUiModel.contains("\"path\": \"/system-setting/approval-workflow-definition/detail\""));
    assertTrue(detailUiModel.contains("\"title\": \"审批流详情\""));
    assertTrue(detailSchema.contains("/api/approval-workflow/definition/${id}"));
    assertTrue(detailSchema.contains("\"type\": \"workflow-diagram-viewer\""));
    assertTrue(detailSchema.contains("\"definition\": \"$$\""));
    assertFalse(detailSchema.contains("workflow-editor"));
    assertFalse(detailSchema.contains("workflow-chart"));

    assertTrue(editorSchema.contains("\"basicInfoFormItems\""));
    assertFalse(editorSchema.contains("\"basicInfoSchema\""));
    assertFalse(editorSchema.contains("\"basicInfoButtonLabel\""));
    assertFalse(editorSchema.contains("\"basicInfoButtonIcon\""));
    assertFalse(editorSchema.contains("\"basicInfoOpenMode\""));
    assertTrue(editorSchema.contains("\"label\": \"流程标识\""));
    assertTrue(editorSchema.contains("\"label\": \"流程名称\""));
    assertTrue(editorSchema.contains("\"label\": \"流程描述\""));
    JsonNode runtimeEditor = findWorkflowEditor(readJsonResource(
        "META-INF/ouroboros/metadata/system-setting/approval-workflow-definition/{id}.ui-schema.json"));
    assertBusinessModelBoundFieldLocked(runtimeEditor, "name");
    assertBusinessModelBoundFieldLocked(runtimeEditor, "formType");
    assertBusinessModelBoundFieldLocked(runtimeEditor, "formId");
    assertFalse(findBasicInfoFormItem(runtimeEditor, "title").has("disabledOn"));
    assertFalse(findBasicInfoFormItem(runtimeEditor, "description").has("disabledOn"));
    assertTrue(editorSchema.contains("\"formFieldSourceApi\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/approval-workflow/base-info/form/${flowDefinition.form.formType}/${flowDefinition.form.formId}/workflow-field\""));
    assertFalse(editorSchema.contains("\"conditionFieldSourceApi\""));
    assertFalse(editorSchema.contains("\"formPermissionFieldSourceApi\""));
    assertTrue(editorSchema.contains("\"candidateSourceApi\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/approval-workflow/candidate/${candidateName}/items\""));

    assertTrue(versionDetailSchema.contains("/api/approval-workflow/definition/${flowKey}/versions/${version}"));
    assertFalse(versionDetailSchema.contains("/api/approval-workflow/definition/${flowKey}/versions/${version}/diagram"));
    assertTrue(versionDetailSchema.contains("${deploymentTime|date:YYYY-MM-DD HH\\\\:mm\\\\:ss :YYYY-MM-DDTHH\\\\:mm\\\\:ss}"));
    assertTrue(versionDetailSchema.contains("\"type\": \"workflow-diagram-viewer\""));
    assertTrue(versionDetailSchema.contains("\"definition\": \"${definition}\""));
    assertFalse(versionDetailSchema.contains("\"activeNodeIds\""));
    assertFalse(versionDetailSchema.contains("workflow-editor"));
    assertFalse(versionDetailSchema.contains("workflow-chart"));

    assertTrue(instanceDetailSchema.contains("/api/approval-workflow/instance/${instanceId}/diagram"));
    assertTrue(instanceDetailSchema.contains("/api/approval-workflow/instance/${instanceId}/timeline"));
    assertTrue(instanceDetailSchema.contains("\"type\": \"workflow-diagram-viewer\""));
    assertTrue(instanceDetailSchema.contains("\"activeNodeIds\": \"${activeNodeIds}\""));
    assertFalse(instanceDetailSchema.contains("workflow-editor"));
    assertFalse(instanceDetailSchema.contains("workflow-chart"));

    assertTrue(statisticsSchema.contains("/api/approval-workflow/statistics/duration"));
    assertTrue(statisticsSchema.contains("\"source\": \"${nodes}\""));
    assertTrue(statisticsSchema.contains("\"source\": \"${approvers}\""));
    assertTrue(statisticsAuthority.contains("GET /api/approval-workflow/statistics/duration"));
  }

  @Test
  void approvalCenterAdvancedActionDialogsShouldExistAndPointToCanonicalTaskActionSurface() {
    String approvalCenterSchema =
        readResource("META-INF/ouroboros/metadata/approval-center.ui-schema.json");
    String transferDialogSchema =
        readResource("META-INF/ouroboros/metadata/approval-center/transfer-form.ui-schema.json");
    String addSignDialogSchema =
        readResource("META-INF/ouroboros/metadata/approval-center/add-sign-form.ui-schema.json");
    String freeRejectDialogSchema =
        readResource("META-INF/ouroboros/metadata/approval-center/free-reject-form.ui-schema.json");
    String freeApprovalDialogSchema =
        readResource("META-INF/ouroboros/metadata/approval-center/free-approval-form.ui-schema.json");
    String centerAuthority =
        readResource("META-INF/ouroboros/metadata/approvalWorkflow/center.authority.json");
    String startAuthority =
        readResource("META-INF/ouroboros/metadata/approvalWorkflow/center/start.authority.json");
    String transferDialogModel =
        readResource("META-INF/ouroboros/metadata/approval-center/transfer-form.ui-model.json");
    String addSignDialogModel =
        readResource("META-INF/ouroboros/metadata/approval-center/add-sign-form.ui-model.json");
    String freeRejectDialogModel =
        readResource("META-INF/ouroboros/metadata/approval-center/free-reject-form.ui-model.json");
    String freeApprovalDialogModel =
        readResource("META-INF/ouroboros/metadata/approval-center/free-approval-form.ui-model.json");

    assertTrue(approvalCenterSchema.contains("/approval-center/transfer-form"));
    assertTrue(approvalCenterSchema.contains("/approval-center/add-sign-form"));
    assertTrue(approvalCenterSchema.contains("/approval-center/free-reject-form"));
    assertTrue(approvalCenterSchema.contains("/approval-center/free-approval-form"));

    assertTrue(transferDialogSchema.contains("/api/approval-workflow/task/${id}/transfer-targets"));
    assertTrue(transferDialogSchema.contains("\"action\": \"transfer\""));
    assertTrue(transferDialogSchema.contains("\"name\": \"transfer.targetUserId\""));
    assertTrue(transferDialogModel.contains("\"path\": \"/approval-center/transfer-form\""));

    assertTrue(addSignDialogSchema.contains("/api/approval-workflow/task/${id}/add-sign-targets"));
    assertTrue(addSignDialogSchema.contains("\"action\": \"addSign\""));
    assertTrue(addSignDialogSchema.contains("\"name\": \"addSign.targetUserIds\""));
    assertTrue(addSignDialogModel.contains("\"path\": \"/approval-center/add-sign-form\""));

    assertTrue(freeRejectDialogSchema.contains("/api/approval-workflow/task/${id}/free-reject-targets"));
    assertTrue(freeRejectDialogSchema.contains("\"action\": \"freeReject\""));
    assertTrue(freeRejectDialogSchema.contains("\"name\": \"freeReject.targetNodeId\""));
    assertTrue(freeRejectDialogModel.contains("\"path\": \"/approval-center/free-reject-form\""));

    assertTrue(freeApprovalDialogSchema.contains("/api/approval-workflow/task/${id}/free-approval-targets"));
    assertTrue(freeApprovalDialogSchema.contains("\"action\": \"freeApprove\""));
    assertTrue(freeApprovalDialogSchema.contains("\"name\": \"freeApproval.consultUserIds\""));
    assertTrue(freeApprovalDialogModel.contains("\"path\": \"/approval-center/free-approval-form\""));

    assertAuthorityPatternCoversDialogUrl(
        centerAuthority,
        transferDialogSchema,
        "GET /api/approval-workflow/task/{taskId}/transfer-targets",
        "/api/approval-workflow/task/${id}/transfer-targets");
    assertAuthorityPatternCoversDialogUrl(
        centerAuthority,
        addSignDialogSchema,
        "GET /api/approval-workflow/task/{taskId}/add-sign-targets",
        "/api/approval-workflow/task/${id}/add-sign-targets");
    assertAuthorityPatternCoversDialogUrl(
        centerAuthority,
        freeRejectDialogSchema,
        "GET /api/approval-workflow/task/{taskId}/free-reject-targets",
        "/api/approval-workflow/task/${id}/free-reject-targets");
    assertAuthorityPatternCoversDialogUrl(
        centerAuthority,
        freeApprovalDialogSchema,
        "GET /api/approval-workflow/task/{taskId}/free-approval-targets",
        "/api/approval-workflow/task/${id}/free-approval-targets");

    assertTrue(startAuthority.contains("POST /api/approval-workflow/instance/{id}/start-requirements"));
    assertFalse(startAuthority.contains("transfer-targets"));
    assertFalse(startAuthority.contains("add-sign-targets"));
    assertFalse(startAuthority.contains("free-reject-targets"));
    assertFalse(startAuthority.contains("free-approval-targets"));
  }

  private void assertAuthorityPatternCoversDialogUrl(
      String authority,
      String dialogSchema,
      String authorityApi,
      String dialogUrl
  ) {
    assertTrue(authority.contains(authorityApi), "authority should contain " + authorityApi);
    assertTrue(dialogSchema.contains(dialogUrl), "dialog should contain " + dialogUrl);

    String authorityPath = authorityApi.substring(authorityApi.indexOf(' ') + 1);
    String concreteDialogPath = dialogUrl.replace("${id}", "task-1");
    assertTrue(
        PathPatternParser.defaultInstance.parse(authorityPath).matches(PathContainer.parsePath(concreteDialogPath)),
        "authority pattern should cover dialog URL with a concrete task id");
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  private JsonNode readJsonResource(String resourcePath) {
    try {
      return OBJECT_MAPPER.readTree(readResource(resourcePath));
    } catch (Exception e) {
      throw new AssertionError("failed to parse resource as json: " + resourcePath, e);
    }
  }

  private JsonNode findWorkflowEditor(JsonNode node) {
    JsonNode workflowEditor = findWorkflowEditorOrNull(node);
    assertNotNull(workflowEditor, "workflow-editor should exist");
    return workflowEditor;
  }

  private JsonNode findOperationButtonByApi(JsonNode schema, String api) {
    JsonNode button = findOperationButtonByApiOrNull(schema, api);
    assertNotNull(button, "operation button should exist for api: " + api);
    return button;
  }

  private JsonNode findOperationButtonByIcon(JsonNode schema, String icon) {
    JsonNode button = findOperationButtonByIconOrNull(schema, icon);
    assertNotNull(button, "operation button should exist for icon: " + icon);
    return button;
  }

  private JsonNode findActionByLabel(JsonNode schema, String label) {
    JsonNode action = findActionByLabelOrNull(schema, label);
    assertNotNull(action, "action should exist for label: " + label);
    return action;
  }

  private JsonNode findActionByLabelOrNull(JsonNode node, String label) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (node.isObject() && "action".equals(node.path("type").asText()) && label.equals(node.path("label").asText())) {
      return node;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        JsonNode action = findActionByLabelOrNull(child, label);
        if (action != null) {
          return action;
        }
      }
    }
    return null;
  }

  private JsonNode findOperationButtonByApiOrNull(JsonNode node, String api) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (node.isObject() && "button".equals(node.path("type").asText()) && api.equals(node.path("api").asText())) {
      return node;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        JsonNode button = findOperationButtonByApiOrNull(child, api);
        if (button != null) {
          return button;
        }
      }
    }
    return null;
  }

  private JsonNode findOperationButtonByIconOrNull(JsonNode node, String icon) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (node.isObject() && "button".equals(node.path("type").asText()) && icon.equals(node.path("icon").asText())) {
      return node;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        JsonNode button = findOperationButtonByIconOrNull(child, icon);
        if (button != null) {
          return button;
        }
      }
    }
    return null;
  }

  private JsonNode findWorkflowEditorOrNull(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (node.isObject() && "workflow-editor".equals(node.path("type").asText())) {
      return node;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        JsonNode workflowEditor = findWorkflowEditorOrNull(child);
        if (workflowEditor != null) {
          return workflowEditor;
        }
      }
    }
    return null;
  }

  private JsonNode findBasicInfoFormItem(JsonNode workflowEditor, String name) {
    for (JsonNode item : workflowEditor.path("basicInfoFormItems")) {
      if (name.equals(item.path("name").asText())) {
        return item;
      }
    }
    throw new AssertionError("basic info form item should exist: " + name);
  }

  private void assertBusinessModelBoundFieldLocked(JsonNode workflowEditor, String name) {
    assertEquals(
        "${formType === 'business-model'}",
        findBasicInfoFormItem(workflowEditor, name).path("disabledOn").asText(),
        name + " should be locked for business-model bound runtime definitions");
  }
}
