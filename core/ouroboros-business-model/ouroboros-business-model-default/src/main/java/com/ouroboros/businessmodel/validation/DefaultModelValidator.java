package com.ouroboros.businessmodel.validation;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.util.SPI;

/**
 * 模型验证器默认实现
 */
public class DefaultModelValidator implements ModelValidator<DefaultBusinessModel> {
  final private List<ModelDetailValidator<DefaultBusinessModel>> detailValidators;

  @SuppressWarnings("unchecked")
  public DefaultModelValidator() {
    detailValidators = SPI
        .getCachedSortedServiceStream(ModelDetailValidator.class, DefaultBusinessModel.class)
        .filter(v -> v.support(DefaultBusinessModel.class))
        .map(v -> (ModelDetailValidator<DefaultBusinessModel>) v)
        .collect(Collectors.toList());
  }

  @Override
  public ValidationResult validate(DefaultBusinessModel model) {
    var context = new ValidationContext<>(model);
    detailValidators.forEach(v -> v.doValidate(context));
    return new ValidationResult(context.getErrors());
  }
}
