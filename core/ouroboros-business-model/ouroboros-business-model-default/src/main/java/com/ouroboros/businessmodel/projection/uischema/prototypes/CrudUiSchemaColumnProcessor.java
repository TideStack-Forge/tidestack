package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uischema.schema.*;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Json;

class CrudUiSchemaColumnProcessor {
  private final DefaultBusinessModel businessModel;
  private final Map<String, Object> config;

  CrudUiSchemaColumnProcessor(DefaultBusinessModel businessModel, Map<String, Object> config) {
    this.businessModel = businessModel;
    this.config = config;
  }

  void preprocessColumns() {
    List<Tuple2<Map<String, Object>, ?>> labelNFieldList = getDisplayColumns().stream()
        .map(column -> {
          var type = Optional.ofNullable(column.get("type"))
              .map(Object::toString)
              .orElse("field");
          var name = Optional.ofNullable(column.get("name"))
              .map(Object::toString)
              .orElse("");
          var obj = "field".equals(type)
              ? businessModel.getField(name).orElse(null)
              : "relation".equals(type)
              ? businessModel.getRelation(name).orElse(null)
              : column;
          return Tuple.of(column, obj);
        })
        .filter(tuple -> tuple._2() != null)
        .collect(Collectors.toList());

    // 如果没有配置字段，自动从业务模型上取字段
    if (labelNFieldList.isEmpty()) {
      var fields = businessModel.getFields()
          .stream()
          .map(field -> Tuple.of(Collections.singletonMap("name", (Object) field.getName()), field))
          .collect(Collectors.toList());
      var relations = businessModel.getRelations()
          .stream()
          .map(relation -> Tuple.of(Collections.singletonMap("name", (Object) relation.getName()), relation))
          .collect(Collectors.toList());
      labelNFieldList.addAll(fields);
      labelNFieldList.addAll(relations);
      // 添加基本审计字段
      if (businessModel.getExtraPropDeep(Boolean.class, "features", "isEnabledAuditFields").orElse(false)) {
        var staticDateSchema = new HashMap<String, Object>();
        var userRefSchema = new HashMap<String, Object>();

        var createdAtConfig = new HashMap<String, Object>();
        var createdByConfig = new HashMap<String, Object>();
        var updatedAtConfig = new HashMap<String, Object>();
        var updatedByConfig = new HashMap<String, Object>();

        staticDateSchema.put("type", "static-date");
        staticDateSchema.put("format", "YYYY-MM-DD HH:mm:ss");
        staticDateSchema.put("sortable", true);

        userRefSchema.put("type", "user-ref");

        createdAtConfig.put("type", "builtin");
        createdAtConfig.put("schema", staticDateSchema);
        createdAtConfig.put("title", "创建时间");
        createdAtConfig.put("name", "createdAt");

        updatedAtConfig.put("type", "builtin");
        updatedAtConfig.put("schema", staticDateSchema);
        updatedAtConfig.put("title", "更新时间");
        updatedAtConfig.put("name", "updatedAt");

        createdByConfig.put("type", "builtin");
        createdByConfig.put("schema", userRefSchema);
        createdByConfig.put("title", "创建人");
        createdByConfig.put("name", "createdBy");

        updatedByConfig.put("type", "builtin");
        updatedByConfig.put("schema", userRefSchema);
        updatedByConfig.put("title", "更新人");
        updatedByConfig.put("name", "updatedBy");

        labelNFieldList.add(Tuple.of(createdAtConfig, createdAtConfig));
        labelNFieldList.add(Tuple.of(createdByConfig, createdByConfig));
        labelNFieldList.add(Tuple.of(updatedAtConfig, updatedAtConfig));
        labelNFieldList.add(Tuple.of(updatedByConfig, updatedByConfig));
      }
    }

    // 将配置的label写入column，形成columnList
    var columns = labelNFieldList.stream()
        .filter(filterOmit(t -> t._1.get("name") instanceof String str ? str : null))
        .map(tuple -> {
          var label = Optional.ofNullable(tuple._1().get("title"))
              .map(Object::toString)
              .orElse("");
          var customSchema = Optional.ofNullable(tuple._1().get("schema"))
              .filter(Map.class::isInstance)
              .map(Map.class::cast)
              .orElseGet(Collections::emptyMap);
          var columnProps = Optional.ofNullable(tuple._1().get("columnProps"))
              .filter(Map.class::isInstance)
              .map(m -> (Map<?, ?>) m)
              .orElseGet(Collections::emptyMap)
              .entrySet()
              .stream()
              .filter(e -> e.getKey() instanceof String)
              .filter(e -> e.getValue() != null && (!(e.getValue() instanceof String sv) || !sv.isEmpty()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2));
          var obj = tuple._2();
          var column = obj instanceof BusinessModelField field
              ? buildFieldColumn(field)
              : obj instanceof BusinessModelRelation
              ? buildRelationColumn((BusinessModelRelation) obj)
              : buildCustomColumn((Map<String, Object>) obj);
          column.putProps(customSchema);
          column.putProps((Map<String, Object>) columnProps);
          if (StringUtils.isEmpty(column.getName())) {
            column.setName(ObjectUtils.toString(tuple._1.get("name"), () -> null));
          }
          if (StringUtils.isNotBlank(label)) {
            column.setProp("label", label);
          }
          return column;
        })
        .collect(Collectors.toList());
    config.put("columns", columns);
  }

