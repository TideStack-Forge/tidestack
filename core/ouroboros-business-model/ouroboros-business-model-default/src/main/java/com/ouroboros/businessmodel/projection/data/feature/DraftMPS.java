package com.ouroboros.businessmodel.projection.data.feature;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelFieldMeta;

/**
 * 为数据模型添加草稿状态位
 */
@Priority(5)
public class DraftMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var isEnableDraft = model.getExtraPropDeep(Boolean.class, "features", "isEnabledDraft").orElse(false);
    if (!isEnableDraft) {
      return;
    }
    addFieldsToMainDataModel(context, getDraftFieldMeta(), getSubmittedTimeFieldMeta());
  }

  private DataModelFieldMeta getDraftFieldMeta() {
    var draftFieldMeta = new DataModelFieldMeta();
    draftFieldMeta.setName("isDraft");
    draftFieldMeta.setLabel("是否为草稿");
    draftFieldMeta.setType("Boolean");
    draftFieldMeta.setDefaultValue("true");
    return draftFieldMeta;
  }

  private DataModelFieldMeta getSubmittedTimeFieldMeta() {
    var submittedTimeFieldMeta = new DataModelFieldMeta();
    submittedTimeFieldMeta.setName("submittedTime");
    submittedTimeFieldMeta.setLabel("提交时间");
    submittedTimeFieldMeta.setType("DateTime");
    return submittedTimeFieldMeta;
  }

}
