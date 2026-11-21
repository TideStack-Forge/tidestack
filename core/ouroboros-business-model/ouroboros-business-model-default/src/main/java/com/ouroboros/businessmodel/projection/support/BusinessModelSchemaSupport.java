package com.ouroboros.businessmodel.projection.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.projection.data.basic.BasicMPS;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeTemplateResolver;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport;

public final class BusinessModelSchemaSupport {
  private static final BasicMPS BASIC_MPS = new BasicMPS();
  private static final FieldTypeTemplateResolver FIELD_TYPE_TEMPLATE_RESOLVER = new FieldTypeTemplateResolver();

  private BusinessModelSchemaSupport() {
  }

  public static SchemaModel createRequestSchema(DefaultBusinessModel model) {
    return buildModelSchema(model, SchemaUsage.CREATE_REQUEST, new ArrayList<String>(), false);
  }

  public static SchemaModel updateRequestSchema(DefaultBusinessModel model) {
    return buildModelSchema(model, SchemaUsage.UPDATE_REQUEST, new ArrayList<String>(), false);
  }

  public static SchemaModel responseSchema(DefaultBusinessModel model) {
    return buildModelSchema(model, SchemaUsage.RESPONSE, new ArrayList<String>(), false);
  }

  public static Map<String, Object> createValidationSchema(DefaultBusinessModel model) {
    return toValidationSchemaMap(createRequestSchema(model));
  }

  public static Map<String, Object> updateValidationSchema(DefaultBusinessModel model) {
    return toValidationSchemaMap(updateRequestSchema(model));
  }

  public static SchemaModel primaryKeySchema(DefaultBusinessModel model) {
    var fieldMetaByName = fieldMetaByName(model);
    return model.getFields().stream()
        .filter(BusinessModelField::getIsPrimaryKey)
        .findFirst()
        .map(field -> buildFieldSchema(field, fieldMetaByName.get(field.getName()), SchemaUsage.RESPONSE))
        .orElseGet(() -> SchemaModel.builder().stringType().build());
  }

  public static String primaryKeyDescription(DefaultBusinessModel model) {
    return model.getFields().stream()
        .filter(BusinessModelField::getIsPrimaryKey)
        .findFirst()
        .map(field -> firstNonBlank(field.getDescription(), field.getTitle(), "主键"))
        .orElse("主键");
  }

  private static Map<String, Object> toValidationSchemaMap(SchemaModel schema) {
    return SchemaModelJsonSchemaSupport.toJsonSchemaMap(schema);
  }

  private static SchemaModel buildModelSchema(DefaultBusinessModel model, SchemaUsage usage, List<String> visiting,
                                              boolean childCollectionItem) {
    var visitKey = model.getFullName() + ":" + usage.name();
    if (visiting.contains(visitKey)) {
      return SchemaModel.builder()
          .objectType()
          .additionalProperties(Boolean.TRUE)
          .build();
    }

    visiting.add(visitKey);
    try {
      var fieldMetaByName = fieldMetaByName(model);
      var properties = new LinkedHashMap<String, SchemaModel>();
      var required = new ArrayList<String>();
      var createViewFieldNames = resolveCreateViewFieldNames(model, usage, childCollectionItem);

      for (BusinessModelField field : model.getFields()) {
        var fieldMeta = fieldMetaByName.get(field.getName());
        var fieldSchema = buildFieldSchema(field, fieldMeta, usage);
        properties.put(field.getName(), fieldSchema);
        if (shouldRequireField(field, fieldMeta, usage, createViewFieldNames)) {
          required.add(field.getName());
        }
      }

      for (BusinessModelRelation relation : model.getRelations()) {
        var relationSchema = buildRelationSchema(relation, usage, visiting, childCollectionItem);
        if (relationSchema == null) {
          continue;
        }
        properties.put(relation.getName(), relationSchema);
        if (shouldRequireRelation(relation, usage, childCollectionItem, createViewFieldNames)) {
          required.add(relation.getName());
        }
      }

      var builder = SchemaModel.builder()
          .objectType()
          .title(model.getTitle())
          .description(firstNonBlank(model.getDescription(), model.getTitle()))
          .properties(properties);

      if (usage.acceptsAdditionalProperties()) {
        builder.additionalProperties(Boolean.TRUE);
      }
      if (!required.isEmpty()) {
        builder.required(required);
      }
      return builder.build();
    } finally {
      visiting.remove(visitKey);
    }
  }

