package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.util.UISchemaUtils;

public class UiSchemaUtils {
  public static SchemaNode buildPicker(BusinessModelRelation relation) {
    var label = relation.getTitle();
    var businessModel = relation.getModel();
    var relatedModel = relation.getRelatedModel();
    var pickerTitle = "请选择" + relation.getTitle();
    var formItem = UISchemaUtils.createFormItemNode("picker", relation.getName(), label, pickerTitle);
    var size = relatedModel.getFields().size() < 4 ? "md" : relatedModel.getFields().size() > 8 ? "xl" : "lg";
    var pickApiBaseUrl = BusinessModelUtil.buildKebabCasePath(relatedModel);
    var pickApi = "/api" + pickApiBaseUrl;
    var referenceKey = Optional.ofNullable(relation.getReferenceKey())
        .filter(StringUtils::isNotBlank)
        .orElse("id");

    // 剔除关联字段，避免循环引用
    var omit = relatedModel.getRelations()
        .stream()
        .filter(r -> r.getRelatedModelName().equals(businessModel.getName()))
        .map(BusinessModelRelation::getName)
        .collect(Collectors.toList());

    var isCustomPicker = relation.getExtraPropDeep(Boolean.class, "selector", "isCustomPicker").orElse(false);
    var isCustomPickerFilter = isCustomPicker && relation.getExtraPropDeep(Boolean.class, "selector", "isCustomPickerFilters").orElse(false);

    var displayColumns = isCustomPicker
        ? relation.getExtraPropDeep(List.class, "selector", "customDisplayColumns").orElseGet(Collections::emptyList)
        : relation.getRelatedModel().getExtraPropDeep(List.class, "views", "index", "displayColumns").orElseGet(Collections::emptyList);
    var defaultFilters = isCustomPickerFilter
        ? relation.getExtraPropDeep(List.class, "selector", "customPickerFilters").orElseGet(Collections::emptyList)
        : relation.getRelatedModel().getExtraPropDeep(List.class, "views", "index", "defaultFilters").orElseGet(Collections::emptyList);

    var pickerSchema = CrudUiSchemaModelPrototype.get(relatedModel)
        .configure("isAuto", true)
        .configure("showHeaderToolbarActions", false)
        .configure("isExportCurrentPageEnabled", false)
        .configure("isExportAllEnabled", false)
        .configure("showOperations", false)
        .configure("defaultFilters", defaultFilters)
        .configure("displayColumns", displayColumns)
        .configure("omit", omit)
        .getModel();


    relation.getExtraPropDeep(Map.class, "selector", "defaultRequestParams")
        .filter(m -> !m.isEmpty())
        .ifPresent(defaultParams -> {
          pickerSchema.setProp("defaultParams", defaultParams);
        });

    var labelField = relatedModel.getFields()
        .stream()
        .filter(BusinessModelField::getIsDisplayField)
        .findFirst()
        .map(BusinessModelField::getName)
        .orElse(referenceKey);
    formItem.setProp("valueField", referenceKey);
    formItem.setProp("labelField", labelField);
    formItem.setProp("multiple", false);
    formItem.setProp("source", pickApi);
    formItem.setProp("pickerSchema", pickerSchema);
    formItem.setProp("size", size);
    formItem.setProp("label", label);
    formItem.setProp("clearable", true);
    return formItem;
  }

  public static SchemaNode buildSelect(BusinessModelRelation relation) {
    var label = relation.getTitle();
    var relatedModel = relation.getRelatedModel();
    var selectTitle = "请选择" + relation.getTitle();
    var formItem = UISchemaUtils.createFormItemNode("select", relation.getName(), label, selectTitle);

    var selectApiBaseUrl = BusinessModelUtil.buildKebabCasePath(relatedModel);
    var selectApiUrl = "get:/api" + selectApiBaseUrl;
    var selectApi = new ApiConfig(selectApiUrl);
    var referenceKey = Optional.ofNullable(relation.getReferenceKey())
        .filter(StringUtils::isNotBlank)
        .orElse("id");

    var labelField = relatedModel.getFields()
        .stream()
        .filter(BusinessModelField::getIsDisplayField)
        .findFirst()
        .map(BusinessModelField::getName)
        .orElse(referenceKey);

    relation.getExtraPropDeep(Map.class, "selector", "defaultRequestParams")
        .filter(m -> !m.isEmpty())
        .ifPresent(selectApi::setData);

    formItem.setProp("valueField", referenceKey);
    formItem.setProp("labelField", labelField);
    formItem.setProp("multiple", false);
    formItem.setProp("source", selectApi);
    formItem.setProp("label", label);
    formItem.setProp("searchable", true);
    formItem.setProp("clearable", true);
    return formItem;
  }

  public static SchemaNode buildRelationFormItem(BusinessModelRelation relation) {
    var selectorType = relation.getExtraProp(String.class, "selectorType", "picker");
    var item = "select".equalsIgnoreCase(selectorType)
        ? buildSelect(relation)
        : buildPicker(relation);
    item.setProp("required", relation.getIsRequired());
    return item;
  }

  public static FormItemNode buildInputFormItem(BusinessModelField field, String label) {
    // 先取模型字段上的自定义表单项配置
    var customItem = field.getCustomInput();
    if (customItem != null && !customItem.isEmpty()) {
      var input = new FormItemNode();
      input.putProps(customItem);
      input.setName(field.getName());
      input.setRequired(field.getIsRequired());
      if (StringUtils.isEmpty(input.getLabel())) {
        input.setLabel(label);
      }
      return input;
    }

    // 如果没有自定义表单项配置，则取字段类型对应的默认表单项
    var formItem = BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
        .map(compiledField -> compiledField.toInputFormItem())
        .map(BusinessModelFieldTypeSupport::copyFormItem)
        .orElseGet(() -> UISchemaUtils.createFormItemNode("input-text", field.getName(), field.getTitle()));

    formItem.setName(field.getName());
    if (formItem.getRequired() == null) {
      formItem.setRequired(field.getIsRequired());
    }
    formItem.setLabel(label);
    return formItem;
  }

  public static FormItemNode buildDisplayFormItem(BusinessModelField field, String label) {
    // 先取模型字段上的自定义表单项配置
    var customItem = field.getCustomDisplay();
    if (customItem != null && !customItem.isEmpty()) {
      var input = new FormItemNode();
      input.putProps(customItem);
      input.setName(field.getName());
      input.setRequired(field.getIsRequired());
      if (StringUtils.isEmpty(input.getLabel())) {
        input.setLabel(label);
      }
      return input;
    }

    // 如果没有自定义表单项配置，则取字段类型对应的默认表单项
    var formItem = BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
        .map(compiledField -> compiledField.toDisplayFormItem())
        .map(BusinessModelFieldTypeSupport::copyFormItem)
        .orElseGet(() -> UISchemaUtils.createFormItemNode("static-text", field.getName(), field.getTitle()));

    formItem.setName(field.getName());
    formItem.setRequired(field.getIsRequired());
    formItem.setLabel(label);
    return formItem;
  }
}
