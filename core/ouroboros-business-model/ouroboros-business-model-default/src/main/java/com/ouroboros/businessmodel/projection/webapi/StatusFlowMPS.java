package com.ouroboros.businessmodel.projection.webapi;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 状态流接口的Web API模型
 */
@Priority(14)
public class StatusFlowMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // 如果未启用状态流，则不生成Web API模型
    var statusFlowIsEnabled = context.getSourceModel()
        .getExtraPropDeep(Boolean.class, "statusFlow", "isEnabled")
        .orElse(false);
    if (!statusFlowIsEnabled) {
      return;
    }
    var processor = getWebApiModelProcessor("statusFlow", "POST", getPathBuilderWithId(path -> path + "/status-flow"), "statusFlow");
    processor.accept(context);
  }

}
