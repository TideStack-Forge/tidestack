package com.ouroboros.businessmodel.dev.ui.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelEditorUISchemaProviderTest {

  @Test
  void shouldKeepDefaultEditorSchemaAndMergePluginFragments() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("${appName}"));
    assertTrue(schemaJson.contains("\"hash\":\"workflow\""));
    assertTrue(schemaJson.contains("workflow.bindings[0].processId"));
    assertTrue(schemaJson.contains("审批流程"));
    assertTrue(schemaJson.contains("默认流程"));
    assertTrue(schemaJson.contains("启用工作流集成"));
    assertTrue(schemaJson.contains("\"type\":\"input-group\""));
    assertTrue(schemaJson.contains("创建流程"));
    assertTrue(schemaJson.contains("选择现有流程"));
    assertTrue(schemaJson.contains("打开流程编辑器"));
    assertTrue(schemaJson.contains("/dev/${appName}/approval-workflow-definition/${workflowDefinitionName}"));
    assertTrue(schemaJson.contains("/dev/${appName}/approval-workflow-definition/${workflow.bindings[0].processId}"));
    assertTrue(schemaJson.contains("新建默认审批流程"));
    assertTrue(schemaJson.contains("create-default-approval-workflow-form"));
    assertTrue(schemaJson.contains("workflowDefinitionName"));
    assertTrue(schemaJson.contains("workflowDefinitionTitle"));
    assertTrue(schemaJson.contains("workflowDefinitionDescription"));
    assertTrue(schemaJson.contains("\"className\":\"mt-4 text-right\""));
    assertTrue(schemaJson.contains("创建并编辑"));
    assertTrue(schemaJson.contains("默认审批流程创建失败"));
    assertTrue(schemaJson.contains("\"definitionId\":\"${definitionId}\""));
    assertTrue(schemaJson.contains("\"formatVersion\":1"));
    assertTrue(schemaJson.contains("\"name\":\"${workflowDefinitionName}\""));
    assertTrue(schemaJson.contains("\"title\":\"${workflowDefinitionTitle}\""));
    assertTrue(schemaJson.contains("\"description\":\"${workflowDefinitionDescription || ''}\""));
    assertTrue(schemaJson.contains("\"type\":\"StartNode\""));
    assertTrue(schemaJson.contains("\"type\":\"ApprovalNode\""));
    assertTrue(schemaJson.contains("\"type\":\"EndNode\""));
    assertTrue(schemaJson.contains("formType") && schemaJson.contains("business-model"));
    assertTrue(schemaJson.contains("formId") && schemaJson.contains("${fullName}"));
    assertTrue(schemaJson.contains("\"sourceLayer\":\"default\""));
    assertTrue(schemaJson.contains("returnTarget"));
    assertTrue(schemaJson.contains("receiveActions"));
    assertTrue(schemaJson.contains("approvalWorkflowDefinitionSaved"));
    assertTrue(schemaJson.contains("bindingWritebackSuggested"));
    assertTrue(schemaJson.contains("/api/dev/${appName}/approval-workflow-definition"));
    assertTrue(schemaJson.contains("名称或标识"));
    assertTrue(schemaJson.contains("\"responseData\":{\"items\":{\"$items\":{\"&\":\"$model\""));
    assertTrue(schemaJson.contains("rowClassNameExpr"));
    assertTrue(schemaJson.contains("当前绑定"));
    assertTrue(schemaJson.contains("\"type\":\"operation\""));
    assertTrue(schemaJson.contains("\"label\":\"选择\""));
    assertFalse(schemaJson.contains("\"itemAction\""));
    assertTrue(schemaJson.contains("\"workflow.isEnabled\":true"));
    assertTrue(schemaJson.contains("\"workflow.bindings[0].processId\":\"${workflowDefinitionName}\""));
    assertTrue(schemaJson.contains("\"workflow.bindings[0].processId\":\"${name || id}\""));
    assertTrue(schemaJson.contains("\"definitionId\":\"${name || id}\""));
    assertTrue(schemaJson.contains("\"definitionName\":\"${title || name || id}\""));
    assertTrue(schemaJson.contains("closeDialog"));
    assertBusinessModelFormWritebacksDoNotCopyCurrentContext(schema);
    assertFalse(schemaJson.contains("/dev/${appName}/approval-workflow-definition/add"));
    assertFalse(schemaJson.contains("/dev/${appName}/approval-workflow-definition/editor"));
    assertFalse(schemaJson.contains("\"type\":\"picker\""));
    assertFalse(schemaJson.contains("\"embed\":true"));
    assertFalse(schemaJson.contains("\"name\":\"selectedWorkflowDefinition\""));
    assertFalse(schemaJson.contains("SelectResult.value[0]"));
    assertFalse(schemaJson.contains("\"name\":\"definitionId\""));
    assertFalse(schemaJson.contains("\"type\":\"通知节点\""));
    assertFalse(schemaJson.contains("绑定所选流程"));
    assertFalse(schemaJson.contains("替换绑定流程"));
    assertFalse(schemaJson.contains("替换为所选流程"));
    assertFalse(schemaJson.contains("绑定并打开编辑器"));
    assertFalse(schemaJson.contains("替换并打开编辑器"));
    assertFalse(schemaJson.contains("\"title\":\"默认流程绑定\""));
    assertFalse(schemaJson.contains("通过显式 processId"));
    assertFalse(schemaJson.contains("默认流程定义资产"));
    assertFalse(schemaJson.contains("最终何时生效"));
    assertFalse(schemaJson.contains("创建前先明确指定 name"));
    assertFalse(schemaJson.contains("这里只列出开发平台中的默认层流程定义资产。"));
    assertFalse(schemaJson.contains("部署后实例层"));
    assertFalse(schemaJson.contains("已部署"));
    assertFalse(schemaJson.contains("已下线"));
    assertFalse(schemaJson.contains("\"value\":\"deployed\""));
    assertFalse(schemaJson.contains("\"value\":\"offline\""));
    assertFalse(schemaJson.contains("指定流程标识"));
    assertFalse(schemaJson.contains("\"scene\""));
    assertFalse(schemaJson.contains("business-model-design"));
    assertFalse(schemaJson.contains("definition-library"));
    assertFalse(schemaJson.contains("instance-customization"));
    assertFalse(schemaJson.contains("readonly-viewer"));
    assertFalse(schemaJson.contains("approval_workflow_definition_open_requested"));
    assertFalse(schemaJson.contains("approval_workflow_definition_select_requested"));
    assertFalse(schemaJson.contains("approval_workflow_definition_replace_requested"));
    assertFalse(schemaJson.contains("\"modelKey\":\"${fullName}\""));
    assertFalse(schemaJson.contains("\"modelName\":\"${title}\""));
    assertFalse(schemaJson.contains("\"title\":\"流程流转\""));
    assertFalse(schemaJson.contains("archiveDeleteConfig.archiveModel"));
    assertFalse(schemaJson.contains("scd2HistoryConfig.historyModel"));
    assertTrue(schemaJson.contains("自动生成并列归档模型"));
    assertTrue(schemaJson.contains("自动生成并列历史模型"));
    assertTrue(schemaJson.contains("businessModelFeaturesPanel"));
  }

  @Test
  void shouldNotMergeDefaultOnlyPluginFragmentsForNonDefaultEditor() throws Exception {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = generateEditorSchema(provider, Collections.singletonMap("modelType", BusinessModelTypes.COMPOSITE_VIEW));
    String schemaJson = Json.toJsonString(schema);

    assertFalse(schemaJson.contains("archiveDeleteConfig.archiveModel"));
    assertFalse(schemaJson.contains("scd2HistoryConfig.historyModel"));
  }

  @Test
  void shouldUseDashboardEditorSchemaWhenModelTypeContextIsDashboard() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema(
        "/dev/{appName}/business-model/editor",
        Collections.<String, Object>singletonMap("modelType", BusinessModelTypes.DASHBOARD)
    ).orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"type\":\"dashboard\""));
    assertTrue(schemaJson.contains("\"name\":\"chart\""));
    assertTrue(schemaJson.contains("\"type\":\"select\""));
    assertTrue(schemaJson.contains("/api/dev/${appName}/chart/options"));
  }

  @Test
  void shouldGenerateCompositeViewEditorSchemaWithSourceColumnsFiltersAndActions() throws Exception {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = generateEditorSchema(provider, Collections.singletonMap("modelType", BusinessModelTypes.COMPOSITE_VIEW));
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"value\":\"composite-view\""));
    assertTrue(schemaJson.contains("\"hash\":\"source\""));
    assertTrue(schemaJson.contains("\"name\":\"datasetName\""));
    assertTrue(schemaJson.contains("\"name\":\"embeddedQuery\""));
    assertTrue(schemaJson.contains("\"hash\":\"columns\""));
    assertTrue(schemaJson.contains("\"name\":\"columns\""));
    assertTrue(schemaJson.contains("\"name\":\"field\""));
    assertTrue(schemaJson.contains("\"name\":\"title\""));
    assertTrue(schemaJson.contains("\"name\":\"width\""));
    assertTrue(schemaJson.contains("\"name\":\"format\""));
    assertTrue(schemaJson.contains("\"hash\":\"filters\""));
    assertTrue(schemaJson.contains("\"name\":\"filterConfig.items\""));
    assertTrue(schemaJson.contains("\"name\":\"ops\""));
    assertTrue(schemaJson.contains("\"name\":\"widget\""));
    assertTrue(schemaJson.contains("\"hash\":\"actions\""));
    assertTrue(schemaJson.contains("\"name\":\"actionButtons\""));
    assertTrue(schemaJson.contains("\"name\":\"scope\""));
    assertTrue(schemaJson.contains("\"value\":\"row\""));
    assertTrue(schemaJson.contains("\"value\":\"batch\""));
    assertTrue(schemaJson.contains("\"name\":\"apiRef\""));
    assertTrue(schemaJson.contains("\"name\":\"contextBinding\""));
    assertFalse(schemaJson.contains("workflow.bindings[0].processId"));
    assertFalse(schemaJson.contains("archiveDeleteConfig.archiveModel"));
    assertFalse(schemaJson.contains("scd2HistoryConfig.historyModel"));
  }

  @Test
  void shouldPreserveWrapperEditorShellWhenMergingPluginFragments() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);

    assertEquals("wrapper", Json.toMap(Json.toJsonString(schema)).get("type"));
  }

  @Test
  void shouldKeepAppNameInBusinessModelEditShellDataForNestedModelMetaProviders() throws IOException {
    String pageSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/business-model/{fullName}.ui-schema.json");
    String dialogSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/business-model/edit.ui-schema.json");
    String createSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/business-model/create.ui-schema.json");
    String detailSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/business-model/detail.ui-schema.json");

    assertTrue(pageSchema.contains("\"appName\": \"$appName\""));
    assertTrue(pageSchema.contains("\"appName\": \"${result.data.appName}\""));
    assertTrue(dialogSchema.contains("\"appName\": \"$appName\""));
    assertTrue(dialogSchema.contains("\"appName\": \"${result.data.appName}\""));
    assertTrue(createSchema.contains("appName=${appName}"));
    assertTrue(detailSchema.contains("\"appName\": \"${appName}\""));
    assertTrue(pageSchema.contains("\"schemaId\": \"/dev/{appName}/business-model/editor\""));
    assertTrue(dialogSchema.contains("\"url\": \"/api/amis-schema?path=/dev/{appName}/business-model/editor&appName=${appName}&fullName=${fullName}&modelType=${modelType}\""));
  }

  @Test
  void shouldResolveConcreteAppNameInDynamicEditorSchemaDataSources() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema(
        "/dev/{appName}/business-model/editor",
        Map.of("appName", "test-app", "fullName", "Order", "modelType", BusinessModelTypes.DEFAULT)
    ).orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("/api/dev/test-app/business-model/list"));
    assertTrue(schemaJson.contains("/api/dev/test-app/business-model/${fullName}/reference-relations"));
    assertTrue(schemaJson.contains("/api/dev/test-app/business-model-helper/${modelRef}/model-meta"));
    assertFalse(schemaJson.contains("/api/dev/${appName}"));
  }

  @Test
  void shouldKeepBusinessModelEditorTabsHeightAnchor() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"type\":\"tabs\""));
    assertTrue(schemaJson.contains("\"style\":{\"height\":\"200px\"}"));
  }

  @Test
  void shouldKeepBusinessFieldMainColumnsEqualWidthAndActionColumnsCompact() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"name\":\"name\""));
    assertTrue(schemaJson.contains("\"label\":\"字段名\""));
    assertTrue(schemaJson.contains("\"name\":\"title\""));
    assertTrue(schemaJson.contains("\"label\":\"显示名\""));
    assertTrue(schemaJson.contains("\"name\":\"type\""));
    assertTrue(schemaJson.contains("\"label\":\"类型\""));
    assertTrue(countMatches(schemaJson, "\"columnClassName\":\"flex-1\"") >= 3);
    assertTrue(schemaJson.contains("\"name\":\"isPrimaryKey\""));
    assertTrue(schemaJson.contains("\"name\":\"isRequired\""));
    assertTrue(schemaJson.contains("\"name\":\"isUnique\""));
    assertTrue(countMatches(schemaJson, "\"columnClassName\":\"flex-grow-0 min-w-max\"") >= 3);
  }

  @Test
  void shouldKeepRelationMainColumnsEqualWidthAndConfigColumnCompact() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"name\":\"relations\""));
    assertTrue(schemaJson.contains("\"label\":\"关系名\""));
    assertTrue(schemaJson.contains("\"label\":\"显示名\""));
    assertTrue(schemaJson.contains("\"label\":\"类型\""));
    assertTrue(schemaJson.contains("\"label\":\"模型\""));
    assertTrue(countMatches(schemaJson, "\"columnClassName\":\"flex-1\"") >= 7);
    assertTrue(schemaJson.contains("\"actionType\":\"drawer\""));
    assertTrue(countMatches(schemaJson, "\"columnClassName\":\"flex-grow-0 min-w-max\"") >= 4);
  }

  @Test
  void shouldKeepReferenceRelationsCrudAutoFillHeightDisabledExplicitly() {
    BusinessModelEditorUISchemaProvider provider = new BusinessModelEditorUISchemaProvider();

    Object schema = provider.getUISchema("/dev/{appName}/business-model/editor").orElseThrow(AssertionError::new);
    String schemaJson = Json.toJsonString(schema);

    assertTrue(schemaJson.contains("\"url\":\"/api/dev/${appName}/business-model/${fullName}/reference-relations\""));
    assertTrue(schemaJson.contains("\"affixHeader\":false"));
    assertTrue(schemaJson.contains("\"autoFillHeight\":false"));
  }

  private static int countMatches(String text, String token) {
    Matcher matcher = Pattern.compile(Pattern.quote(token)).matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static Object generateEditorSchema(BusinessModelEditorUISchemaProvider provider, Map<String, Object> context) throws Exception {
    Method method = BusinessModelEditorUISchemaProvider.class.getDeclaredMethod("generateEditorSchema", Map.class);
    method.setAccessible(true);
    return method.invoke(provider, context);
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelEditorUISchemaProviderTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IOException("Missing resource: " + path);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = stream.read(buffer)) != -1) {
        output.write(buffer, 0, length);
      }
      return output.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  private static void assertBusinessModelFormWritebacksDoNotCopyCurrentContext(Object node) {
    if (node instanceof Map<?, ?> map) {
      if ("edit-business-model-form".equals(map.get("componentName"))) {
        Object argsObject = map.get("args");
        assertTrue(argsObject instanceof Map<?, ?>);
        Object valueObject = ((Map<?, ?>) argsObject).get("value");
        assertTrue(valueObject instanceof Map<?, ?>);
        assertFalse(((Map<?, ?>) valueObject).containsKey("&"));
      }
      for (Object child : map.values()) {
        assertBusinessModelFormWritebacksDoNotCopyCurrentContext(child);
      }
      return;
    }
    if (node instanceof List<?> list) {
      for (Object child : list) {
        assertBusinessModelFormWritebacksDoNotCopyCurrentContext(child);
      }
    }
  }
}
