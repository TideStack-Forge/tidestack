package com.ouroboros.businessmodel.validation;

import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFieldError;

public class FieldsValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    DefaultBusinessModel model = context.getModel();

    // 如果为定义字段和关系则不进行校验
    if (model.getFields().isEmpty() && model.getRelations().isEmpty()) {
      return;
    }

    if (model.getFields().stream().noneMatch(BusinessModelField::getIsPrimaryKey)) {
      context.addError(new BusinessDefineFieldError("字段配置错误：未设置主键"));
    }

    if (model.getFields().stream().filter(BusinessModelField::getIsPrimaryKey).count() > 1) {
      context.addError(new BusinessDefineFieldError("字段配置错误：暂不支持多主键模式"));
    }

    if (model.getFields().stream().noneMatch(field -> field.getIsPrimaryKey() && Objects.equals(field.getName(), "id"))) {
      context.addError(new BusinessDefineFieldError("字段配置错误：主键字段名称必须为id"));
    }

    var invalidFieldTypes = model.getFields().stream()
        .filter(field -> !BusinessModelFieldTypeSupport.getRuntimeMetadata(field.getType()).isPresent())
        .collect(Collectors.toList());
    if (!invalidFieldTypes.isEmpty()) {
      context.addError(new BusinessDefineFieldError(String.format(
          "字段配置错误：%s",
          invalidFieldTypes.stream().map(field ->
              String.format("字段: %s, 类型: %s无效", field.getName(), field.getType())
          ).collect(Collectors.joining(", ", "", "！"))
      )));
    }
  }
}
