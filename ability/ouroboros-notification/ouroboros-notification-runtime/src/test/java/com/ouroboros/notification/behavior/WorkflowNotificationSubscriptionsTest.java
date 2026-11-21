package com.ouroboros.notification.behavior;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WorkflowNotificationSubscriptionsTest {
  @Test
  void officialContributorNormalizesFourConditionalRulesWithoutRewritingLegacyMetadata() {
    try (var context = context(true)) {
      var meta = meta(true); var original = Json.toJsonString(meta);
      var model = new DefaultBusinessModel(meta, mock(BusinessModelProvider.class));
      assertEquals(original, Json.toJsonString(meta));
      assertEquals(4, model.getBehavior().getEventSubscriptions().size());
      for (var rule : Rule.values()) {
        var subscription = model.getBehavior().getEventSubscriptions().get(rule.subscriptionName());
        assertEquals(rule.eventName(), subscription.getSourceEvent().getEventName());
        assertEquals("cases.Document", subscription.getSourceEvent().getModelFullName());
        assertEquals(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT, subscription.getExecutionMode());
        assertEquals(rule.listenerConfig(), subscription.getTarget().getConfig());
        assertNull(subscription.getTarget().getInputMapping());
        var source = model.getBehavior().getSubscriptionSources().get(rule.subscriptionName());
        assertEquals("workflow:approval.notification", source.resource());
        assertEquals("2.0", source.formatVersion());
        var origins = model.getBehavior().getMemberSources().stream().filter(origin ->
            "subscription".equals(origin.memberType()) && rule.subscriptionName().equals(origin.memberName())).toList();
        assertEquals(2, origins.size());
        assertEquals("generated", origins.getFirst().kind());
        assertEquals("workflow:approval.notification", origins.getFirst().source().resource());
        var configuration = origins.getLast();
        assertEquals("configuration", configuration.role());
        assertEquals("approval-workflow-definition:approval", configuration.source().resource());
        assertEquals("notification." + rule.templateProperty(), configuration.source().fieldPath());
        assertTrue(configuration.description().contains("模板"));
        assertTrue(configuration.description().contains("资格"));
      }
      assertTrue(SPI.getSortedServiceStream(BusinessModelBehaviorContributor.class)
          .anyMatch(contributor -> contributor instanceof WorkflowNotificationBehaviorContributor));
      assertTrue(new WorkflowNotificationBehaviorContributor().getContributions().isEmpty());
    }
  }

  @Test
  void disabledWorkflowRetainsInFlightRulesButUnboundModelsHaveNoGeneratedSubscriptions() {
    try (var context = context(true)) {
      var model = new DefaultBusinessModel(meta(false), mock(BusinessModelProvider.class));
      assertEquals(4, model.getBehavior().getEventSubscriptions().size());
      assertFalse(model.getBehavior().getActions().containsKey("startProcess"));
      var unbound = meta(false); unbound.setExtraProp("workflow", Map.of("isEnabled", false));
      assertTrue(new WorkflowNotificationSubscriptions().contribute(unbound).isEmpty());
    }
  }

  @Test
  void missingOptionalIntegrationPreservesOriginalRulesAndDuplicateUserDefinitionsRemainConflicts() {
    assertTrue(new WorkflowNotificationBehaviorContributor().getContributions(meta(true)).isEmpty());
    try (var context = context(false)) {
      assertTrue(new DefaultBusinessModel(meta(true), mock(BusinessModelProvider.class)).getBehavior().getEventSubscriptions().isEmpty());
    }
    try (var context = context(true)) {
      var meta = meta(true); var duplicate = new EventSubscriptionDefinition(); duplicate.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
      duplicate.setName(Rule.TASK_CREATED.subscriptionName()); duplicate.getTarget().setListenerName("other");
      meta.setEventSubscriptions(List.of(duplicate));
      var model = new DefaultBusinessModel(meta, mock(BusinessModelProvider.class));
      assertFalse(model.getBehavior().getDefinitionConflicts().isEmpty());
    }
    assertTrue(new WorkflowNotificationBehaviorContributor().getContributions(meta(true)).isEmpty());
    assertDoesNotThrow(WorkflowNotificationSubscriptions::new);
    assertEquals(4, Arrays.stream(Rule.values()).map(Rule::eventName).distinct().count());
  }

  private GenericApplicationContext context(boolean enabled) {
    var context = new GenericApplicationContext();
    context.registerBean(SpringContextUtil.class);
    if (enabled) { context.registerBean(WorkflowNotificationSubscriptions.class, () -> new WorkflowNotificationSubscriptions()); }
    context.refresh();
    return context;
  }

  private DefaultBusinessModelMeta meta(boolean enabled) {
    var meta = new DefaultBusinessModelMeta(); meta.setNamespace("cases"); meta.setName("Document"); meta.setIsEnabled(true);
    var id = new BusinessModelFieldMeta(); id.setName("id"); id.setType("整数数字"); id.setIsPrimaryKey(true); meta.setFields(List.of(id));
    meta.setExtraProp("workflow", Map.of("isEnabled", enabled, "bindings", List.of(Map.of("processId", "approval"))));
    return meta;
  }
}
