package com.ouroboros.businessmodel.projection.ui;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.uimodel.UIModel;

/**
 * 详情页UI模型
 */
@Priority(23)
public class DetailMPS extends AbstractUIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }
    final var title = context.getSourceModel().getExtraPropDeep(String.class, "views", "detail", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse(context.getSourceModel().getTitle() + "详情");
    final var processor = getUiModelProcessor("detail", path -> path + "/detail", title);
    processor.apply(context).ifPresent(uiModel -> context.putModel(UIModel.class, uiModel.getPath(), uiModel));
  }
}
