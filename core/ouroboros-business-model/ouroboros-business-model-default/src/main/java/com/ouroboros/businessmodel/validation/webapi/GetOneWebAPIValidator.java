package com.ouroboros.businessmodel.validation.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineApiError;
import com.ouroboros.businessmodel.validation.webapi.support.BusinessModelDataModelResolver;
import com.ouroboros.businessmodel.validation.webapi.support.PopulateDefinitionValidator;
import com.ouroboros.businessmodel.validation.webapi.support.WhereDefinitionValidator;

public class GetOneWebAPIValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    DefaultBusinessModel model = context.getModel();

    if (!model.getExtraPropDeep(Boolean.class, "apis", "getOne", "isEnabled").orElse(true)) {
      return;
    }

    var populate = model.getExtraPropDeep(Object.class, "apis", "getOne", "populate").orElse(null);
    var populateValidator = new PopulateDefinitionValidator(
        new WhereDefinitionValidator(), () -> new BusinessModelDataModelResolver(model));
    for (String message : populateValidator.validate(model, populate, "接口定义错误: 获取单条 -> 关联模型 -> 填充关联模型")) {
      context.addError(new BusinessDefineApiError(message));
    }
  }
}
