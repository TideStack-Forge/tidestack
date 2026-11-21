package com.ouroboros.businessmodel.projection.authority;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 生成业务模型的状态流转相关权限模型
 */
@Priority(74)
public class StatusFlowMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // TODO: 生成业务模型的状态流转相关权限模型，下次迭代实现
  }

}
