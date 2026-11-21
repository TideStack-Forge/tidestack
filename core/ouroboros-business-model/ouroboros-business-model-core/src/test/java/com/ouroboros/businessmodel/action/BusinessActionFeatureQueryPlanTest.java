package com.ouroboros.businessmodel.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;

import static org.junit.jupiter.api.Assertions.*;

class BusinessActionFeatureQueryPlanTest {
  @Test
  void resolvedIdentitiesAreImmutableAndChangeThePublishedRevisionDeterministically() {
    var mappings = new LinkedHashMap<String, String>(); mappings.put("orderId", "businessKey");
    var conditions = new LinkedHashMap<String, Object>(); conditions.put("sourceModel", "sales.Order");
    var first = new BusinessActionFeatureQueryPlan(BusinessActionFeatureQueryPlan.Kind.HISTORY,
        "sales.Order", "sales.OrderHistory", "orderId", "businessKey", mappings, conditions);
    mappings.put("orderId", "changed"); conditions.put("sourceModel", "other");
    assertEquals("businessKey", first.sourceFieldMappings().get("orderId"));
    assertEquals("sales.Order", first.fixedConditions().get("sourceModel"));
    assertThrows(UnsupportedOperationException.class, () -> first.sourceFieldMappings().clear());
    assertThrows(UnsupportedOperationException.class, () -> first.fixedConditions().clear());
    var original = behavior(); var published = original.withFeatureQueryPlans(Map.of("searchHistory", first));
    assertNotEquals(original.getModelRevision(), published.getModelRevision());
    assertSame(published, published.withFeatureQueryPlans(Map.of("searchHistory", first)));
    var second = plan("sales.OrderHistory1", "sales.Order");
    assertEquals(original.withFeatureQueryPlans(Map.of("searchHistory", second)).getModelRevision(),
        published.withFeatureQueryPlans(Map.of("searchHistory", second)).getModelRevision());
    assertEquals(original.getModelRevision(), published.withFeatureQueryPlans(Map.of()).getModelRevision());
    assertThrows(UnsupportedOperationException.class, () -> published.getFeatureQueryPlans().clear());
    assertTrue(original.getFeatureQueryPlans().isEmpty());
  }

  @Test
  void inconsistentPhysicalIdentityOrActionBindingCannotBePublished() {
    assertThrows(IllegalArgumentException.class, () -> new BusinessActionFeatureQueryPlan(BusinessActionFeatureQueryPlan.Kind.HISTORY,
        "sales.Order", "sales.OrderHistory", "id", "businessKey", Map.of("id", "other"), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> plan(" ", "sales.Order"));
    assertThrows(IllegalArgumentException.class, () -> behavior().withFeatureQueryPlans(Map.of("searchHistory", plan("sales.OtherHistory", "sales.Other"))));
    assertThrows(IllegalArgumentException.class, () -> behavior().withFeatureQueryPlans(Map.of("wrong", plan("sales.OrderHistory", "sales.Order"))));
    var action = action(); action.setReadOnly(false);
    assertThrows(IllegalArgumentException.class, () -> new BusinessModelBehavior("sales.Order", List.of(action), List.of(), List.of())
        .withFeatureQueryPlans(Map.of("searchHistory", plan("sales.OrderHistory", "sales.Order"))));
    action.setReadOnly(true); action.getBinding().setKind("logicFlow");
    assertThrows(IllegalArgumentException.class, () -> new BusinessModelBehavior("sales.Order", List.of(action), List.of(), List.of())
        .withFeatureQueryPlans(Map.of("searchHistory", plan("sales.OrderHistory", "sales.Order"))));
  }

  private static BusinessActionFeatureQueryPlan plan(String target, String source) {
    return new BusinessActionFeatureQueryPlan(BusinessActionFeatureQueryPlan.Kind.HISTORY,
        source, target, "orderId", "businessKey", Map.of("orderId", "businessKey"), Map.of());
  }

  private static BusinessModelBehavior behavior() {
    return new BusinessModelBehavior("sales.Order", List.of(action()), List.of(), List.of(), List.of(), "declaration-v1");
  }

  private static ActionDefinition action() {
    var action = new ActionDefinition(); action.setName("searchHistory"); action.setReadOnly(true);
    var binding = new ActionBinding(); binding.setKind("handler"); binding.setHandlerName(BusinessActionFeatureQueryPlan.HANDLER_NAME);
    action.setBinding(binding); return action;
  }
}
