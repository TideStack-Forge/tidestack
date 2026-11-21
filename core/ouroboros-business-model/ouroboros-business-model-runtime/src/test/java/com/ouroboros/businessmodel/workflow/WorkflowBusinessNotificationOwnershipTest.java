package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryAttempt;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryMode;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowBusinessNotificationOwnershipTest {
  @Test
  void disablingNeverTransfersNewResponsibilityAndPreservesTheOldOwnerForExactRestoration() {
    var fixture = new Fixture();
    var owner = fixture.ownership.capture(fixture.task()).orElseThrow();
    fixture.subscription.setIsEnabled(false);
    assertTrue(fixture.ownership.capture(fixture.task()).isEmpty());
    assertThrows(BusinessException.class, () -> fixture.ownership.requireHandoff(owner));
    fixture.subscription.setIsEnabled(true);
    fixture.ownership.requireHandoff(owner);
    assertEquals(owner, fixture.ownership.capture(fixture.task()).orElseThrow());
  }

  @Test
  void onlyVersionedApplicableNotificationFactsWithRealRecipientsCanTransferResponsibility() {
    var fixture = new Fixture(); var event = fixture.task();
    assertTrue(fixture.ownership.capture(event).isPresent());
    event.setProjectionVersion(null); assertTrue(fixture.ownership.capture(event).isEmpty());
    event.setProjectionVersion(1); event.setBusinessContext(null); assertTrue(fixture.ownership.capture(event).isEmpty());
    event = fixture.task(); event.setCandidateUsers(List.of()); assertTrue(fixture.ownership.capture(event).isEmpty());
    event.setCandidateUsers(List.of(" ")); assertTrue(fixture.ownership.capture(event).isEmpty());
    event = fixture.task(); event.getNotification().setTaskCreatedTemplate(" "); assertTrue(fixture.ownership.capture(event).isEmpty());
    event = fixture.task(); event.setFactKind(FlowTaskEvent.FactKind.ACTION_COMPLETED); assertTrue(fixture.ownership.capture(event).isEmpty());
    event = fixture.task(); event.setState(TaskState.HANDLED); assertTrue(fixture.ownership.capture(event).isEmpty());
    event = fixture.task(); event.setNotification(null); assertTrue(fixture.ownership.capture(event).isEmpty());
    var instance = new FlowInstanceEvent(); assertTrue(fixture.ownership.capture(instance).isEmpty());
    instance.setNotification(new FlowDefinition.Notification()); instance.setEventType(FlowInstanceEvent.EventType.STARTED);
    assertTrue(fixture.ownership.capture(instance).isEmpty());
    instance.setEventType(FlowInstanceEvent.EventType.APPROVED); instance.getNotification().setInstanceCompletedTemplate("notice");
    instance.setProjectionVersion(1); instance.setBusinessContext(new FlowInstanceEvent.BusinessContext("cases.Document", "42"));
    instance.setFlowKey("approval"); assertTrue(fixture.ownership.capture(instance).isEmpty());
  }

  @Test
  void missingPublishedModelBindingOrIndependentImplementationNeverStealsTheLegacySend() {
    var fixture = new Fixture(); var event = fixture.task();
    when(fixture.models.getBusinessModel(anyString())).thenReturn(Optional.empty()); assertTrue(fixture.ownership.capture(event).isEmpty());
    when(fixture.models.getBusinessModel(anyString())).thenReturn(Optional.of(fixture.model));
    event.setFlowKey("foreign"); assertTrue(fixture.ownership.capture(event).isEmpty()); event.setFlowKey("approval");
    when(fixture.runtime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.empty()); assertTrue(fixture.ownership.capture(event).isEmpty());
    when(fixture.runtime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.of(new BusinessEventDeliveryAttempt("r", null, null, null)));
    assertTrue(fixture.ownership.capture(event).isEmpty());
  }

  @Test
  void persistentOwnerRejectsChangedExecutionIdentityOrRedirectedRulesAndAcceptsTheirExactRestoration() {
    var fixture = new Fixture(); var owner = fixture.ownership.capture(fixture.task()).orElseThrow();
    fixture.ownership.requireHandoff(owner);
    when(fixture.runtime.resolveUser(anyString())).thenReturn(Optional.of("other-user"));
    assertThrows(BusinessException.class, () -> fixture.ownership.requireHandoff(owner));
    assertNotEquals(owner, fixture.ownership.capture(fixture.task()).orElseThrow());
    when(fixture.runtime.resolveUser(anyString())).thenReturn(Optional.of("notification-user"));
    fixture.subscription.setFilter("${ true }");
    assertThrows(BusinessException.class, () -> fixture.ownership.requireHandoff(owner));
    assertTrue(fixture.ownership.capture(fixture.task()).isEmpty());
    fixture.subscription.setFilter(null); fixture.subscription.getTarget().setConfig(Map.of("workflow", true, "templateProperty", "instanceCompletedTemplate"));
    assertTrue(fixture.ownership.capture(fixture.task()).isEmpty());
    fixture.subscription.getTarget().setConfig(Rule.TASK_CREATED.listenerConfig());
    fixture.ownership.requireHandoff(owner);
    when(fixture.runtime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> fixture.ownership.requireHandoff(owner));
  }

  private static class Fixture {
    final BusinessModelProvider models = mock(BusinessModelProvider.class);
    final BusinessModel model = mock(BusinessModel.class);
    final BusinessModelBehaviorProvider behaviors = mock(BusinessModelBehaviorProvider.class);
    final BusinessEventSubscriptionRuntime runtime = mock(BusinessEventSubscriptionRuntime.class);
    final EventSubscriptionDefinition subscription = new EventSubscriptionDefinition();
    final WorkflowBusinessNotificationOwnership ownership = new WorkflowBusinessNotificationOwnership(models, behaviors, () -> runtime);

    Fixture() {
      subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
      when(runtime.resolveUser(anyString())).thenReturn(Optional.of("notification-user"));
      when(model.getFullName()).thenReturn("cases.Document");
      when(model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId")).thenReturn(Optional.of("approval"));
      when(models.getBusinessModel("cases.Document")).thenReturn(Optional.of(model));
      subscription.setName(Rule.TASK_CREATED.subscriptionName()); subscription.getSourceEvent().setModelFullName("cases.Document");
      subscription.getSourceEvent().setEventName(Rule.TASK_CREATED.eventName()); subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(BusinessWorkflowNotificationSource.LISTENER_NAME);
      subscription.getTarget().setConfig(Rule.TASK_CREATED.listenerConfig()); when(behaviors.getBusinessModelBehavior("cases.Document")).thenAnswer(ignored -> Optional.of(
          new BusinessModelBehavior("cases.Document", List.of(), List.of(), List.of(subscription))));
      when(runtime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.of(
          new BusinessEventDeliveryAttempt("revision", null, "listener", "1", com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT)));
    }

    FlowTaskEvent task() {
      var event = new FlowTaskEvent(); event.setProjectionVersion(1); event.setFlowKey("approval"); event.setState(TaskState.CREATED);
      event.setBusinessContext(new FlowInstanceEvent.BusinessContext("cases.Document", "42")); event.setCandidateUsers(List.of("alice"));
      var notification = new FlowDefinition.Notification(); notification.setTaskCreatedTemplate("notice"); event.setNotification(notification);
      return event;
    }
  }
}
