package com.ouroboros.businessmodel.projection.data.feature;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

/**
 * 添加变更日志数据模型
 */
@Priority(7)
public class ChangeLogMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // TODO: 增加平行的数据模型
  }

}
