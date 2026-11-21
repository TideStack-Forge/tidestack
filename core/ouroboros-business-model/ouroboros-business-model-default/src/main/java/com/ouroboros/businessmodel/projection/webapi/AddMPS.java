package com.ouroboros.businessmodel.projection.webapi;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 添加接口的Web API模型
 */
@Priority(11)
public class AddMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var processor = getWebApiModelProcessor("add", "POST", this::buildSimplePath, "add");
    processor.accept(context);
  }

}