  private static Map<String, DataModelFieldMeta> fieldMetaByName(DefaultBusinessModel model) {
    return BASIC_MPS.mapFields(model.getFields()).stream()
        .collect(Collectors.toMap(
            DataModelFieldMeta::getName,
            fieldMeta -> fieldMeta,
            (left, right) -> right,
            LinkedHashMap::new
        ));
  }

  private static boolean shouldRequireField(BusinessModelField field, DataModelFieldMeta fieldMeta, SchemaUsage usage,
                                            Optional<Set<String>> createViewFieldNames) {
    if (!usage.includeRequired() || !Boolean.TRUE.equals(field.getIsRequired())) {
      return false;
    }
    if (usage == SchemaUsage.CREATE_REQUEST && Boolean.TRUE.equals(field.getIsPrimaryKey()) && hasGeneratedPrimaryKey(field, fieldMeta)) {
      return false;
    }
    if (hasDefaultValue(field)) {
      return false;
    }
    return isDeclaredInCreateView(field.getName(), createViewFieldNames);
  }

  /**
   * 判断关系是否应进入请求 schema 的 required 集合。
   * <p>作为父表子集合项构建 schema 时，子表的 master 关系（指回父表）不参与必填校验：
   * 该字段由 ChildCollectionSynchronizer 在保存时依据父子关系自动回填，
   * 客户端既不应该也无法提供（此时父记录主键可能尚未生成）。
   * <p>子表通过自身 API 独立创建时（非嵌套上下文）不受影响，master 关系仍按声明校验。
   */
  private static boolean shouldRequireRelation(BusinessModelRelation relation, SchemaUsage usage,
                                               boolean childCollectionItem,
                                               Optional<Set<String>> createViewFieldNames) {
    if (!usage.includeRequired() || !Boolean.TRUE.equals(relation.getIsRequired())) {
      return false;
    }
    if (childCollectionItem && "master".equalsIgnoreCase(relation.getType())) {
      return false;
    }
    return isDeclaredInCreateView(relation.getName(), createViewFieldNames);
  }

  /**
   * 解析新增接口的输入契约字段集合。
   * <p>业务模型显式声明了新增视图字段清单（{@code views.add.fields}）时，该清单就是新增入口提交的字段集合：
   * 平台生成的新增表单只提交清单内的字段，因此请求 schema 不应把清单外的字段算作客户端必填
   * （典型场景：字段由 {@code apis.add.beforeScript} 在服务端补齐）。
   * <p>仅顶层创建请求生效。子集合项随父记录一起提交，其提交字段由父关系的子表配置决定，
   * 与子模型自身的「新增视图」无关，因此嵌套 schema 维持原有的必填声明。
   */
  private static Optional<Set<String>> resolveCreateViewFieldNames(DefaultBusinessModel model, SchemaUsage usage,
                                                                   boolean childCollectionItem) {
    if (usage != SchemaUsage.CREATE_REQUEST || childCollectionItem) {
      return Optional.empty();
    }
    var viewFields = model.getExtraPropDeep(List.class, "views", "add", "fields");
    if (viewFields.isEmpty()) {
      return Optional.empty();
    }
    Set<String> names = toFieldNames((List<?>) viewFields.get());
    return names.isEmpty() ? Optional.empty() : Optional.of(names);
  }

  private static Set<String> toFieldNames(List<?> viewFields) {
    Set<String> names = new LinkedHashSet<>();
    for (Object viewField : viewFields) {
      if (!(viewField instanceof Map<?, ?> viewFieldConfig)) {
        continue;
      }
      var name = viewFieldConfig.get("name");
      if (name != null && !String.valueOf(name).trim().isEmpty()) {
        names.add(String.valueOf(name));
      }
    }
    return names;
  }

  /**
   * 判断字段/关系是否属于新增入口提交的字段集合；模型未声明新增视图字段清单时不作限制。
   */
  private static boolean isDeclaredInCreateView(String name, Optional<Set<String>> createViewFieldNames) {
    return createViewFieldNames.map(names -> names.contains(name)).orElse(Boolean.TRUE);
  }

  /**
   * 判断字段是否配置了默认值。
   * <p>口径与 {@code DefaultDataModelField#getDefaultValue()} 一致：空白字符串等于没有默认值。
   * 数据层插入时会用该默认值补齐缺失字段（{@code AbstractDataModel#buildInsertRecord}），
   * 因此这类字段不需要客户端提交。
   */
  private static boolean hasDefaultValue(BusinessModelField field) {
    return field != null && StringUtils.isNotBlank(field.getDefaultValue());
  }

