package com.ouroboros.businessmodel.projection.ui;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.uimodel.UIModel;

/**
 * 添加表单页面UI模型
 */
@Priority(21)
public class AddMPS extends AbstractUIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }
    final var title = context.getSourceModel().getExtraPropDeep(String.class, "views", "add", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse("新建" + context.getSourceModel().getTitle());
    final var processor = getUiModelProcessor("add", path -> path + "/add", title);
    processor.apply(context).ifPresent(uiModel -> context.putModel(UIModel.class, uiModel.getPath(), uiModel));
  }
}
