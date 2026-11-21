package com.ouroboros.businessmodel.validation;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 模型详细验证器接口
 *
 * @param <B> 模型类型
 */
public interface ModelDetailValidator<B extends BusinessModel> {
  default boolean support(Class<B> modelClass) {
    return true;
  }

  void doValidate(ValidationContext<B> context);
}