  private static boolean hasGeneratedPrimaryKey(BusinessModelField field, DataModelFieldMeta fieldMeta) {
    if (fieldMeta != null && Boolean.TRUE.equals(fieldMeta.getIsAutoIncrement())) {
      return true;
    }
    return BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
        .map(compiledField -> compiledField.getPrimaryKeyGenerator())
        .map(String::trim)
        .filter(generator -> !generator.isEmpty())
        .isPresent();
  }

  private static SchemaModel buildFieldSchema(
      BusinessModelField field,
      DataModelFieldMeta fieldMeta,
      SchemaUsage usage
  ) {
    var specialSchema = buildSpecialFieldSchema(field, fieldMeta, usage);
    if (specialSchema.isPresent()) {
      return specialSchema.get();
    }

    var builder = scalarSchemaBuilder(fieldMeta);
    applyCommonFieldMetadata(builder, field.getTitle(), firstNonBlank(field.getDescription(), field.getTitle()), fieldMeta);
    applyScalarFieldConstraints(builder, fieldMeta, field);
    return relaxNullableEnum(builder.build(), fieldMeta, usage);
  }

  /**
   * 可空枚举补一个 null 分支。
   *
   * <p>nullable 只会在序列化时把 {@code "null"} 追加到 {@code type}，而 JSON Schema 的 {@code enum}
   * 是独立断言：null 能通过 type，却过不了 enum，于是"非必填且未填写"的枚举字段提交时报
   * {@code does not have a value in the enumeration}。</p>
   *
   * <p>不能直接把 null 加进 enum 数组：{@code SchemaModelJsonSchemaSupport#toJsonSchemaMap} 序列化后会
   * {@code removeNulls}，把 List 里的 null 元素整体丢弃。因此改写成
   * {@code anyOf: [非空枚举, {"type": "null"}]}：null 放行，非法值仍按枚举报错。</p>
   *
   * <p>只作用于请求体；响应 schema 不参与入参校验，保持原样。</p>
   */
  private static SchemaModel relaxNullableEnum(SchemaModel schema, DataModelFieldMeta fieldMeta, SchemaUsage usage) {
    if (schema == null || usage == SchemaUsage.RESPONSE) {
      return schema;
    }
    if (fieldMeta == null || !Boolean.TRUE.equals(fieldMeta.getIsNullable())) {
      return schema;
    }
    var enumValues = schema.getEnumValues();
    if (enumValues == null || enumValues.isEmpty()) {
      return schema;
    }

    // 非空分支去掉 nullable，避免 normalizeForOpenAPI31 再追加一次 null 分支
    var nonNullSchema = schema.map(builder -> builder.nullable(null));
    var nullSchema = SchemaModel.builder().type("null").build();

    return SchemaModel.builder()
        .title(schema.getTitle())
        .description(schema.getDescription())
        .defaultValue(schema.getDefaultValue())
        .anyOf(List.of(nonNullSchema, nullSchema))
        .build();
  }

  private static Optional<SchemaModel> buildSpecialFieldSchema(
      BusinessModelField field,
      DataModelFieldMeta fieldMeta,
      SchemaUsage usage
  ) {
    if (fieldMeta == null || fieldMeta.getType() == null) {
      return Optional.empty();
    }

    var propertyName = specialReferenceProperty(fieldMeta.getType()).orElse(null);
    if (propertyName == null) {
      return Optional.empty();
    }

    if (!usage.acceptsScalarOrObjectInput()) {
      return Optional.of(buildLooseObjectSchema(
          field.getTitle(),
          firstNonBlank(field.getDescription(), field.getTitle()),
          fieldMeta));
    }

    return Optional.of(buildScalarOrObjectSchema(
        field.getTitle(),
        firstNonBlank(field.getDescription(), field.getTitle()),
        fieldMeta,
        buildSyntheticScalarSchema(fieldMeta, field, "String"),
        propertyName));
  }

