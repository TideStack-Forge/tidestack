package com.ouroboros.workflow.approval.manage.lifecycle;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

import io.vavr.control.Try;

public class WorkflowModelDefinitionProjector {
  private static final String EMPTY = "";
  private static final String IMPORTED_CREATOR = "metadata-layer";

  public Try<WorkflowModel> createMetadataLayerDraft(ApprovalWorkflowDefinitionMetadata definition) {
    return ApprovalWorkflowDefinitionDocument.of(definition)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJson)
        .map(metadataJson -> {
          WorkflowModel model = new WorkflowModel();
          applyDefinitionFields(model, definition, metadataJson, IMPORTED_CREATOR, LocalDateTime.now());
          model.setStatus(WorkflowModel.STATUS_DRAFT);
          model.setProcessDefinitionId(EMPTY);
          model.setSource(WorkflowModel.SOURCE_METADATA_LAYER);
          model.setAdopted(false);
          return model;
        });
  }

  public Try<WorkflowModel> createInstanceDraft(
      ApprovalWorkflowDefinitionMetadata definition,
      String creator) {
    return ApprovalWorkflowDefinitionDocument.of(definition)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJson)
        .map(metadataJson -> {
          WorkflowModel model = new WorkflowModel();
          applyDefinitionFields(model, definition, metadataJson, creator, LocalDateTime.now());
          model.setStatus(WorkflowModel.STATUS_DRAFT);
          model.setProcessDefinitionId(EMPTY);
          model.setSource(WorkflowModel.SOURCE_INSTANCE);
          model.setAdopted(false);
          return model;
        });
  }

  public Try<WorkflowModel> updateInstanceDefinition(
      String id,
      WorkflowModel existing,
      ApprovalWorkflowDefinitionMetadata definition,
      String creator) {
    return ApprovalWorkflowDefinitionDocument.of(definition)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJson)
        .map(metadataJson -> {
          WorkflowModel model = existing == null ? new WorkflowModel() : existing;
          model.setId(id);
          model.setProcessDefinitionId(StringUtils.defaultString(model.getProcessDefinitionId()));
          if (existing == null) {
            model.setStatus(WorkflowModel.STATUS_DRAFT);
          } else if (!WorkflowModel.STATUS_DRAFT.equalsIgnoreCase(model.getStatus())) {
            model.setStatus(WorkflowModel.STATUS_UPDATED);
          }
          applyDefinitionFields(model, definition, metadataJson, creator, LocalDateTime.now());
          return model;
        });
  }

  public Try<String> toDeploymentDefinitionJson(WorkflowModel model) {
    return ApprovalWorkflowDefinitionDocument.decode(model.getJson())
        .flatMap(ApprovalWorkflowDefinitionDocument::toRuntimeJson);
  }

  /**
   * 审批定义元数据 → WorkflowModel 的公共字段投影。
   *
   * <p>定义元数据里没有 {@code icon}/{@code category}，而
   * {@code WorkflowModel.category} 是全仓唯一由本投影写入、却被
   * {@code WorkflowModelServiceImpl.searchAvailableByPage} 与
   * {@code ApprovalWorkflowInstanceController.getAvailable} 读取用于分类过滤与分组卡片。
   * 因此这两个字段只在为空时补空串：既保留维护者已设置的分类/图标，
   * 又满足 {@code WorkflowModel.icon}/{@code category} 的非空校验——它们是实体的非空字段，
   * 新建元数据层定义时留 {@code null} 会让插入直接校验失败，导致应用启动中断。
   */
  private void applyDefinitionFields(
      WorkflowModel model,
      ApprovalWorkflowDefinitionMetadata definition,
      String metadataJson,
      String creator,
      LocalDateTime saveAt) {
    model.setId(definition.getName());
    model.setName(definition.getTitle());
    model.setIcon(StringUtils.defaultString(model.getIcon()));
    model.setCategory(StringUtils.defaultString(model.getCategory()));
    model.setJson(metadataJson);
    model.setCreator(creator);
    model.setSaveAt(saveAt);
    model.setDescription(StringUtils.defaultString(definition.getDescription()));
    model.setVersion(definition.getVersion());
  }
}
