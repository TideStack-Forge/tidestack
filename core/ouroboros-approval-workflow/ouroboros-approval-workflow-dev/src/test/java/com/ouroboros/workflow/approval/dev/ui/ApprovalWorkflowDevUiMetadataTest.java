package com.ouroboros.workflow.approval.dev.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApprovalWorkflowDevUiMetadataTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void devUiKeepsDefinitionManagementAsVisibleSurfaceOnly() {
    String addSchema =
        readResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/add.ui-schema.json");
    String listSchema =
        readResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-schema.json");
    String listModel =
        readResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-model.json");
    String devMenu =
        readResource("META-INF/ouroboros/metadata/BusinessLogic.ApprovalWorkflowDefinition.dev-menu.json");

    assertTrue(devMenu.contains("\"title\": \"审批流\""));
    assertTrue(devMenu.contains("\"icon\": \"icon-approval-workflow\""));
    assertTrue(devMenu.contains("\"url\": \"/dev/{appName}/approval-workflow-definition\""));
    assertTrue(devMenu.contains("\"placement\": \"main\""));
    assertTrue(devMenu.contains("\"stage\": \"dev\""));
    assertTrue(listModel.contains("\"title\": \"审批流\""));
    assertTrue(listSchema.contains("\"name\": \"审批流\""));
    assertFalse(listSchema.contains("流程定义库"));
    assertTrue(listSchema.contains("\"label\": \"流程标识\""));
    assertTrue(listSchema.contains("\"label\": \"新建\""));
    assertFalse(listSchema.contains("\"label\": \"新建定义\""));
    assertTrue(listSchema.contains("\"title\": \"新建流程定义\""));
    assertTrue(listSchema.contains("\"name\": \"create-workflow-definition-management-form\""));
    assertTrue(listSchema.contains("\"label\": \"创建并编辑\""));
    assertTrue(listSchema.contains("/approval-workflow-definition/${name}"));
    assertTrue(listSchema.contains("\"type\": \"EndNode\""));
    assertTrue(listSchema.contains("\"id\": \"${name}-end\""));
    assertFalse(listSchema.contains("\"type\": \"通知节点\""));
    assertTrue(listSchema.contains("\"formatVersion\": 1"));
    assertTrue(listSchema.contains("\"title\": \"${title}\""));
    assertTrue(listSchema.contains("\"formId\": \"${formId}\""));
    assertFalse(listSchema.contains("\"formatVersion\": 2"));
    assertFalse(listSchema.contains("\"id\": \"${definitionId}\""));
    assertTrue(listSchema.contains("\"&\": \"$model\""));
    assertTrue(listSchema.contains("/approval-workflow-definition/${id}?mode=preview"));
    assertTrue(listSchema.contains("\"title\": \"编辑审批流「${title || name || id}」\""));
    assertFalse(listSchema.contains("/approval-workflow-definition/editor?id=${id}"));
    assertFalse(listSchema.contains("/approval-workflow-definition/add"));
    assertFalse(listSchema.contains("/approval-workflow-definition/version-history"));
    assertFalse(listSchema.contains("/approval-workflow-definition/statistics"));
    assertFalse(listSchema.contains("\"label\": \"部署\""));
    assertFalse(listSchema.contains("\"label\": \"下线\""));
    assertFalse(listSchema.contains("\"title\": \"定义筛选\""));
    assertFalse(listSchema.contains("\"type\": \"export-excel\""));
    assertFalse(listSchema.contains("\"load-more\""));
    assertFalse(listSchema.contains("\"filter-toggler\""));
    assertFalse(listSchema.contains("\"filterTogglable\": true"));
    assertTrue(listSchema.contains("\"actionType\": \"reload\""));
    assertFalse(listSchema.contains("\"label\": \"流程状态\""));
    assertFalse(listSchema.contains("\"label\": \"流程分类\""));
    assertTrue(listSchema.contains("\"label\": \"关联表单\""));
    assertTrue(listSchema.contains("\"formType\": \"${form.formType}\""));
    assertTrue(listSchema.contains("\"formKey\": \"${form.formId}\""));
    assertTrue(listSchema.contains("\"definitionId\": \"${name}\""));
    assertTrue(listSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow-definition\""));
    assertFalse(listSchema.contains("\"url\": \"/api/approval-workflow/definition\""));
    assertTrue(listSchema.contains("\"tpl\": \"${boundFormSummary || '尚未关联表单'}\""));
    assertFalse(listSchema.contains("默认使用此流程的表单"));
    assertFalse(listSchema.contains("defaultBoundFormSummary"));
    assertFalse(listSchema.contains("defaultBoundForms"));

    assertTrue(addSchema.contains("兼容入口"));
    assertTrue(addSchema.contains("\"label\": \"创建并进入编辑器\""));
    assertTrue(addSchema.contains("\"title\": \"编辑审批流「${title}」\""));
    assertTrue(addSchema.contains("\"name\": \"create-approval-workflow-definition-form\""));
    assertTrue(addSchema.contains("\"name\": \"name\""));
    assertTrue(addSchema.contains("\"name\": \"title\""));
    assertTrue(addSchema.contains("\"name\": \"description\""));
    assertTrue(addSchema.contains("\"formatVersion\": 1"));
    assertTrue(addSchema.contains("\"formId\": \"${formId}\""));
    assertTrue(addSchema.contains("\"type\": \"EndNode\""));
    assertTrue(addSchema.contains("\"id\": \"${name}-end\""));
    assertFalse(addSchema.contains("\"name\": \"definitionId\""));
    assertFalse(addSchema.contains("\"formatVersion\": 2"));
    assertFalse(addSchema.contains("\"id\": \"${definitionId}\""));
    assertFalse(addSchema.contains("\"type\": \"通知节点\""));
    assertTrue(addSchema.contains("bindingWritebackSuggested"));
    assertFalse(addSchema.contains("\"type\": \"workflow-editor\""));

    String editorSchema =
        readResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/{id}.ui-schema.json");
    String editorModel =
        readResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/editor.ui-model.json");
    assertTrue(editorModel.contains("\"title\": \"编辑审批流\""));
    assertTrue(editorSchema.contains("\"title\": \"编辑审批流「${id}」\""));
    assertTrue(editorSchema.contains("\"basicInfoFormItems\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev-fragment-schema/vcs-buttons\""));
    assertTrue(editorSchema.contains("\"apiPath\": \"approval-workflow-definition/${id}\""));
    assertTrue(editorSchema.contains("\"statusId\": \"approvalWorkflowStatus\""));
    assertTrue(editorSchema.contains("\"formId\": \"edit-workflow-model-form\""));
    assertTrue(editorSchema.contains("\"actionType\": \"static\""));
    assertTrue(editorSchema.contains("\"editDisabledOn\": \"mode === 'preview'\""));
    assertTrue(editorSchema.contains("\"id\": \"edit-workflow-model-form\""));
    assertTrue(editorSchema.contains("\"className\": \"h-full\""));
    assertTrue(editorSchema.contains("\"bodyClassName\": \"h-full flex flex-col\""));
    assertTrue(editorSchema.contains("\"label\": false"));
    assertTrue(editorSchema.contains("\"className\": \"h-full flex-grow\""));
    assertTrue(editorSchema.contains("\"inputClassName\": \"h-full flex-grow\""));
    assertTrue(editorSchema.contains("\"actionType\": \"updateTabTitle\""));
    assertFalse(editorSchema.contains("\".cxd-Page-toolbar\""));
    assertFalse(editorSchema.contains("\".approval-workflow-editor-form\""));
    assertFalse(editorSchema.contains("\".approval-workflow-editor-canvas\""));
    assertTrue(editorSchema.contains("\"title\": \"编辑审批流「${flowDefinition.title || flowDefinition.name || id}」\""));
    assertTrue(editorSchema.contains("\"submitSucc\""));
    assertTrue(editorSchema.contains("\"flowDefinition\": \"${result.data.flowDefinition}\""));
    assertTrue(editorSchema.contains("\"version\": \"${result.data.version}\""));
    assertTrue(editorSchema.contains("\"definitionId\": \"${result.data.name || flowDefinition.name || id}\""));
    assertTrue(editorSchema.contains("\"definitionName\": \"${result.data.title || flowDefinition.title}\""));
    assertTrue(editorSchema.contains("\"title\": \"编辑审批流「${result.data.title || flowDefinition.title || flowDefinition.name || id}」\""));
    assertFalse(editorSchema.contains("\"label\": \"取消\""));
    assertFalse(editorSchema.contains("\"actionType\": \"closeTab\""));
    assertFalse(editorSchema.contains("\"actionType\": \"closeCurrentTab\""));
    assertFalse(editorSchema.contains("\"basicInfoSchema\""));
    assertFalse(editorSchema.contains("\"basicInfoButtonLabel\""));
    assertFalse(editorSchema.contains("\"basicInfoButtonIcon\""));
    assertFalse(editorSchema.contains("\"basicInfoOpenMode\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow-definition/${id}\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow-definition/${id}/init-form\""));
    assertFalse(editorSchema.contains("\"url\": \"/api/approval-workflow/definition/${id}\""));
    assertFalse(editorSchema.contains("\"url\": \"/api/approval-workflow/definition/${id}?deploy=false\""));
    assertTrue(editorSchema.contains("\"formFieldSourceApi\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/base-info/workflow-field\""));
    assertTrue(editorSchema.contains("\"formType\": \"${flowDefinition.form.formType}\""));
    assertTrue(editorSchema.contains("\"formKey\": \"${flowDefinition.form.formId}\""));
    assertTrue(editorSchema.contains("\"definitionId\": \"${flowDefinition.name}\""));
    assertTrue(editorSchema.contains("\"candidateSourceApi\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/candidate\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/candidate/${candidateName}/items\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/candidate/${candidateName}/category\""));
    assertFalse(editorSchema.contains("\"url\": \"/api/approval-workflow/candidate"));
    assertFalse(editorSchema.contains("\"conditionFieldSourceApi\""));
    assertFalse(editorSchema.contains("\"formPermissionFieldSourceApi\""));
    assertFalse(editorSchema.contains("\"candidateFieldSourceApi\""));
    assertFalse(editorSchema.contains("\"assigneeFieldSourceApi\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/base-info/workflow-field\""));
    assertTrue(editorSchema.contains("\"basicInfoDataSourceApi\""));
    assertFalse(editorSchema.contains("\"formType\": \"${formType}\""));
    assertFalse(editorSchema.contains("\"formKey\": \"${formKey}\""));
    assertFalse(editorSchema.contains("\"definitionId\": \"${id}\""));
    assertTrue(editorSchema.contains("\"appName\": \"${appName}\""));
    assertTrue(editorSchema.contains("\"label\": \"流程标识\""));
    assertTrue(editorSchema.contains("\"label\": \"流程名称\""));
    assertTrue(editorSchema.contains("\"label\": \"流程描述\""));
    assertTrue(editorSchema.contains("\"label\": \"关联表单\""));
    assertTrue(editorSchema.contains("\"type\": \"select\""));
    assertTrue(editorSchema.contains("\"name\": \"formId\""));
    assertTrue(editorSchema.contains("\"url\": \"/api/dev/${appName}/approval-workflow/base-info/form/${formType}\""));
    assertTrue(editorSchema.contains("\"sendOn\": \"${!!formType}\""));
    assertTrue(editorSchema.contains("\"labelField\": \"title\""));
    assertTrue(editorSchema.contains("\"valueField\": \"name\""));
    assertTrue(editorSchema.contains("\"searchable\": true"));
    assertFalse(editorSchema.contains("\"definitionId\": \"${flowDefinition.id || id}\""));
    assertFalse(editorSchema.contains("默认使用此流程的表单"));
    assertFalse(editorSchema.contains("defaultBoundFormSummary"));
    assertFalse(editorSchema.contains("defaultBoundForms"));
    assertFalse(editorSchema.contains("\"modelKey\": \"${modelKey}\""));
    assertFalse(editorSchema.contains("业务模型上下文"));
    assertFalse(editorSchema.contains("\"className\": \"m-3 mb-0\""));
    assertFalse(editorSchema.contains("当前编辑的是默认审批流程定义"));

    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-model.json");
    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/add.ui-model.json");
    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/editor.ui-model.json");
    assertResourceExists("META-INF/ouroboros/metadata/BusinessLogic.ApprovalWorkflowDefinition.dev-menu.json");
    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-schema.json");
    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/add.ui-schema.json");
    assertResourceExists("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/{id}.ui-schema.json");
    assertValidJson("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-schema.json");
    assertValidJson("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/add.ui-schema.json");
    assertValidJson("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/{id}.ui-schema.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/statistics.ui-model.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/version-history.ui-model.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/version-detail.ui-model.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/instance-details.ui-model.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/instance-details/detail.ui-model.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/statistics.ui-schema.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/version-history.ui-schema.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/version-detail.ui-schema.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/instance-details.ui-schema.json");
    assertResourceMissing("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/instance-details/detail.ui-schema.json");
  }

  @Test
  void devCreateAndBaseInfoFormsBindSelectedFormType() {
    JsonNode addSchema =
        readJsonResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/add.ui-schema.json");
    JsonNode listSchema =
        readJsonResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition.ui-schema.json");
    JsonNode editorSchema =
        readJsonResource("META-INF/ouroboros/metadata/dev/{appName}/approval-workflow-definition/{id}.ui-schema.json");

    assertCreateFormBindsSelectedForm(
        findObjectByName(addSchema, "create-approval-workflow-definition-form"),
        findCreateDefinitionData(addSchema));
    assertCreateActionUsesAmisErrorContract(addSchema);
    assertCreateFormBindsSelectedForm(
        findObjectByName(listSchema, "create-workflow-definition-management-form"),
        findCreateDefinitionData(listSchema));
    assertCreateActionUsesAmisErrorContract(listSchema);

    JsonNode workflowEditor = findObjectByType(editorSchema, "workflow-editor");
    JsonNode basicInfoItems = workflowEditor.path("basicInfoFormItems");
    JsonNode basicInfoFormType = findArrayItemByName(basicInfoItems, "formType");
    JsonNode basicInfoFormId = findArrayItemByName(basicInfoItems, "formId");

    assertSelectableFormBinding(basicInfoFormType, basicInfoFormId);
    assertEquals("${!!formKey}", basicInfoFormType.path("disabledOn").asText());
    assertEquals("${!!formKey}", basicInfoFormId.path("disabledOn").asText());
  }

  private void assertResourceExists(String resourcePath) {
    assertNotNull(readResource(resourcePath));
  }

  private void assertResourceMissing(String resourcePath) {
    assertFalse(Files.exists(Paths.get("src/main/resources").resolve(resourcePath)));
  }

  private void assertValidJson(String resourcePath) {
    String resource = readResource(resourcePath);
    try {
      OBJECT_MAPPER.readTree(resource);
    } catch (Exception e) {
      throw new AssertionError("resource should be valid JSON: " + resourcePath, e);
    }
  }

  private void assertCreateFormBindsSelectedForm(JsonNode form, JsonNode createData) {
    JsonNode appName = findArrayItemByName(form.path("body"), "appName");
    JsonNode formType = findArrayItemByName(form.path("body"), "formType");
    JsonNode formId = findArrayItemByName(form.path("body"), "formId");

    assertEquals("hidden", appName.path("type").asText());
    assertEquals("${appName}", appName.path("value").asText());
    assertSelectableFormBinding(formType, formId);
    assertEquals("${formType}", createData.path("form").path("formType").asText());
    assertEquals("${formId}", createData.path("form").path("formId").asText());
    assertEquals("StartNode", createData.path("nodes").get(0).path("type").asText());
    assertEquals("ApprovalNode", createData.path("nodes").get(1).path("type").asText());
    assertEquals("EndNode", createData.path("nodes").get(2).path("type").asText());
  }

  private void assertSelectableFormBinding(JsonNode formType, JsonNode formId) {
    assertEquals("button-group-select", formType.path("type").asText());
    assertEquals("business-model", formType.path("value").asText());
    assertEquals("/api/dev/${appName}/approval-workflow/base-info/form-types",
        formType.path("source").path("url").asText());
    assertEquals("${formType}", formType.path("source").path("data").path("value").asText());
    JsonNode resetForm = formType.path("onEvent").path("change").path("actions").get(0);
    assertEquals("setValue", resetForm.path("actionType").asText());
    assertEquals("", resetForm.path("args").path("value").path("formId").textValue());
    assertEquals("关联表单", formId.path("label").asText());
    assertEquals("/api/dev/${appName}/approval-workflow/base-info/form/${formType}",
        formId.path("source").path("url").asText());
    assertEquals("${!!formType}", formId.path("source").path("sendOn").asText());
    assertEquals("title", formId.path("labelField").asText());
    assertEquals("name", formId.path("valueField").asText());
  }

  private JsonNode readJsonResource(String resourcePath) {
    try {
      return OBJECT_MAPPER.readTree(readResource(resourcePath));
    } catch (Exception e) {
      throw new AssertionError("resource should be valid JSON: " + resourcePath, e);
    }
  }

  private JsonNode findCreateDefinitionData(JsonNode root) {
    JsonNode action = findObjectByFieldValue(root, "outputVar", "createResult");
    assertEquals("submit", action.path("actionType").asText());
    JsonNode form = findObjectByName(root, action.path("componentName").asText());
    return form.path("api").path("data");
  }

  private void assertCreateActionUsesAmisErrorContract(JsonNode root) {
    JsonNode submitAction = findObjectByFieldValue(root, "outputVar", "createResult");
    assertEquals("submit", submitAction.path("actionType").asText());
    JsonNode failureToast = findObjectByFieldValue(root, "actionType", "toast");
    assertEquals("流程定义创建失败", failureToast.path("args").path("msg").asText());
    assertEquals("${createResult.error}", failureToast.path("expression").asText());
  }

  private JsonNode findObjectByName(JsonNode root, String name) {
    return findObjectByFieldValue(root, "name", name);
  }

  private JsonNode findObjectByType(JsonNode root, String type) {
    return findObjectByFieldValue(root, "type", type);
  }

  private JsonNode findObjectByFieldValue(JsonNode root, String fieldName, String value) {
    if (root == null || root.isMissingNode()) {
      throw new AssertionError("object not found for " + fieldName + "=" + value);
    }
    if (root.isObject()) {
      JsonNode field = root.get(fieldName);
      if (field != null && field.isTextual() && value.equals(field.asText())) {
        return root;
      }
      var fields = root.fields();
      while (fields.hasNext()) {
        var child = fields.next().getValue();
        JsonNode result = findObjectByFieldValueOrNull(child, fieldName, value);
        if (result != null) {
          return result;
        }
      }
    }
    if (root.isArray()) {
      for (JsonNode child : root) {
        JsonNode result = findObjectByFieldValueOrNull(child, fieldName, value);
        if (result != null) {
          return result;
        }
      }
    }
    throw new AssertionError("object not found for " + fieldName + "=" + value);
  }

  private JsonNode findObjectByFieldValueOrNull(JsonNode root, String fieldName, String value) {
    if (root == null || root.isMissingNode()) {
      return null;
    }
    if (root.isObject()) {
      JsonNode field = root.get(fieldName);
      if (field != null && field.isTextual() && value.equals(field.asText())) {
        return root;
      }
      var fields = root.fields();
      while (fields.hasNext()) {
        JsonNode result = findObjectByFieldValueOrNull(fields.next().getValue(), fieldName, value);
        if (result != null) {
          return result;
        }
      }
    }
    if (root.isArray()) {
      for (JsonNode child : root) {
        JsonNode result = findObjectByFieldValueOrNull(child, fieldName, value);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private JsonNode findArrayItemByName(JsonNode array, String name) {
    if (!array.isArray()) {
      throw new AssertionError("expected array while finding item: " + name);
    }
    for (JsonNode item : array) {
      if (name.equals(item.path("name").asText())) {
        return item;
      }
    }
    throw new AssertionError("array item not found: " + name);
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
}
