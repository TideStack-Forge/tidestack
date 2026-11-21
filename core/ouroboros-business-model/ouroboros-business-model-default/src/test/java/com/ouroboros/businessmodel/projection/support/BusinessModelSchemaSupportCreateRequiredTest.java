package com.ouroboros.businessmodel.projection.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.JsonSchemas;

/**
 * 覆盖新增接口请求 schema 的必填判定。
 * <p>新增接口的入参校验节点排在流程的前置脚本之前，如果 {@code required} 直接取模型 {@code isRequired}，
 * 就会把「服务端自己会填」的字段也算成客户端必填，导致平台自己生成的新增表单提交必然失败。因此：
 * <ul>
 *   <li>新增视图（{@code views.add.fields}）未提交的字段不入 {@code required}——典型场景是
 *       {@code apis.add.beforeScript} 在服务端补齐；</li>
 *   <li>配置了非空默认值的字段不入 {@code required}——数据层插入时用
 *       {@code AbstractDataModel#buildInsertRecord} 补齐。</li>
 * </ul>
 */
class BusinessModelSchemaSupportCreateRequiredTest {

  /** 新增视图提交 customerNo/company/mark；custType/allowedOwe 由前置脚本补齐，isAvailable 无有效默认值。 */
  private static final List<String> CREATE_VIEW_FIELDS = List.of("customerNo", "company", "mark");

  @Test
  void requiredShouldOnlyCoverFieldsDeclaredInCreateViewAndWithoutDefaultValue() {
    var schema = BusinessModelSchemaSupport.createValidationSchema(
        customerModel(new InMemoryBusinessModelProvider(), addView(CREATE_VIEW_FIELDS)));

    var required = requiredOf(schema);
    assertTrue(required.contains("customerNo"),
        "新增视图内的必填字段仍应校验，实际 required=" + required);
    assertFalse(required.contains("custType"),
        "新增视图未提交的字段由前置脚本补齐，不应要求客户端必填，实际 required=" + required);
    assertFalse(required.contains("allowedOwe"),
        "新增视图未提交的字段由前置脚本补齐，不应要求客户端必填，实际 required=" + required);
    assertFalse(required.contains("isAvailable"),
        "新增视图未提交的字段不应要求客户端必填，实际 required=" + required);
    assertFalse(required.contains("company"),
        "带默认值的字段由数据层补齐，不应要求客户端必填，实际 required=" + required);
    assertFalse(required.contains("mark"),
        "非必填字段不应进入 required，实际 required=" + required);
  }

  @Test
  void requiredRelationShouldFollowCreateViewDeclaration() {
    var provider = new InMemoryBusinessModelProvider();
    provider.add(referenceModel(provider, "org"));
    var model = customerModel(provider, addView(List.of("customerNo", "agent")));
    model.getMeta().setRelations(new ArrayList<>(List.of(
        referenceRelation("agent", "demo.org"),
        referenceRelation("owner", "demo.org"))));

    var required = requiredOf(BusinessModelSchemaSupport.createValidationSchema(model));
    assertTrue(required.contains("agent"),
        "新增视图内的必填关系仍应校验，实际 required=" + required);
    assertFalse(required.contains("owner"),
        "新增视图未提交的必填关系不应要求客户端提供，实际 required=" + required);
  }

  @Test
  void requiredShouldStayAsDeclaredWhenCreateViewFieldsAreNotConfigured() {
    var schema = BusinessModelSchemaSupport.createValidationSchema(
        customerModel(new InMemoryBusinessModelProvider(), null));

    var required = requiredOf(schema);
    assertTrue(required.contains("custType"),
        "模型未声明新增视图字段清单时应保持原有的必填声明，实际 required=" + required);
    assertTrue(required.contains("allowedOwe"),
        "模型未声明新增视图字段清单时应保持原有的必填声明，实际 required=" + required);
    assertTrue(required.contains("isAvailable"),
        "空白字符串默认值等于没有默认值，仍应为必填，实际 required=" + required);
    assertFalse(required.contains("company"),
        "默认值规则与新增视图配置无关，实际 required=" + required);
  }

