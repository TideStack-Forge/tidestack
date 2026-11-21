package com.ouroboros.businessmodel.validation;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 模型验证器接口
 *
 * @param <B> 模型类型
 */
public interface ModelValidator<B extends BusinessModel> {
  ValidationResult validate(B model);
}
