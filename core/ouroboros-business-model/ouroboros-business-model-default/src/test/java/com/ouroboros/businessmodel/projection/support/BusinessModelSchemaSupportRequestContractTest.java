package com.ouroboros.businessmodel.projection.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import io.vavr.control.Either;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;

/**
 * 覆盖业务模型新增请求 schema 中「文件字段」的入参契约。
 * <p>文件类型的取值转换（{@code UploadFileValue#convert}）只接受字符串，而该类型的
 * ValueType 声明为 Map；schema 若不接受标量，客户端按上传结果提交文件 URL 会被判为类型不符
 * （实测报「type @ /contractFile: string found, [object, null] expected」）。
 * <p>其中 {@code requiredFieldWithoutDefaultShouldStayRequired} 作为对照，
 * 确保该 schema 并非一味放宽。必填字段的默认值语义由
 * {@code BusinessModelSchemaSupportCreateRequiredTest} 覆盖。
 */
class BusinessModelSchemaSupportRequestContractTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String FILE_URL = "http://file-host/upload/2.png";

  private Object previousApplicationContext;

  @BeforeEach
  void bindFieldTypeCatalog() throws Exception {
    previousApplicationContext = readApplicationContext();
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", catalog());
    writeApplicationContext(applicationContext);
  }

  @AfterEach
  void restoreFieldTypeCatalog() throws Exception {
    writeApplicationContext(previousApplicationContext);
  }

  @Test
  void requiredFieldWithoutDefaultShouldStayRequired() {
    var schema = createSchema();

    assertTrue(requiredOf(schema).contains("requiredeeNo"),
        "无默认值的必填字段仍应校验，实际 required=" + requiredOf(schema));
  }

  @Test
  void snowflakePrimaryKeyShouldAcceptStringInRequestsAndResponses() {
    String id = "1989063703355392001";
    assertValid(record("id", id, "requiredeeNo", "R1"), "雪花主键应接受字符串");
    var updateErrors = validate(BusinessModelSchemaSupport.updateValidationSchema(model()), record("id", id));
    assertTrue(updateErrors.isEmpty(), updateErrors.toString());

    var idSchema = com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport.toJsonSchemaMap(
        BusinessModelSchemaSupport.primaryKeySchema(model()));
    var responseSchema = Map.<String, Object>of("type", "object", "properties", Map.of("id", idSchema));
    assertTrue(validate(responseSchema, record("id", id)).isEmpty());
    assertFalse(validate(responseSchema, record("id", Map.of())).isEmpty());
    assertFalse(validate(responseSchema, record("id", 1989063703355392001L)).isEmpty());
    assertFalse(validate(responseSchema, record("id", "1".repeat(20))).isEmpty());
  }

  @Test
  void fileFieldShouldAcceptScalarUrl() {
    assertValid(record("requiredeeNo", "R1", "contractFile", FILE_URL),
        "文件字段应接受 URL 字符串");
  }

  @Test
  void fileFieldShouldAcceptFileObject() {
    var fileObject = new LinkedHashMap<String, Object>();
    fileObject.put("url", FILE_URL);
    fileObject.put("name", "2.png");

    assertValid(record("requiredeeNo", "R1", "contractFile", fileObject),
        "文件字段应接受带 url 的文件对象");
  }

  @Test
  void nullableEnumShouldAcceptNull() {
    assertValid(record("requiredeeNo", "R1", "nullableStatus", null),
        "非必填枚举未填写（null）时应通过校验");
  }

  @Test
  void nullableEnumShouldRejectUnknownValue() {
    assertInvalid(record("requiredeeNo", "R1", "nullableStatus", "UNKNOWN"),
        "非必填枚举仍应拒绝不在选项内的取值");
  }

  @Test
  void nullableEnumShouldAcceptNullOnUpdate() {
    var errors = validate(BusinessModelSchemaSupport.updateValidationSchema(model()),
        record("nullableStatus", null));
    assertTrue(errors.isEmpty(), "更新请求中非必填枚举为 null 时应通过校验，实际校验错误=" + errors);
  }

  @Test
  void fileFieldShouldRejectUnsupportedScalar() {
    assertInvalid(record("requiredeeNo", "R1", "contractFile", 123),
        "文件字段不应接受既非 URL 字符串也非文件对象的取值");
  }

  // ---------- 断言 ----------

  private void assertValid(Map<String, Object> payload, String message) {
    var errors = validate(createSchema(), payload);
    assertTrue(errors.isEmpty(), message + "，实际校验错误=" + errors);
  }

  private void assertInvalid(Map<String, Object> payload, String message) {
    var errors = validate(createSchema(), payload);
    assertFalse(errors.isEmpty(), message);
  }

  private List<String> validate(Map<String, Object> validationSchema, Map<String, Object> payload) {
    try {
      var schemaNode = OBJECT_MAPPER.valueToTree(validationSchema);
      var schema = SchemaRegistry.withDefaultDialect(detectVersion(schemaNode))
          .getSchema(OBJECT_MAPPER.writeValueAsString(schemaNode), InputFormat.JSON);
      var errors = schema.validate(OBJECT_MAPPER.writeValueAsString(payload), InputFormat.JSON);
      return errors == null
          ? Collections.emptyList()
          : errors.stream().map(error -> error.getMessage()).toList();
    } catch (Exception failure) {
      throw new AssertionError("校验请求体失败", failure);
    }
  }

  private static SpecificationVersion detectVersion(JsonNode schemaNode) {
    if (schemaNode == null || !schemaNode.has("$schema")) {
      return SpecificationVersion.DRAFT_7;
    }
    return SpecificationVersion.fromDialectId(schemaNode.path("$schema").asText(null))
        .orElse(SpecificationVersion.DRAFT_7);
  }

  @SuppressWarnings("unchecked")
  private static List<String> requiredOf(Map<String, Object> schema) {
    var required = (List<String>) schema.get("required");
    return required == null ? Collections.emptyList() : required;
  }

  // ---------- 构造 ----------

  private static Map<String, Object> createSchema() {
    return BusinessModelSchemaSupport.createValidationSchema(model());
  }

  private static DefaultBusinessModel model() {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "雪花主键", true, false, null));
    fields.add(field("requiredeeNo", "单行文本", false, true, null));
    fields.add(field("autoFillNo", "单行文本", false, true, "0"));
    fields.add(field("contractFile", "文件", false, false, null));
    fields.add(enumField("nullableStatus", false));

    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName("contract");
    meta.setTitle("contract");
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(new ArrayList<>());
    return new DefaultBusinessModel(meta, new InMemoryBusinessModelProvider());
  }

  private static BusinessModelFieldMeta field(String name, String type, boolean primaryKey,
                                              boolean required, String defaultValue) {
    var field = new BusinessModelFieldMeta();
    field.setName(name);
    field.setTitle(name);
    field.setType(type);
    field.setIsPrimaryKey(primaryKey);
    field.setIsRequired(required);
    field.setDefaultValue(defaultValue);
    field.setExtraProps(new LinkedHashMap<>());
    return field;
  }

  private static BusinessModelFieldMeta enumField(String name, boolean required) {
    var field = field(name, "单选", false, required, null);
    field.setExtraProp("dataType", "String");
    field.setExtraProp("options", List.of(
        stringMap("value", "A", "label", "A"),
        stringMap("value", "B", "label", "B")));
    return field;
  }

  private static Map<String, Object> record(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
      map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return map;
  }

  // ---------- 字段类型目录 ----------

  private static FieldTypeCatalog catalog() {
    var definitions = new LinkedHashMap<String, FieldTypeDefinition>();

    var text = new FieldTypeDefinition();
    text.setName("单行文本");
    text.setSource(FieldTypeDefinitionSource.DEFAULT);
    text.setSourceLocation("test");
    text.setDefaultParams(stringMap("size", 100));
    text.setDataType(stringMap("type", "String", "size", "${size}"));
    definitions.put("单行文本", text);

    var snowflake = new FieldTypeDefinition();
    snowflake.setName("雪花主键");
    snowflake.setSource(FieldTypeDefinitionSource.DEFAULT);
    snowflake.setSourceLocation("test");
    snowflake.setDefaultParams(Collections.emptyMap());
    snowflake.setDataType(stringMap("type", "Snowflake", "size", 19));
    snowflake.setPrimaryKeyGenerator("snowflake");
    definitions.put("雪花主键", snowflake);

    // 与 core/ouroboros-field-type/ouroboros-field-type-default-types 的 单选.field-type.json 保持一致
    var singleChoice = new FieldTypeDefinition();
    singleChoice.setName("单选");
    singleChoice.setSource(FieldTypeDefinitionSource.DEFAULT);
    singleChoice.setSourceLocation("test");
    singleChoice.setDefaultParams(stringMap("dataType", "String", "size", 100));
    singleChoice.setDataType(stringMap("type", "${dataType}", "size", "${size}"));
    definitions.put("单选", singleChoice);

    // 与 ability/ouroboros-file-upload 的 文件.field-type.json 保持一致
    var file = new FieldTypeDefinition();
    file.setName("文件");
    file.setSource(FieldTypeDefinitionSource.DEFAULT);
    file.setSourceLocation("test");
    file.setDefaultParams(Collections.emptyMap());
    file.setDataType(stringMap("type", "UploadFile"));
    definitions.put("文件", file);

    return new InMemoryFieldTypeCatalog(definitions);
  }

  private static Map<String, Object> stringMap(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
      map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return map;
  }

  private static Object readApplicationContext() throws Exception {
    return SpringContextUtil.getApplicationContext();
  }

  private static void writeApplicationContext(Object applicationContext) throws Exception {
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static final class InMemoryFieldTypeCatalog implements FieldTypeCatalog {
    private final Map<String, FieldTypeDefinition> definitions;
    private final DefaultFieldTypeCompiler compiler = new DefaultFieldTypeCompiler();

    private InMemoryFieldTypeCatalog(Map<String, FieldTypeDefinition> definitions) {
      this.definitions = definitions;
    }

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return new ArrayList<FieldTypeDefinition>(definitions.values());
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(
        FieldTypeCatalogContext context,
        String name,
        Map<String, Object> params
    ) {
      var definition = definitions.get(name);
      if (definition == null) {
        return Either.left(new FieldTypeCompileException(
            "未知字段类型 " + name,
            name,
            FieldTypeDefinitionSource.DEFAULT,
            "test",
            "compile"));
      }
      return compiler.compile(definition, params, context);
    }
  }

  private static final class InMemoryBusinessModelProvider implements BusinessModelProvider {
    private final Map<String, BusinessModel> models = new LinkedHashMap<>();

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.unmodifiableMap(models);
    }
  }
}