  private static SchemaModel buildRelationSchema(BusinessModelRelation relation, SchemaUsage usage, List<String> visiting,
                                                  boolean childCollectionItem) {
    if ("child".equalsIgnoreCase(relation.getType())) {
      if (!relation.getSubmitTogether()) {
        return null;
      }
      var relatedModel = relation.getRelatedModel();
      if (relatedModel == null) {
        return null;
      }
      return SchemaModel.builder()
          .arrayType()
          .title(relation.getTitle())
          .description(firstNonBlank(relation.getDescription(), relation.getTitle()))
          .items(buildModelSchema(relatedModel, usage.nestedUsage(), visiting, true))
          .build();
    }
    if (!"master".equalsIgnoreCase(relation.getType()) && !"reference".equalsIgnoreCase(relation.getType())) {
      return null;
    }

    var relatedModel = relation.getRelatedModel();
    if (relatedModel == null) {
      return null;
    }

    if (!usage.acceptsScalarOrObjectInput()) {
      return buildModelSchema(relatedModel, usage.nestedUsage(), visiting, false).map(
          builder -> builder.title(relation.getTitle()),
          builder -> builder.description(firstNonBlank(relation.getDescription(), relation.getTitle()))
      );
    }

    var referenceKey = firstNonBlank(relation.getReferenceKey(), "id");
    var referenceFieldMeta = fieldMetaByName(relatedModel).get(referenceKey);
    var referenceField = relatedModel.getField(referenceKey).orElse(null);
    var relationFieldMeta = new DataModelFieldMeta();
    relationFieldMeta.setType(referenceFieldMeta == null ? "String" : referenceFieldMeta.getType());
    relationFieldMeta.setIsNullable(!Boolean.TRUE.equals(relation.getIsRequired()));

    SchemaModel scalarSchema;
    if (referenceFieldMeta == null) {
      scalarSchema = SchemaModel.builder().stringType().build();
    } else {
      scalarSchema = buildScalarOnlySchema(referenceFieldMeta, referenceField);
    }

    return buildScalarOrObjectSchema(
        relation.getTitle(),
        firstNonBlank(relation.getDescription(), relation.getTitle()),
        relationFieldMeta,
        scalarSchema,
        referenceKey).map(builder -> builder.extraProp("x-business-relation", Map.of(
            "modelFullName", relation.getModel().getFullName(), "relationName", relation.getName())));
  }

  private static SchemaModel.Builder scalarSchemaBuilder(DataModelFieldMeta fieldMeta) {
    return baseSchemaBuilder(fieldMeta);
  }

  private static void applyCommonFieldMetadata(
      SchemaModel.Builder builder,
      String title,
      String description,
      DataModelFieldMeta fieldMeta
  ) {
    builder.title(title);
    builder.description(description);
    if (fieldMeta != null && fieldMeta.getIsNullable() != null) {
      builder.nullable(fieldMeta.getIsNullable());
    }
    if (fieldMeta != null && fieldMeta.getDefaultValue() != null) {
      builder.defaultValue(convertDefaultValue(fieldMeta.getType(), fieldMeta.getDefaultValue()));
    }
  }

  private static void applyScalarFieldConstraints(
      SchemaModel.Builder builder,
      DataModelFieldMeta fieldMeta,
      BusinessModelField field
  ) {
    applyDataModelConstraints(builder, fieldMeta);
    if (field != null) {
      applyFieldTypeConstraints(builder, field);
      applyEnumConstraints(builder, field);
    }
  }

  private static SchemaModel buildSyntheticScalarSchema(
      DataModelFieldMeta fieldMeta,
      BusinessModelField field,
      String scalarType
  ) {
    var syntheticMeta = fieldMeta == null ? new DataModelFieldMeta() : fieldMeta.deepCopy();
    syntheticMeta.setType(scalarType);
    return buildScalarOnlySchema(syntheticMeta, field);
  }

  private static SchemaModel buildScalarOnlySchema(DataModelFieldMeta fieldMeta, BusinessModelField field) {
    var builder = scalarSchemaBuilder(fieldMeta);
    applyScalarFieldConstraints(builder, fieldMeta, field);
    return builder.build();
  }

  private static SchemaModel buildScalarOrObjectSchema(
      String title,
      String description,
      DataModelFieldMeta fieldMeta,
      SchemaModel scalarSchema,
      String propertyName
  ) {
    var objectProperties = new LinkedHashMap<String, SchemaModel>();
    objectProperties.put(propertyName, scalarSchema);

    var objectSchema = SchemaModel.builder()
        .objectType()
        .properties(objectProperties)
        .required(Collections.singletonList(propertyName))
        .additionalProperties(Boolean.TRUE)
        .build();

    var oneOf = new ArrayList<SchemaModel>();
    oneOf.add(scalarSchema);
    oneOf.add(objectSchema);
    var builder = SchemaModel.builder().oneOf(oneOf);
    applyCommonFieldMetadata(builder, title, description, fieldMeta);
    return builder.build();
  }

