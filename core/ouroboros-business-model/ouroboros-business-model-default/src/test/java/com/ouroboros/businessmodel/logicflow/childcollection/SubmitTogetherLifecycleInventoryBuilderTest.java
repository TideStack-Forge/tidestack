package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MetadataOnlyDataModel;

class SubmitTogetherLifecycleInventoryBuilderTest {

  @Test
  void shouldRecordSubmitTogetherRelationsWithoutDataModelEmbeddedDiagnostics() {
    DefaultBusinessModelMeta owner = model("crm", "customer");
    owner.setRelations(Collections.singletonList(childRelation("contacts", "crm.contact", "customerId")));
    DataModel childDataModel = new MetadataOnlyDataModel(new DataModelMeta());

    Map<String, Object> inventory = new SubmitTogetherLifecycleInventoryBuilder().build(
        Collections.singletonMap(owner.getFullName(), owner),
        name -> "crm.contact".equals(name) ? Optional.of(childDataModel) : Optional.empty(),
        Collections.singletonList(source("repository", true)));

    assertEquals(Boolean.TRUE, inventory.get("sourceComplete"));
    List<?> entries = (List<?>) inventory.get("entries");
    assertEquals(1, entries.size());
    Map<?, ?> entry = (Map<?, ?>) entries.get(0);
    assertEquals("crm.customer.contacts", entry.get("id"));
    assertEquals("crm.contact", entry.get("childModel"));
    assertFalse(entry.containsKey("effectivePlugins"));
  }

  @Test
  void shouldCreateBlockingIssueWhenChildDataModelIsMissing() {
    DefaultBusinessModelMeta owner = model("crm", "customer");
    owner.setRelations(Collections.singletonList(childRelation("contacts", "crm.contact", "customerId")));

    Map<String, Object> inventory = new SubmitTogetherLifecycleInventoryBuilder().build(
        Collections.singletonMap(owner.getFullName(), owner),
        name -> Optional.empty(),
        Collections.singletonList(source("repository", true)));

    List<?> entries = (List<?>) inventory.get("entries");
    Map<?, ?> entry = (Map<?, ?>) entries.get(0);
    List<?> issues = (List<?>) entry.get("issues");
    assertEquals(1, issues.size());
    assertTrue(String.valueOf(((Map<?, ?>) issues.get(0)).get("id")).contains("missing-data-model"));
  }

  private DefaultBusinessModelMeta model(String namespace, String name) {
    DefaultBusinessModelMeta model = new DefaultBusinessModelMeta();
    model.setNamespace(namespace);
    model.setName(name);
    return model;
  }

  private BusinessModelRelationMeta childRelation(String name, String model, String referenceKey) {
    BusinessModelRelationMeta relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setModel(model);
    relation.setType("child");
    relation.setSubmitTogether(true);
    relation.setReferenceKey(referenceKey);
    return relation;
  }

  private Map<String, Object> source(String type, boolean complete) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("type", type);
    source.put("sourceComplete", complete);
    return source;
  }
}
