package com.ouroboros.businessmodel.projection.ui;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;

/**
 * 列表页UI模型
 */
@Priority(20)
public class IndexMPS extends AbstractUIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var title = context.getSourceModel().getExtraPropDeep(String.class, "views", "index", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse(context.getSourceModel().getTitle() + "列表页");
    final var processor = getUiModelProcessor("index", path -> path, title);
    processor.apply(context).ifPresent(uimodel -> {
      ((SimpleUIModel) uimodel).setExtraProp("schemaId", uimodel.getPath());
      context.putModel(UIModel.class, uimodel.getPath(), uimodel);
    });
  }
}

