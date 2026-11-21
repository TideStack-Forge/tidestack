package com.ouroboros.businessmodel.projection.ui;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.uimodel.UIModel;

/**
 * 编辑表单页面UI模型
 */
@Priority(22)
public class EditMPS extends AbstractUIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }
    final var title = context.getSourceModel().getExtraPropDeep(String.class, "views", "edit", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse("编辑" + context.getSourceModel().getTitle());
    final var processor = getUiModelProcessor("edit", path -> path + "/edit", title);
    processor.apply(context).ifPresent(uiModel -> context.putModel(UIModel.class, uiModel.getPath(), uiModel));
  }
}
