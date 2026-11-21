package com.ouroboros.businessmodel.projection.data.basic;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

@Priority(8)
public class RawNameMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // TODO: 处理 rawName
  }
}
