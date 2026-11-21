package com.ouroboros.businessmodel.validation.view;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.ModelDetailValidator;
import com.ouroboros.businessmodel.validation.ValidationContext;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineViewError;

public class EditViewValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    DefaultBusinessModel model = context.getModel();
    List<BusinessModelField> fields = model.getFields();

    // 当模型没有字段时，不进行校验
    if (ObjectUtils.isEmpty(model.getFields()) && ObjectUtils.isEmpty(model.getRelations())) {
      return;
    }

    if (!model.getExtraPropDeep(Boolean.class, "views.edit.isEnabled").orElse(false)) {
      return;
    }

    // 已选择的字段
    List<Map<String, Object>> useFields = model.getExtraPropDeep(List.class, "views", "edit", "fields")
        .orElse(Collections.emptyList());

    // 自定义字段
    List<Map<String, Object>> customFields = useFields.stream()
        .filter(field -> Objects.equals(field.get("type"), "field"))
        .collect(Collectors.toList());
    List<Map<String, Object>> invalidCustomFields = customFields.stream()
        .filter(field -> fields.stream().noneMatch(f -> Objects.equals(field.get("name"), f.getName())))
        .collect(Collectors.toList());
    if (!invalidCustomFields.isEmpty()) {
      context.addError(new BusinessDefineViewError(
          "视图配置定义错误: 编辑表单 -> 表单布局 -> 表单字段: 选择了不存在的字段: " + String.join(",",
              invalidCustomFields.stream()
                  .map(field -> (String) field.get("name"))
                  .collect(Collectors.toList()))
      ));
    }

    // 关联字段
    List<Map<String, Object>> relationFields = useFields.stream()
        .filter(field -> Objects.equals(field.get("type"), "relation"))
        .collect(Collectors.toList());
    List<Map<String, Object>> invalidRelationFields = relationFields.stream()
        .filter(field -> !model.getRelation((String) field.get("name")).isPresent())
        .collect(Collectors.toList());
    if (!invalidRelationFields.isEmpty()) {
      context.addError(new BusinessDefineViewError(
          "视图配置定义错误: 编辑表单 -> 表单布局 -> 表单字段: 选择了不存在的关联模型: " + String.join(",",
              invalidRelationFields.stream()
                  .map(field -> (String) field.get("name"))
                  .collect(Collectors.toList()))
      ));
    }
  }
}
