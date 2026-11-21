package com.ouroboros.businessmodel.projection.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/**
 * 覆盖子集合项中 master 关系（子表指回父表）的必填判定。
 * <p>该字段由 {@code ChildCollectionSynchronizer} 在保存时依据父子关系自动回填，
 * 客户端既不应该也无法提供（父记录主键可能尚未生成），因此作为父表子集合项构建
 * 请求 schema 时不应进入 required；而子表通过自身 API 独立创建时仍按声明校验。
 */
class BusinessModelSchemaSupportChildMasterRelationTest {

  private static final String PARENT = "demo.order";
  private static final String CHILD = "demo.orderLine";

  @Test
  void childCollectionItemShouldNotRequireMasterRelation() {
    var schema = BusinessModelSchemaSupport.createValidationSchema(parentWithChild());

    var required = childItemRequired(schema);
    assertFalse(required.contains("orderCode"),
        "子集合项不应要求 master 关系，实际 required=" + required);
  }

  @Test
  void childCollectionItemShouldStillRequireItsOwnMandatoryFields() {
    var schema = BusinessModelSchemaSupport.createValidationSchema(parentWithChild());

    var required = childItemRequired(schema);
    assertTrue(required.contains("lineCode"),
        "子集合项自身的必填字段仍应校验，实际 required=" + required);
  }

  @Test
  void childCreatedThroughItsOwnApiShouldStillRequireMasterRelation() {
    var provider = new InMemoryBusinessModelProvider();
    var parent = businessModel(provider, "order", "id");
    parent.getMeta().setRelations(new ArrayList<>());
    var child = childModel(provider);
    provider.add(parent);
    provider.add(child);

    var schema = BusinessModelSchemaSupport.createValidationSchema(child);

    @SuppressWarnings("unchecked")
    var required = (List<String>) schema.get("required");
    assertNotNull(required, "子表独立创建时应有 required 声明");
    assertTrue(required.contains("orderCode"),
        "子表独立创建时 master 关系仍应为必填，实际 required=" + required);
  }

  // ---------- 构造 ----------

  private DefaultBusinessModel parentWithChild() {
    var provider = new InMemoryBusinessModelProvider();
    var parent = businessModel(provider, "order", "id");
    var childRelation = new BusinessModelRelationMeta();
    childRelation.setName("lines");
    childRelation.setTitle("lines");
    childRelation.setType("child");
    childRelation.setSubmitTogether(true);
    childRelation.setModel(CHILD);
    childRelation.setReferenceKey("orderCode");
    parent.getMeta().setRelations(new ArrayList<>(List.of(childRelation)));

    var child = childModel(provider);
    provider.add(parent);
    provider.add(child);
    return parent;
  }

  /** 子表：含主键、自身必填字段 lineCode，以及标记为必填的 master 关系 orderCode。 */
  private DefaultBusinessModel childModel(InMemoryBusinessModelProvider provider) {
    var child = businessModel(provider, "orderLine", "lineId");
    child.getMeta().getFields().add(field("lineCode", false));

    var masterRelation = new BusinessModelRelationMeta();
    masterRelation.setName("orderCode");
    masterRelation.setTitle("orderCode");
    masterRelation.setType("master");
    masterRelation.setSubmitTogether(false);
    masterRelation.setModel(PARENT);
    masterRelation.setReferenceKey("id");
    masterRelation.setIsRequired(true);
    child.getMeta().setRelations(new ArrayList<>(List.of(masterRelation)));
    return child;
  }

  @SuppressWarnings("unchecked")
  private static List<String> childItemRequired(Map<String, Object> schema) {
    var properties = (Map<String, Object>) schema.get("properties");
    assertNotNull(properties, "schema 应有 properties");
    var lines = (Map<String, Object>) properties.get("lines");
    assertNotNull(lines, "schema 应包含子集合属性 lines");
    var items = (Map<String, Object>) lines.get("items");
    assertNotNull(items, "lines 应为数组并带 items");
    var required = (List<String>) items.get("required");
    return required == null ? Collections.emptyList() : required;
  }

  private static DefaultBusinessModel businessModel(InMemoryBusinessModelProvider provider, String name, String primaryKey) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field(primaryKey, true));

    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(new ArrayList<>());
    return new DefaultBusinessModel(meta, provider);
  }

  private static BusinessModelFieldMeta field(String name, boolean primaryKey) {
    var field = new BusinessModelFieldMeta();
    field.setName(name);
    field.setTitle(name);
    field.setType("单行文本");
    field.setIsRequired(true);
    field.setIsPrimaryKey(primaryKey);
    field.setExtraProps(new LinkedHashMap<>());
    return field;
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