  private List<Map<String, Object>> getDisplayColumns() {
    return Optional.ofNullable(config.get("displayColumns"))
        .filter(List.class::isInstance)
        .map(l -> (List<Map<String, Object>>) l)
        .orElseGet(Collections::emptyList);
  }

  private Boolean isWorkflowEnabled() {
    return Optional.ofNullable(config.get("workflowEnabled"))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private String getStatusField() {
    return Optional.ofNullable(config.get("statusField"))
        .map(String::valueOf)
        .orElse("status");
  }

  private Map<String, Map<String, Object>> getStatusMap() {
    return Optional.ofNullable(config.get("statusMap"))
        .filter(Map.class::isInstance)
        .map(m -> (Map<String, Map<String, Object>>) m)
        .orElseGet(Collections::emptyMap);
  }

  /**
   * 根据业务字段构建列
   */
  private SchemaNode buildFieldColumn(BusinessModelField field) {
    var label = field.getTitle();

    var customDisplay = field.getCustomDisplay();
    if (customDisplay != null) {
      var displayComponent = Json.toBean(FormItemNode.class, Json.toJsonString(customDisplay));
      if (StringUtils.isBlank(displayComponent.getName())) {
        displayComponent.setName(field.getName());
      }
      if (StringUtils.isBlank(displayComponent.getLabel())) {
        displayComponent.setLabel(label);
      }
      displayComponent.setProp("sortable", true);
      return displayComponent;
    }

    // 如果启用了工作流且是状态字段，则替换显示类型
    if (isWorkflowEnabled() && getStatusField().equals(field.getName())) {
      var statusColumn = new SchemaNode();
      var statusMap = new LinkedHashMap<String, String>();

      statusColumn.setType("mapping");
      statusColumn.setName(field.getName());
      statusColumn.setProp("label", label);
      statusColumn.setProp("map", statusMap);

      // 转换状态表为 mapping
      getStatusMap().forEach((status, customStatus) -> {
        String code = Objects.toString(customStatus.get("code"));
        String statusLabel = Objects.toString(customStatus.get("label"));
        if (!statusMap.containsKey(code)) {
          statusMap.put(code, statusLabel);
        }
      });

      return statusColumn;
    }

    // 如果有自定义输入组件，则使用自定义输入组件的static格式
    var customInput = field.getCustomInput();
    if (customInput != null) {
      var inputComponent = Json.toBean(FormItemNode.class, Json.toJsonString(customInput));
      if (StringUtils.isBlank(inputComponent.getName())) {
        inputComponent.setName(field.getName());
      }
      if (StringUtils.isBlank(inputComponent.getLabel())) {
        inputComponent.setLabel(label);
      }
      inputComponent.setStatic(true);
      inputComponent.setProp("sortable", true);
      return inputComponent;
    }

    var inputComponent = BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
        .map(compiledField -> compiledField.toDisplayFormItem())
        .map(BusinessModelFieldTypeSupport::copyFormItem)
        .orElse(null);
    if (inputComponent != null) {
      inputComponent.setName(field.getName());
      inputComponent.setLabel(label);
      inputComponent.setProp("sortable", true);
      // TODO: 为了解决数据字典输出json的问题，现在先硬编码 pristine
      if ("数据字典".equals(field.getType())) {
        var pristine = Collections.singletonMap("tpl", "${" + field.getName() + ".label}");
        inputComponent.setProp("pristine", pristine);
      }
      return inputComponent;
    }

    // 默认展示
    var column = new HashMap<String, Object>();
    column.put("name", field.getName());
    column.put("label", label);
    column.put("sortable", true);
    return new SchemaNode(column);
  }

  /**
   * 根据关联字段构建列
   */
  private SchemaNode buildRelationColumn(BusinessModelRelation relation) {
    final var childRelationTypes = Arrays.asList("oneToMany", "child");
    if (childRelationTypes.contains(relation.getType())) {
      return buildCollectionColumn(relation);
    }
    return buildModelColumn(relation);
  }


  // TODO: 删掉
  private FormNode buildFormLayout(Map<String, Object> config) {
    String layoutType = Converter.toString(config.get("layoutType"));
    Integer labelWidth = Converter.toInteger(config.get("labelWidth"));

    var formNode = new FormNode();
    var messages = new HashMap<String, String>();

    messages.put("saveSuccess", "保存成功");
    formNode.setMessages(messages);

    if ("normal".equals(layoutType)) {
      formNode.setMode(layoutType);
      return formNode;
    }
    formNode.setMode("horizontal");
    formNode.setLabelWidth(labelWidth);
    return formNode;
  }

  /**
   * 构建关联字段-模型字段列
   *
   * @param relation
   * @return
   */
  private SchemaNode buildModelColumn(BusinessModelRelation relation) {
    var relatedModel = relation.getRelatedModel();
    var relationName = relation.getName();
    var relationTitle = relation.getTitle();
    var referenceKey = Optional.ofNullable(relation.getReferenceKey())
        .filter(StringUtils::isNotBlank)
        .orElse("id");

    var relatedModelBasePath = BusinessModelUtil.buildKebabCasePath(relatedModel);
    var relatedModelDisplayName = relatedModel.getFields()
        .stream()
        .filter(BusinessModelField::getIsDisplayField)
        .findFirst()
        .map(BusinessModelField::getName)
        .orElse(referenceKey);
    var actionLabelTpl = "$'{' {0} && {0} !== __super.{0} && {0}.id ? {0}.{1} : ''查看'' '}'";
    var actionIcon = "fa fa-eye";

    var columnNode = new CommonContainer();
    var noDataTpl = new SchemaNode();
    var columnAction = new ActionNode();
    var columnActionDialog = new CommonContainer();


    columnNode.setType("container");
    columnNode.setProp("label", relationTitle);
    columnNode.setBody(Arrays.asList(noDataTpl, columnAction));

    columnAction.setLabel(MessageFormat.format(actionLabelTpl, relationName, relatedModelDisplayName));
    columnAction.setLevel("link");
    columnAction.setIcon(actionIcon);
    columnAction.setActionType("dialog");
    columnAction.setProp("dialog", columnActionDialog);
    columnAction.setVisibleOn("${" + relationName + "}");

    noDataTpl.setType("tpl");
    noDataTpl.setProp("tpl", "-");
    noDataTpl.setHiddenOn("${" + relationName + "}");

    columnActionDialog.setType("dialog");
    columnActionDialog.setProp("size", "md");
    columnActionDialog.setProp("title", "查看" + relationTitle);
    columnActionDialog.setProp("data", new HashMap<>() {{
      put(referenceKey, MessageFormat.format("$'{' {0} && {0}.{1} || {0} '}'", relationName, referenceKey));
    }});
    columnActionDialog.setProp("url", relatedModelBasePath + "/detail");

    if (!"id".equalsIgnoreCase(referenceKey)) {
      var initApi = new ApiConfig();
      initApi.setMethod("get");
      initApi.setUrl("/api" + relatedModelBasePath);
      initApi.setData(Collections.singletonMap(referenceKey, "${" + referenceKey + "}"));
      initApi.setResponseData(Collections.singletonMap("&", "${items[0]}"));
      var rewriteProps = new HashMap<String, Object>();
      columnActionDialog.setProp("rewriteProps", rewriteProps);
      rewriteProps.put("initApi", initApi);
    }

    return columnNode;
  }

  /**
   * 构建关联字段-集合字段列
   *
   * @param relation
   * @return
   */
  private SchemaNode buildCollectionColumn(BusinessModelRelation relation) {
    var relatedModel = relation.getRelatedModel();
    var relationName = relation.getName();
    var relationTitle = relation.getTitle();

    var actionLabelTpl = "$'{' {0} && {0} !== __super.{0} && {0}.length != null ? ''数量('' + {0}.length + '')'' : ''查看'' '}'";
    var actionIcon = "fa fa-table-list";
    var relatedIndexConfig = relatedModel.getExtraPropDeep(Map.class, "views", "index").orElse(Collections.emptyMap());
    var referenceKey = relation.getReferenceKey();
    var additionalParamKey = referenceKey != null ? referenceKey : businessModel.getName();

    var columnNode = new CommonContainer();
    var columnAction = new ActionNode();
    var columnActionDialog = new CommonContainer();

    columnNode.setType("container");
    columnNode.setProp("label", relationTitle);
    columnNode.setBody(columnAction);

    columnAction.setActionType("dialog");
    columnAction.setIcon(actionIcon);
    columnAction.setLevel("link");
    columnAction.setLabel(MessageFormat.format(actionLabelTpl, relationName));
    columnAction.setProp("dialog", columnActionDialog);

    columnActionDialog.setType("dialog");
    columnActionDialog.setProp("title", "查看" + relationTitle);
    columnActionDialog.setProp("size", "lg");

    // 剔除相关关联字段，避免循环引用
    var omitRelationNames = relatedModel.getRelations()
        .stream()
        .filter(r -> r.getRelatedModelName().equals(businessModel.getFullName()))
        .filter(r -> "child".equals(r.getType()) || r.getName().equals(referenceKey))
        .map(BusinessModelRelation::getName)
        .collect(Collectors.toList());

    var crud = CrudUiSchemaModelPrototype.get(relatedModel)
        .configure(relatedIndexConfig)
        .configure("isAuto", true)
        .configure("additionalParams", Collections.singletonMap(additionalParamKey, "${ " + relation.getKey() + " }"))
        .configure("affixHeader", false)
        .configure("showHeaderToolbarActions", false)
        .configure("showOperations", false)
        .configure("isExportCurrentPageEnabled", false)
        .configure("isExportAllEnabled", false)
        .configure("omit", omitRelationNames)
        .getModel();
    crud.setProp("filter", null);

    columnActionDialog.setBody(crud);

    return columnNode;
  }

  /**
   * 构建自定义列
   *
   * @param columnConfig
   * @return
   */
  private SchemaNode buildCustomColumn(Map<String, Object> columnConfig) {
    var type = Optional.ofNullable(columnConfig.get("type"))
        .map(Object::toString)
        .orElse("custom");
    var column = new SchemaNode();
    if ("builtin".equals(type)) {
      column.setName(Converter.toString(columnConfig.get("name")));
      column.setProp("label", Converter.toString(columnConfig.getOrDefault("label", columnConfig.get("title"))));
    }
    return column;
  }

  private <T> Predicate<T> filterOmit(Function<T, String> nameGetter) {
    var omit = Optional.ofNullable(config.get("omit"))
        .filter(List.class::isInstance)
        .map(List.class::cast)
        .map(list -> ((List<?>) list).stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toSet()))
        .orElseGet(Collections::emptySet);
    if (omit.isEmpty()) {
      return e -> true;
    }
    return e -> !omit.contains(nameGetter.apply(e));
  }
}
