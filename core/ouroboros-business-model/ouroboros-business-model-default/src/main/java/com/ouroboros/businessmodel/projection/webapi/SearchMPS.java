package com.ouroboros.businessmodel.projection.webapi;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 查询接口的Web API模型
 */
@Priority(10)
public class SearchMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var processor = getWebApiModelProcessor("search", "GET", this::buildSimplePath, "search");
    processor.accept(context);
  }

}