  @Test
  void createRequestBodyWithOnlyCreateViewFieldsShouldPassValidation() {
    var schema = BusinessModelSchemaSupport.createValidationSchema(
        customerModel(new InMemoryBusinessModelProvider(), addView(CREATE_VIEW_FIELDS)));

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("customerNo", "1");
    requestBody.put("mark", "");
    assertTrue(JsonSchemas.validate(schema, requestBody),
        "平台自己生成的新增表单提交的字段集应通过校验: " + requestBody);

    var missingRequired = new LinkedHashMap<>(requestBody);
    missingRequired.remove("customerNo");
    assertFalse(JsonSchemas.validate(schema, missingRequired),
        "新增视图内的必填字段缺失仍应被拒绝: " + missingRequired);
  }

  // ---------- 构造 ----------

  /** 含主键、视图内必填字段、视图外必填字段与带默认值必填字段的模型。 */
  private static DefaultBusinessModel customerModel(InMemoryBusinessModelProvider provider, Map<String, Object> views) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", true, null, Map.of("autoIncrement", true)));
    fields.add(field("customerNo", true, null, Collections.emptyMap()));
    fields.add(field("custType", true, null, Collections.emptyMap()));
    fields.add(field("isAvailable", true, "", Collections.emptyMap()));
    fields.add(field("allowedOwe", true, null, Collections.emptyMap()));
    fields.add(field("company", true, "'1'", Collections.emptyMap()));
    fields.add(field("mark", false, null, Collections.emptyMap()));

    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName("customer");
    meta.setTitle("客商");
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(new ArrayList<>());
    if (views != null) {
      meta.setExtraProp("views", views);
    }
    return new DefaultBusinessModel(meta, provider);
  }

  private static DefaultBusinessModel referenceModel(InMemoryBusinessModelProvider provider, String name) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", true, null, Map.of("autoIncrement", true)));

    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(new ArrayList<>());
    return new DefaultBusinessModel(meta, provider);
  }

  private static Map<String, Object> addView(List<String> fieldNames) {
    var viewFields = new ArrayList<Map<String, Object>>();
    for (String name : fieldNames) {
      viewFields.add(new LinkedHashMap<>(Map.of("type", "field", "name", name)));
    }
    var addView = new LinkedHashMap<String, Object>();
    addView.put("isEnabled", true);
    addView.put("fields", viewFields);
    var views = new LinkedHashMap<String, Object>();
    views.put("add", addView);
    return views;
  }

  private static BusinessModelFieldMeta field(String name, boolean required, String defaultValue,
                                             Map<String, Object> extraProps) {
    var field = new BusinessModelFieldMeta();
    field.setName(name);
    field.setTitle(name);
    field.setType("单行文本");
    field.setIsRequired(required);
    field.setIsPrimaryKey(false);
    field.setDefaultValue(defaultValue);
    field.setExtraProps(new LinkedHashMap<>(extraProps));
    return field;
  }

  private static BusinessModelRelationMeta referenceRelation(String name, String modelName) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setType("reference");
    relation.setModel(modelName);
    relation.setReferenceKey("id");
    relation.setIsRequired(true);
    relation.setExtraProps(new LinkedHashMap<>());
    return relation;
  }

  private static List<String> requiredOf(Map<String, Object> schema) {
    var required = schema.get("required");
    if (!(required instanceof List<?> requiredNames)) {
      return Collections.emptyList();
    }
    var names = new ArrayList<String>();
    for (Object name : requiredNames) {
      names.add(String.valueOf(name));
    }
    return names;
  }

  private static final class InMemoryBusinessModelProvider implements BusinessModelProvider {
    private final Map<String, BusinessModel> models = new LinkedHashMap<>();

    private void add(DefaultBusinessModel model) {
      models.put(model.getFullName(), model);
    }

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
