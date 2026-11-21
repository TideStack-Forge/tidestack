package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;

class ChildCollectionSyncPlanCompilerTest {

  @Test
  void shouldCompileWhenStringChildReferenceUsesDifferentOwnerDataType() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = businessModel(provider, "order", "id", "自增主键");
    owner.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderCode", "id")));
    var child = businessModel(provider, "orderLine", "lineId", "自增主键", field("orderCode", "单行文本", false));
    provider.add(owner);
    provider.add(child);

    var bundle = new ModelBundle();
    bundle.putModel(DataModelMeta.class, "demo.order", dataModel("demo", "order", "id", fieldMeta("id", "Long", true)));
    bundle.putModel(DataModelMeta.class, "demo.orderLine", dataModel(
        "demo", "orderLine", "lineId", fieldMeta("lineId", "Long", true), fieldMeta("orderCode", "String", false)));

    assertDoesNotThrow(() -> new ChildCollectionSyncPlanCompiler().compile(owner, bundle));
  }

  @Test
  void shouldRejectDifferentNonStringReferenceDataType() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = businessModel(provider, "order", "id", "自增主键");
    owner.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderId", "id")));
    var child = businessModel(provider, "orderLine", "lineId", "自增主键", field("orderId", "整数数字", false));
    provider.add(owner);
    provider.add(child);

    var bundle = new ModelBundle();
    bundle.putModel(DataModelMeta.class, "demo.order", dataModel("demo", "order", "id", fieldMeta("id", "Long", true)));
    bundle.putModel(DataModelMeta.class, "demo.orderLine", dataModel(
        "demo", "orderLine", "lineId", fieldMeta("lineId", "Long", true), fieldMeta("orderId", "Integer", false)));

    assertThrows(IllegalArgumentException.class, () -> new ChildCollectionSyncPlanCompiler().compile(owner, bundle));
  }

  @Test
  void shouldCompileNestedSubmitTogetherPlansWithNonIdPrimaryKeysAndStableFingerprints() {
    var provider = new InMemoryBusinessModelProvider();
    var order = businessModel(provider, "order", "orderCode");
    order.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderCode", "orderCode")));
    var line = businessModel(provider, "orderLine", "lineCode", field("orderCode", "String", false));
    line.getMeta().setRelations(List.of(childRelation("attachments", "demo.orderLineAttachment", "lineCode", "lineCode")));
    var attachment = businessModel(provider, "orderLineAttachment", "attachmentCode", field("lineCode", "String", false));
    provider.add(order);
    provider.add(line);
    provider.add(attachment);

    var bundle = new ModelBundle();
    bundle.putModel(DataModelMeta.class, "demo.order", dataModel("demo", "order", "orderCode", fieldMeta("orderCode", "String", true)));
    bundle.putModel(DataModelMeta.class, "demo.orderLine", dataModel(
        "demo",
        "orderLine",
        "lineCode",
        fieldMeta("lineCode", "String", true),
        fieldMeta("orderCode", "String", false)
    ));
    bundle.putModel(DataModelMeta.class, "demo.orderLineAttachment", dataModel(
        "demo",
        "orderLineAttachment",
        "attachmentCode",
        fieldMeta("attachmentCode", "String", true),
        fieldMeta("lineCode", "String", false)
    ));

    var plans = new ChildCollectionSyncPlanCompiler().compile(order, bundle);

    assertEquals(1, plans.size());
    var linePlan = plans.get(0);
    assertEquals("lines", linePlan.relationName());
    assertEquals("demo.order", linePlan.ownerDataModelName());
    assertEquals("demo.orderLine", linePlan.childDataModelName());
    assertEquals("orderCode", linePlan.ownerKeyField());
    assertEquals("orderCode", linePlan.childReferenceField());
    assertEquals("lineCode", linePlan.childPrimaryKeyField());
    assertFalse(linePlan.ownerDataModelFingerprint().isBlank());
    assertFalse(linePlan.childDataModelFingerprint().isBlank());
    assertEquals(1, linePlan.children().size());
    assertEquals("attachments", linePlan.children().get(0).relationName());
    assertEquals("attachmentCode", linePlan.children().get(0).childPrimaryKeyField());

    var changedChildMeta = dataModel(
        "demo",
        "orderLine",
        "lineCode",
        fieldMeta("lineCode", "String", true),
        fieldMeta("orderCode", "Long", false)
    );
    assertNotEquals(
        linePlan.childDataModelFingerprint(),
        ChildCollectionSyncPlanCompiler.fingerprint(changedChildMeta, "lineCode", "orderCode")
    );
  }

  @Test
  void shouldCompileBusinessCodeOwnerKeyViaChildMasterRelation() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = businessModel(provider, "order", "id", "自增主键", field("productTypeNo", "单行文本", false));
    owner.getMeta().setRelations(List.of(childRelationWithoutKey("tagTemplate", "demo.orderLine", "typeNo")));
    var child = businessModel(provider, "orderLine", "lineId", "自增主键");
    child.getMeta().setRelations(List.of(masterRelation("typeNo", "demo.order", "productTypeNo")));
    provider.add(owner);
    provider.add(child);

    var bundle = new ModelBundle();
    bundle.putModel(DataModelMeta.class, "demo.order", dataModel(
        "demo", "order", "id", fieldMeta("id", "Long", true), fieldMeta("productTypeNo", "String", false)));
    var typeNoField = fieldMeta("typeNo", "Model", false);
    typeNoField.setExtraProp("model", "demo.order");
    typeNoField.setExtraProp("referenceKey", "productTypeNo");
    bundle.putModel(DataModelMeta.class, "demo.orderLine", dataModel(
        "demo", "orderLine", "lineId", fieldMeta("lineId", "Long", true), typeNoField));

    var plans = assertDoesNotThrow(() -> new ChildCollectionSyncPlanCompiler().compile(owner, bundle));

    assertEquals(1, plans.size());
    assertEquals("productTypeNo", plans.get(0).ownerKeyField());
    assertEquals("typeNo", plans.get(0).childReferenceField());
  }

  private static DefaultBusinessModel businessModel(
      InMemoryBusinessModelProvider provider,
      String name,
      String primaryKey,
      BusinessModelFieldMeta... additionalFields
  ) {
    return businessModel(provider, name, primaryKey, "单行文本", additionalFields);
  }

  private static DefaultBusinessModel businessModel(
      InMemoryBusinessModelProvider provider,
      String name,
      String primaryKey,
      String primaryKeyType,
      BusinessModelFieldMeta... additionalFields
  ) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field(primaryKey, primaryKeyType, true));
    fields.addAll(List.of(additionalFields));

    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(new ArrayList<>());
    return new DefaultBusinessModel(meta, provider);
  }

  private static BusinessModelFieldMeta field(String name, String type, boolean primaryKey) {
    var field = new BusinessModelFieldMeta();
    field.setName(name);
    field.setTitle(name);
    field.setType(type);
    field.setIsRequired(true);
    field.setIsPrimaryKey(primaryKey);
    field.setExtraProps(new LinkedHashMap<>());
    return field;
  }

  private static BusinessModelRelationMeta childRelation(String name, String childModelName, String referenceKey, String ownerKey) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setType("child");
    relation.setSubmitTogether(true);
    relation.setModel(childModelName);
    relation.setReferenceKey(referenceKey);
    relation.setExtraProp("key", ownerKey);
    return relation;
  }

  private static BusinessModelRelationMeta childRelationWithoutKey(String name, String childModelName, String referenceKey) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setType("child");
    relation.setSubmitTogether(true);
    relation.setModel(childModelName);
    relation.setReferenceKey(referenceKey);
    return relation;
  }

  private static BusinessModelRelationMeta masterRelation(String name, String ownerModelName, String referenceKey) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setType("master");
    relation.setSubmitTogether(false);
    relation.setModel(ownerModelName);
    relation.setReferenceKey(referenceKey);
    return relation;
  }

  private static DataModelMeta dataModel(String namespace, String name, String primaryKey, DataModelFieldMeta... fields) {
    var meta = new DataModelMeta();
    meta.setNamespace(namespace);
    meta.setName(name);
    meta.setFields(new ArrayList<>(List.of(fields)));
    meta.setPrimaryKeys(Collections.singletonList(primaryKey));
    return meta;
  }

  private static DataModelFieldMeta fieldMeta(String name, String type, boolean primaryKey) {
    var field = new DataModelFieldMeta();
    field.setName(name);
    field.setType(type);
    field.setExtraProp("isPrimaryKey", primaryKey);
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