  private static SchemaModel buildLooseObjectSchema(
      String title,
      String description,
      DataModelFieldMeta fieldMeta
  ) {
    var builder = SchemaModel.builder()
        .objectType()
        .additionalProperties(Boolean.TRUE);
    applyCommonFieldMetadata(builder, title, description, fieldMeta);
    return builder.build();
  }

  private static SchemaModel.Builder baseSchemaBuilder(DataModelFieldMeta fieldMeta) {
    if (fieldMeta == null || fieldMeta.getType() == null) {
      return SchemaModel.builder().objectType();
    }

    var type = fieldMeta.getType();
    if (isStringType(type)) {
      var builder = SchemaModel.builder().stringType();
      if ("Date".equalsIgnoreCase(type)) {
        builder.format("date");
      }
      if ("DateTime".equalsIgnoreCase(type)) {
        builder.format("date-time");
      }
      return builder;
    }
    if (isIntegerType(type)) {
      return SchemaModel.builder().integerType();
    }
    if (isNumberType(type)) {
      return SchemaModel.builder().numberType();
    }
    if ("Boolean".equalsIgnoreCase(type)) {
      return SchemaModel.builder().booleanType();
    }
    if ("List".equalsIgnoreCase(type) || "Array".equalsIgnoreCase(type)) {
      return SchemaModel.builder().arrayType();
    }
    if ("Map".equalsIgnoreCase(type) || "Object".equalsIgnoreCase(type)) {
      return SchemaModel.builder().objectType().additionalProperties(Boolean.TRUE);
    }
    return SchemaModel.builder().objectType();
  }

  /**
   * 特殊数据类型的「标量标识属性」：返回非空表示该类型可提交标量（标识值本身）或对象
   * （对象中由该属性承载同一标识值），据此生成 oneOf 校验 schema。
   * <p>文件字段必须在此登记：{@code UploadFileValue#convert} 只接受字符串
   * （文件 URL 或 JSON 串），而该类型的 ValueType 声明为 Map，
   * 不登记时会退化成纯 object schema，客户端提交文件 URL 将被判为类型不符。
   */
  private static Optional<String> specialReferenceProperty(String type) {
    if ("Employee".equalsIgnoreCase(type) || "Organization".equalsIgnoreCase(type)) {
      return Optional.of("id");
    }
    if ("DataDictItem".equalsIgnoreCase(type)) {
      return Optional.of("code");
    }
    if ("UploadFile".equalsIgnoreCase(type)) {
      return Optional.of("url");
    }
    return Optional.empty();
  }

  private static void applyDataModelConstraints(SchemaModel.Builder builder, DataModelFieldMeta fieldMeta) {
    if (fieldMeta == null) {
      return;
    }

    if (isStringType(fieldMeta.getType()) && fieldMeta.getSize() != null) {
      builder.maxLength(fieldMeta.getSize());
    }
    if (isNumberType(fieldMeta.getType()) || isIntegerType(fieldMeta.getType())) {
      getNumber(fieldMeta.getExtraProp("min").orElse(null)).ifPresent(builder::minimum);
      getNumber(fieldMeta.getExtraProp("max").orElse(null)).ifPresent(builder::maximum);
    }
    if (isNumberType(fieldMeta.getType()) && fieldMeta.getDecimalDigits() != null && fieldMeta.getDecimalDigits() > 0) {
      builder.multipleOf(BigDecimal.ONE.movePointLeft(fieldMeta.getDecimalDigits()));
    }
  }

  private static void applyFieldTypeConstraints(SchemaModel.Builder builder, BusinessModelField field) {
    resolveValidations(field).ifPresent(validations -> {
      var matchRegexp = validations.get("matchRegexp");
      if (matchRegexp instanceof String) {
        var regexp = (String) matchRegexp;
        if (!regexp.trim().isEmpty() && !".*".equals(regexp)) {
          builder.pattern(regexp);
        }
      }
      if (Boolean.TRUE.equals(validations.get("isEmail"))) {
        builder.format("email");
      }
      if (Boolean.TRUE.equals(validations.get("isUrl"))) {
        builder.format("uri");
      }
    });

    getNumber(field.getExtraProp("min").orElse(null)).ifPresent(builder::minimum);
    getNumber(field.getExtraProp("max").orElse(null)).ifPresent(builder::maximum);
    field.getExtraProp(String.class, "regexp")
        .filter(regexp -> !regexp.trim().isEmpty() && !".*".equals(regexp))
        .ifPresent(builder::pattern);
  }

