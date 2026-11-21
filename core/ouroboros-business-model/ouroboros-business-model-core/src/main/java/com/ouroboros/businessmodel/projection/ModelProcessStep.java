package com.ouroboros.businessmodel.projection;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 模型投射流水线步骤接口
 *
 * @param <B> 模型类型
 */
public interface ModelProcessStep<B extends BusinessModel> {
  default Boolean support(ModelBundleProjector<?> projector) {
    return true;
  }

  void process(ModelProjectionPipelineContext<B> context);
}
