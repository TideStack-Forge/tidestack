package com.ouroboros.businessmodel.projection.webapi;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 删除接口的Web API模型
 */
@Priority(12)
public class DeleteMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var processor = getWebApiModelProcessor("delete", "DELETE", this::buildPathWithId, "delete");
    processor.accept(context);
  }

}

