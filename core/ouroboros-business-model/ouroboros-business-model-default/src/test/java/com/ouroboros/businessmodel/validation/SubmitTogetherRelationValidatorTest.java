package com.ouroboros.businessmodel.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;

class SubmitTogetherRelationValidatorTest {

  @Test
  void shouldRejectInvalidSubmitTogetherRelationsBeforeProjection() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = model(provider, "demo", "order", field("id", "单行文本", true), field("code", "单行文本", false));
    owner.getMeta().setRelations(List.of(
        childRelation("lines", "demo.orderLine", "orderId", "id"),
        childRelation("missingReference", "demo.orderLineMissingRef", "orderId", "id"),
        childRelation("typeConflict", "demo.orderLineTypeConflict", "orderId", "id"),
        childRelation("compositeChild", "demo.orderLineCompositePk", "orderId", "id")
    ));

    provider.add(model(provider, "demo", "orderLine", field("lineId", "单行文本", true), field("orderId", "单行文本", false)));
    provider.add(model(provider, "demo", "orderLineMissingRef", field("lineId", "单行文本", true)));
    provider.add(model(provider, "demo", "orderLineTypeConflict", field("lineId", "单行文本", true), field("orderId", "整数数字", false)));
    provider.add(model(provider,
        "demo",
        "orderLineCompositePk",
        field("lineId", "单行文本", true),
        field("lineNo", "整数数字", true),
        field("orderId", "单行文本", false)
    ));

    var errors = validateSubmitTogetherRelations(owner);

    assertFalse(errors.isEmpty());
    var messages = errors.stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
    assertTrue(messages.contains("missingReference"));
    assertTrue(messages.contains("orderId"));
    assertTrue(messages.contains("typeConflict"));
    assertTrue(messages.contains("类型"));
    assertTrue(messages.contains("compositeChild"));
    assertTrue(messages.contains("单列主键"));
  }

  @Test
  void shouldRejectSubmitTogetherRelationCycles() {
    var provider = new InMemoryBusinessModelProvider();
    var order = model(provider, "demo", "order", field("id", "单行文本", true));
    order.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderId", "id")));
    var line = model(provider, "demo", "orderLine", field("lineId", "单行文本", true), field("orderId", "单行文本", false));
    line.getMeta().setRelations(List.of(childRelation("orders", "demo.order", "lineId", "lineId")));

    provider.add(order);
    provider.add(line);

    var errors = validateSubmitTogetherRelations(order);

    assertFalse(errors.isEmpty());
    var messages = errors.stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
    assertTrue(messages.contains("关系环"));
    assertTrue(messages.contains("lines"));
    assertTrue(messages.contains("orders"));
  }

  @Test
  void shouldReportSubmitTogetherErrorsThroughDefaultValidationChain() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = model(provider, "demo", "order", field("id", "单行文本", true));
    owner.getMeta().setRelations(List.of(childRelation("typeConflict", "demo.orderLine", "orderId", "id")));
    provider.add(owner);
    provider.add(model(provider, "demo", "orderLine", field("lineId", "单行文本", true), field("orderId", "整数数字", false)));

    var result = assertDoesNotThrow(owner::getValidationResult);

    assertFalse(result.isValid());
    var messages = result.getErrors().stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
    assertTrue(messages.contains("typeConflict"));
    assertTrue(messages.contains("类型"));
  }

  @Test
  void shouldAcceptValidNestedSubmitTogetherRelations() {
    var provider = new InMemoryBusinessModelProvider();
    var order = model(provider, "demo", "order", field("orderCode", "单行文本", true));
    order.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderCode", "orderCode")));
    var line = model(provider, "demo", "orderLine", field("lineCode", "单行文本", true), field("orderCode", "单行文本", false));
    line.getMeta().setRelations(List.of(childRelation("attachments", "demo.orderLineAttachment", "lineCode", "lineCode")));
    var attachment = model(provider, "demo", "orderLineAttachment", field("attachmentCode", "单行文本", true), field("lineCode", "单行文本", false));

    provider.add(order);
    provider.add(line);
    provider.add(attachment);

    var errors = validateSubmitTogetherRelations(order);

    assertTrue(errors.isEmpty(), errors::toString);
  }

  @Test
  void shouldAcceptStringChildReferenceForAnyOwnerKeyType() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = model(provider, "demo", "order", field("id", "自增主键", true));
    owner.getMeta().setRelations(List.of(childRelation("lines", "demo.orderLine", "orderCode", "id")));
    var child = model(provider, "demo", "orderLine", field("lineId", "自增主键", true), field("orderCode", "单行文本", false));
    provider.add(owner);
    provider.add(child);

    var errors = validateSubmitTogetherRelations(owner);

    assertTrue(errors.isEmpty(), errors::toString);
  }

  @Test
  void shouldAcceptBusinessCodeOwnerKeyViaChildMasterRelation() {
    var provider = new InMemoryBusinessModelProvider();
    var owner = model(provider, "demo", "productType", field("id", "自增主键", true), field("productTypeNo", "单行文本", false));
    owner.getMeta().setRelations(List.of(childRelationWithoutKey("tagTemplate", "demo.productTypeTag", "typeNo")));
    var child = model(provider, "demo", "productTypeTag", field("id", "自增主键", true));
    child.getMeta().setRelations(List.of(masterRelation("typeNo", "demo.productType", "productTypeNo")));
    provider.add(owner);
    provider.add(child);

    var errors = validateSubmitTogetherRelations(owner);

    assertTrue(errors.isEmpty(), errors::toString);
  }

  private static List<Throwable> validateSubmitTogetherRelations(DefaultBusinessModel model) {
    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SubmitTogetherRelationValidator().doValidate(context);
    return context.getErrors();
  }

  private static DefaultBusinessModel model(InMemoryBusinessModelProvider provider, String namespace, String name, BusinessModelFieldMeta... fields) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace(namespace);
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    meta.setFields(new ArrayList<>(List.of(fields)));
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
    relation.setModel(childModelName);
    relation.setType("child");
    relation.setSubmitTogether(true);
    relation.setReferenceKey(referenceKey);
    relation.setExtraProp("key", ownerKey);
    return relation;
  }

  private static BusinessModelRelationMeta childRelationWithoutKey(String name, String childModelName, String referenceKey) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setModel(childModelName);
    relation.setType("child");
    relation.setSubmitTogether(true);
    relation.setReferenceKey(referenceKey);
    return relation;
  }

  private static BusinessModelRelationMeta masterRelation(String name, String ownerModelName, String referenceKey) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setTitle(name);
    relation.setModel(ownerModelName);
    relation.setType("master");
    relation.setSubmitTogether(false);
    relation.setReferenceKey(referenceKey);
    return relation;
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
