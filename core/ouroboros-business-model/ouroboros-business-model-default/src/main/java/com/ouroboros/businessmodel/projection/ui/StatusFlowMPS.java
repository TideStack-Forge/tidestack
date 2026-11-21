package com.ouroboros.businessmodel.projection.ui;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.uimodel.UIModel;

/**
 * 状态流页面UI模型
 */
@Priority(24)
public class StatusFlowMPS extends AbstractUIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }
    // 如果状态流未启用，则不生成UI模型
    var statusFlowIsEnabled = context.getSourceModel()
        .getExtraPropDeep(Boolean.class, "statusFlow", "isEnabled")
        .orElse(false);
    if (!statusFlowIsEnabled) {
      return;
    }

    // 如果状态流API未启用，则不生成UI模型
    var statusFlowApiIsEnabled = context.getSourceModel()
        .getExtraPropDeep(Boolean.class, WEBAPIS_CONFIG_KEY, "statusFlow", "isEnabled")
        .orElse(false);
    if (!statusFlowApiIsEnabled) {
      return;
    }
    final var processor = getUiModelProcessor("statusFlow", path -> path + "/status-flow", context.getSourceModel().getTitle() + "流程");
    processor.apply(context).ifPresent(uiModel -> context.putModel(UIModel.class, uiModel.getPath(), uiModel));
  }
}