  private static void applyEnumConstraints(SchemaModel.Builder builder, BusinessModelField field) {
    if ("多选".equals(field.getType())) {
      return;
    }
    field.getExtraProp(List.class, "options")
        .map(BusinessModelSchemaSupport::toEnumValues)
        .filter(values -> !values.isEmpty())
        .ifPresent(builder::enumValues);
  }

  private static List<Object> toEnumValues(List<?> options) {
    var enumValues = new ArrayList<Object>();
    for (Object option : options) {
      if (option instanceof Map<?, ?>) {
        var optionMap = (Map<?, ?>) option;
        if (optionMap.containsKey("value")) {
          enumValues.add(optionMap.get("value"));
          continue;
        }
      }
      if (option != null) {
        enumValues.add(option);
      }
    }
    return enumValues;
  }

  private static Optional<Map<String, Object>> resolveValidations(BusinessModelField field) {
    return BusinessModelFieldTypeSupport.getRuntimeMetadata(field.getType())
        .map(definition -> resolveMap(definition, definition.getValidations(), field.getExtraProps()))
        .filter(map -> !map.isEmpty());
  }

  private static Map<String, Object> resolveMap(
      FieldTypeDefinition definition,
      Map<String, Object> template,
      Map<String, Object> fieldParams
  ) {
    if (template == null || template.isEmpty()) {
      return Collections.emptyMap();
    }
    var params = new LinkedHashMap<String, Object>();
    params.putAll(Optional.ofNullable(definition.getDefaultParams()).orElseGet(Collections::emptyMap));
    params.putAll(Optional.ofNullable(fieldParams).orElseGet(Collections::emptyMap));
    return FIELD_TYPE_TEMPLATE_RESOLVER.resolveMap(template, params, definition)
        .getOrElse(Collections.<String, Object>emptyMap());
  }

  private static Optional<Number> getNumber(Object value) {
    if (value instanceof Number) {
      return Optional.of((Number) value);
    }
    if (value instanceof String) {
      var stringValue = (String) value;
      if (stringValue.trim().isEmpty()) {
        return Optional.empty();
      }
      try {
        return Optional.of(new BigDecimal(stringValue));
      } catch (NumberFormatException parseFailure) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static Object convertDefaultValue(String type, String defaultValue) {
    if (defaultValue == null) {
      return null;
    }
    try {
      if (isIntegerType(type)) {
        return Long.valueOf(defaultValue);
      }
      if (isNumberType(type)) {
        return new BigDecimal(defaultValue);
      }
      if ("Boolean".equalsIgnoreCase(type)) {
        return Boolean.valueOf(defaultValue);
      }
    } catch (RuntimeException conversionFailure) {
      return defaultValue;
    }
    return defaultValue;
  }

  private static boolean isStringType(String type) {
    return "String".equalsIgnoreCase(type)
        || "Snowflake".equalsIgnoreCase(type)
        || "Date".equalsIgnoreCase(type)
        || "DateTime".equalsIgnoreCase(type);
  }

  private static boolean isIntegerType(String type) {
    return "Integer".equalsIgnoreCase(type)
        || "Long".equalsIgnoreCase(type)
        || "Short".equalsIgnoreCase(type)
        || "Byte".equalsIgnoreCase(type);
  }

  private static boolean isNumberType(String type) {
    return "Double".equalsIgnoreCase(type)
        || "Float".equalsIgnoreCase(type)
        || "Decimal".equalsIgnoreCase(type)
        || "BigDecimal".equalsIgnoreCase(type);
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private enum SchemaUsage {
    CREATE_REQUEST,
    UPDATE_REQUEST,
    RESPONSE;

    private boolean includeRequired() {
      return this == CREATE_REQUEST;
    }

    private boolean acceptsAdditionalProperties() {
      return this != RESPONSE;
    }

    private boolean acceptsScalarOrObjectInput() {
      return this != RESPONSE;
    }

    private SchemaUsage nestedUsage() {
      if (this == RESPONSE) {
        return RESPONSE;
      }
      if (this == UPDATE_REQUEST) {
        return UPDATE_REQUEST;
      }
      return CREATE_REQUEST;
    }
  }
}
