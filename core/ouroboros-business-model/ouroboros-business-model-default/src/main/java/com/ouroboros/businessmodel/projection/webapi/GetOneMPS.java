package com.ouroboros.businessmodel.projection.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 获取单个接口的Web API模型
 */
public class GetOneMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var processor = getWebApiModelProcessor("getOne", "GET", this::buildPathWithId, "getOne");
    processor.accept(context);
  }

}
