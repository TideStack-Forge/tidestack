package com.ouroboros.businessmodel.projection;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 模型投射器接口
 *
 * @param <B> 模型类型
 */
public interface ModelBundleProjector<B extends BusinessModel> {
  default Boolean support(B model) {
    return true;
  }

  ModelBundle project(B model);
}
