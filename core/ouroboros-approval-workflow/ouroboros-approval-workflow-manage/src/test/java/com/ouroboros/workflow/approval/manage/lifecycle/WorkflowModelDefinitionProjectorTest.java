package com.ouroboros.workflow.approval.manage.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadataCodec;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

class WorkflowModelDefinitionProjectorTest {
  private final ApprovalWorkflowDefinitionMetadataCodec metadataCodec = new ApprovalWorkflowDefinitionMetadataCodec();
  private final WorkflowModelDefinitionProjector projector = new WorkflowModelDefinitionProjector();

  @Test
  void createMetadataLayerDraftProjectsStaticMetadataToRuntimeModelFields() {
    WorkflowModel model = projector.createMetadataLayerDraft(metadata()).get();

    assertEquals("flow-a", model.getId());
    assertEquals("采购审批", model.getName());
    assertEquals(WorkflowModel.STATUS_DRAFT, model.getStatus());
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, model.getSource());
    assertEquals(Integer.valueOf(3), model.getVersion());
    assertFalse(model.getAdopted());
    assertEquals("", model.getProcessDefinitionId());

    JsonNode persisted = Json.toBean(JsonNode.class, model.getJson());
    assertEquals(1, persisted.path("formatVersion").asInt());
    assertEquals("flow-a", persisted.path("name").asText());
    assertEquals("purchase-form", persisted.path("form").path("formId").asText());
  }

  @Test
  void createInstanceDraftKeepsCanonicalDefinitionVersionAndStartsWithoutDeploymentPointer() {
    WorkflowModel model = projector.createInstanceDraft(metadata(), "alice").get();

    assertEquals(WorkflowModel.STATUS_DRAFT, model.getStatus());
    assertEquals(WorkflowModel.SOURCE_INSTANCE, model.getSource());
    assertEquals(Integer.valueOf(3), model.getVersion());
    assertFalse(model.getAdopted());
    assertEquals("", model.getProcessDefinitionId());
    assertEquals("alice", model.getCreator());
  }

  @Test
  void updateDeployedInstanceMarksUpdatedAndKeepsCurrentDeploymentPointer() {
    WorkflowModel existing = projector.createInstanceDraft(metadata(), "alice").get();
    existing.setStatus(WorkflowModel.STATUS_DEPLOYED);
    existing.setProcessDefinitionId("flow-a:1:100");

    WorkflowModel updated = projector.updateInstanceDefinition("flow-a", existing, metadata(), "bob").get();

    assertEquals(WorkflowModel.STATUS_UPDATED, updated.getStatus());
    assertEquals("flow-a:1:100", updated.getProcessDefinitionId());
    assertEquals("bob", updated.getCreator());
  }

  /**
   * 投影结果必须填满 {@code WorkflowModel} 的非空字段。
   *
   * <p>{@code icon}/{@code category} 是实体上的非空字段（数据层会为它们补 {@code NotNull} 规则），
   * 但定义元数据里没有对应来源。草稿一旦留空，元数据层定义的插入会被数据层非空规则拒绝，
   * 而 {@code ApprovalWorkflowDefinitionLayerRefresher} 会把该异常抛到上下文刷新，应用直接启动失败。
   */
  @Test
  void draftsFillEveryNonNullableWorkflowModelField() throws Exception {
    assertFillsNonNullableFields(projector.createMetadataLayerDraft(metadata()).get());
    assertFillsNonNullableFields(projector.createInstanceDraft(metadata(), "alice").get());
  }

  private void assertFillsNonNullableFields(WorkflowModel model) throws Exception {
    DataModelMeta modelMeta = TypedModelMetaHelper.scanModelMate(WorkflowModel.class.getPackageName())
        .get(WorkflowModel.class.getSimpleName());
    List<String> missingFields = new ArrayList<>();
    for (DataModelFieldMeta fieldMeta : modelMeta.getFields()) {
      if (Boolean.TRUE.equals(fieldMeta.getIsNullable())) {
        continue;
      }
      java.lang.reflect.Field entityField = WorkflowModel.class.getDeclaredField(fieldMeta.getName());
      entityField.setAccessible(true);
      if (entityField.get(model) == null) {
        missingFields.add(fieldMeta.getName());
      }
    }

    assertTrue(missingFields.isEmpty(), () -> "投影结果缺少非空字段，插入会校验失败: " + missingFields);
  }

  /**
   * 定义元数据里没有 icon/category，保存时必须保留模型上已有的值。
   *
   * <p>此前无条件写空串，而 category 是全仓唯一由本投影写入、却被"可发起流程分类过滤与分组卡片"
   * 读取的字段，导致每次保存定义都清空维护者设置的分类/图标。
   */
  @Test
  void updateInstanceDefinitionPreservesMaintainedCategoryAndIcon() {
    WorkflowModel existing = projector.createInstanceDraft(metadata(), "alice").get();
    existing.setCategory("人事审批");
    existing.setIcon("icon-hr");

    WorkflowModel updated = projector.updateInstanceDefinition("flow-a", existing, metadata(), "bob").get();

    assertEquals("人事审批", updated.getCategory());
    assertEquals("icon-hr", updated.getIcon());
  }

  @Test
  void toDeploymentDefinitionJsonProjectsStoredMetadataToRuntimeDefinition() {
    WorkflowModel model = projector.createMetadataLayerDraft(metadata()).get();

    String deploymentJson = projector.toDeploymentDefinitionJson(model).get();

    JsonNode runtime = Json.toBean(JsonNode.class, deploymentJson);
    assertEquals(2, runtime.path("formatVersion").asInt());
    assertEquals("flow-a", runtime.path("id").asText());
    assertEquals("采购审批", runtime.path("name").asText());
    assertEquals("purchase-form", runtime.path("formKey").asText());
  }

  private ApprovalWorkflowDefinitionMetadata metadata() {
    return metadataCodec.decodeMetadata("""
        {
          "formatVersion": 1,
          "name": "flow-a",
          "title": "采购审批",
          "description": "采购申请审批",
          "version": 3,
          "enabled": true,
          "form": {
            "formType": "default",
            "formId": "purchase-form"
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start", "targetId": "end" }
          ]
        }
        """).get();
  }
}
