package com.ouroboros.workflow.approval.dev.service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.dev.model.ApprovalWorkflowDefinitionAsset;

import io.vavr.control.Try;

@Service
@DevelopModule("approval-workflow-definition")
public class ApprovalWorkflowDefinitionDevelopService extends BaseDevelopService<ApprovalWorkflowDefinitionAsset> {
  public static final String MODEL_FILE_DIRECTORY = "workflow";
  public static final String MODEL_FILE_EXTENSION = ".workflow.json";

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getModelDisplayName() {
    return "审批流定义资产";
  }

  @Override
  protected String getFullName(ApprovalWorkflowDefinitionAsset model) {
    return model.getName();
  }

  @Override
  protected Try<Void> validateModel(ApprovalWorkflowDefinitionAsset model) {
    if (model == null) {
      return Try.failure(new BusinessException("审批流定义不能为空"));
    }
    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException("流程标识不能为空"));
    }
    if (!isSafeDefinitionId(model.getName())) {
      return Try.failure(new BusinessException("流程标识不能包含路径分隔符、空白或上级目录片段"));
    }
    if (StringUtils.isBlank(model.getTitle())) {
      return Try.failure(new BusinessException("流程名称不能为空"));
    }
    if (model.getVersion() == null || model.getVersion() <= 0) {
      return Try.failure(new BusinessException("流程版本必须为正整数"));
    }
    if (model.getForm() == null
        || StringUtils.isBlank(model.getForm().getFormType())
        || StringUtils.isBlank(model.getForm().getFormId())) {
      return Try.failure(new BusinessException("流程表单绑定不能为空"));
    }
    if (model.getFlowDefinition() == null || model.getFlowDefinition().isNull()) {
      return Try.failure(new BusinessException("流程定义内容不能为空"));
    }
    return Try.success(null);
  }

  @Override
  public Try<ApprovalWorkflowDefinitionAsset> buildModelFromFileContent(String fileContent) {
    return ApprovalWorkflowDefinitionDocument.decode(fileContent)
        .map(ApprovalWorkflowDefinitionDocument::metadata)
        .flatMap(this::assetFromMetadata);
  }

  @Override
  public Try<ApprovalWorkflowDefinitionAsset> buildModelFromRequestBody(String requestBody) {
    return ApprovalWorkflowDefinitionDocument.decode(requestBody)
        .map(ApprovalWorkflowDefinitionDocument::metadata)
        .flatMap(this::assetFromMetadata);
  }

  @Override
  protected Try<String> getPermanentJson(ApprovalWorkflowDefinitionAsset model) {
    return metadataFromAsset(model)
        .flatMap(ApprovalWorkflowDefinitionDocument::of)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJson);
  }

  @Override
  public Try<ApprovalWorkflowDefinitionAsset> update(String fullName, ApprovalWorkflowDefinitionAsset model) {
    if (model == null || !StringUtils.equals(fullName, model.getName())) {
      return Try.failure(new BusinessException("流程标识与文件路径不一致，不能隐式重命名审批流定义"));
    }
    model.setVersion(readVersion(fullName).map(version -> version + 1).orElse(1));
    return super.update(fullName, model);
  }

  @Override
  public Optional<ApprovalWorkflowDefinitionAsset> getOne(String fullName) {
    return super.getOne(fullName);
  }

  @Override
  public Try<?> getInitFormData(String fullName) {
    return Try.success(getOne(fullName)
        .map(ApprovalWorkflowDefinitionAsset::getFlowDefinition)
        .orElse(null));
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> buildModelFromFileContent(file.getContent())
        .map(asset -> new ModelFileMetaWrapper<>(asset, file));
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<ApprovalWorkflowDefinitionAsset>> buildSearchFilter(
      Map<String, Object> searchParam) {
    String keyword = Optional.ofNullable(searchParam.get("keyword"))
        .map(Object::toString)
        .orElse("")
        .toLowerCase();
    return wrapper -> {
      if (StringUtils.isBlank(keyword)) {
        return true;
      }
      ApprovalWorkflowDefinitionAsset model = wrapper.getModel();
      return StringUtils.defaultString(model.getName()).toLowerCase().contains(keyword)
          || StringUtils.defaultString(model.getTitle()).toLowerCase().contains(keyword);
    };
  }

  @Override
  public java.util.List<?> search(Map<String, Object> searchParam) {
    return super.search(searchParam).stream()
        .map(ModelFileMetaWrapper.class::cast)
        .sorted((left, right) -> {
          ApprovalWorkflowDefinitionAsset leftModel = (ApprovalWorkflowDefinitionAsset) left.getModel();
          ApprovalWorkflowDefinitionAsset rightModel = (ApprovalWorkflowDefinitionAsset) right.getModel();
          return StringUtils.defaultString(leftModel.getName())
              .compareTo(StringUtils.defaultString(rightModel.getName()));
        })
        .collect(Collectors.toList());
  }

  private Try<ApprovalWorkflowDefinitionAsset> assetFromMetadata(ApprovalWorkflowDefinitionMetadata metadata) {
    return ApprovalWorkflowDefinitionDocument.of(metadata)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJsonNode)
        .map(definition -> {
          ApprovalWorkflowDefinitionAsset asset = new ApprovalWorkflowDefinitionAsset();
          asset.setName(metadata.getName());
          asset.setTitle(metadata.getTitle());
          asset.setDescription(metadata.getDescription());
          asset.setVersion(metadata.getVersion());
          asset.setEnabled(metadata.getEnabled());
          asset.setForm(metadata.getForm());
          asset.setFlowDefinition(definition);
          return asset;
        });
  }

  private Try<ApprovalWorkflowDefinitionMetadata> metadataFromAsset(ApprovalWorkflowDefinitionAsset asset) {
    return Json.tryToJsonString(asset.getFlowDefinition())
        .flatMap(ApprovalWorkflowDefinitionDocument::decode)
        .map(ApprovalWorkflowDefinitionDocument::metadata)
        .map(metadata -> {
          metadata.setName(asset.getName());
          metadata.setTitle(asset.getTitle());
          metadata.setDescription(asset.getDescription());
          metadata.setVersion(asset.getVersion() == null ? 1 : asset.getVersion());
          metadata.setEnabled(asset.getEnabled());
          metadata.setForm(asset.getForm());
          return metadata;
        });
  }

  private Optional<Integer> readVersion(String fullName) {
    VCSFile modelFile = getVcsManager().getFile(buildModelFilePath(fullName));
    if (!modelFile.exists()) {
      return Optional.empty();
    }
    return buildModelFromFileContent(modelFile.getContent()).toJavaOptional()
        .map(ApprovalWorkflowDefinitionAsset::getVersion);
  }

  private boolean isSafeDefinitionId(String id) {
    String trimmed = StringUtils.trimToEmpty(id);
    return StringUtils.equals(id, trimmed)
        && StringUtils.isNotBlank(trimmed)
        && !StringUtils.contains(trimmed, "/")
        && !StringUtils.contains(trimmed, "\\")
        && !StringUtils.contains(trimmed, "..");
  }

}
