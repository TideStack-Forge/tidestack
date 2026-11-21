package com.ouroboros.notification.behavior;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.NotificationDeliveryPolicy;
import com.ouroboros.notification.NotificationDispatchRequest;
import com.ouroboros.notification.NotificationDispatchResult;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class BusinessEventNotificationListenerTest {
  @Test
  void stableRuleIdentityAndSchemaValidatedOptionsReachTheExistingDispatcher() {
    var fixture = new Fixture(); var request = new AtomicReference<NotificationDispatchRequest>();
    when(fixture.dispatcher.sendWithResult(any(), any())).thenAnswer(call -> {
      request.set(call.getArgument(0)); return complete();
    });
    fixture.subscription.getTarget().setConfig(Map.of("template", "notice", "options", Map.of("channels", List.of("email"), "sendByEmployee", "alice")));
    fixture.send(); var first = request.get();
    assertEquals("fact", first.sourceFactId()); assertEquals("business-event:Order#notify", first.ruleIdentity());
    assertEquals("notice", first.template()); assertEquals(List.of("alice"), first.employeeIds());
    assertEquals(List.of("email"), first.options().getChannels()); assertEquals("alice", first.options().getSendByEmployee());
    fixture.send(); assertEquals(first.ruleRevision(), request.get().ruleRevision());
    fixture.subscription.getTarget().setInputMapping(Map.of("payload", "${ event.payload }")); fixture.send();
    assertNotEquals(first.ruleRevision(), request.get().ruleRevision());
  }

  @Test
  void incompleteOrInvalidDeliveryNeverReportsReliableSuccess() {
    var fixture = new Fixture();
    for (var status : List.of(NotificationDispatchResult.Status.BLOCKED, NotificationDispatchResult.Status.PENDING, NotificationDispatchResult.Status.UNKNOWN)) {
      when(fixture.dispatcher.sendWithResult(any(), any())).thenReturn(new NotificationDispatchResult("n", status, "unresolved", List.of()));
      var failure = assertThrows(BusinessException.class, fixture::send);
      assertTrue(failure.getMessage().contains(status.name()));
    }
    reset(fixture.dispatcher); fixture.subscription.getTarget().setConfig(Map.of("template", "notice", "unexpected", true));
    assertThrows(BusinessException.class, fixture::send); verifyNoInteractions(fixture.dispatcher);
    fixture.subscription.getTarget().setConfig(Map.of("template", "notice")); fixture.subscription.getTarget().setKind("listener"); fixture.subscription.getTarget().setListenerName("other");
    assertThrows(BusinessException.class, fixture::send); verifyNoInteractions(fixture.dispatcher);
  }

  @Test
  void recipientPolicyRequiresCurrentEnabledUserDisclosureAndTheExactFrozenAddressSet() {
    var fixture = new Fixture(); var policy = new AtomicReference<NotificationDeliveryPolicy>();
    when(fixture.dispatcher.sendWithResult(any(), any())).thenAnswer(call -> { policy.set(call.getArgument(1)); return complete(); });
    fixture.send();
    assertEquals("notification-recipient-identity-unavailable", policy.get().evaluate("alice", "email", List.of("a@test")).diagnostic());
    var employee = mock(Employee.class); when(employee.getUser()).thenReturn("user"); when(fixture.employees.getById("alice")).thenReturn(Optional.of(employee));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("user"); when(user.isEnabled()).thenReturn(false);
    when(fixture.users.getUser("user")).thenReturn(Optional.of(user));
    assertEquals("notification-recipient-identity-unavailable", policy.get().evaluate("alice", "email", List.of("a@test")).diagnostic());
    when(user.isEnabled()).thenReturn(true); when(fixture.disclosure.canRead(any())).thenAnswer(call -> {
      assertEquals("user", CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity()); return false;
    });
    assertEquals("notification-recipient-disclosure-denied", policy.get().evaluate("alice", "email", List.of("a@test")).diagnostic());
    assertTrue(CurrentSecurityContextHolder.findIdentity().isEmpty());
    doReturn(true).when(fixture.disclosure).canRead(any());
    when(fixture.addresses.parse(anyCollection(), anyCollection())).thenReturn(Map.of("email", List.of("b@test")));
    assertEquals("notification-recipient-address-changed", policy.get().evaluate("alice", "email", List.of("a@test")).diagnostic());
    assertEquals(NotificationDeliveryPolicy.Disposition.ALLOW, policy.get().evaluate("alice", "email", List.of("b@test")).disposition());
  }

  @Test
  void workflowUsesAuthorizedSourceAndRecordsInapplicableRecipientsAsSkippedBeforeRecipientDisclosure() {
    var fixture = new Fixture(); var policy = new AtomicReference<NotificationDeliveryPolicy>();
    var request = new AtomicReference<NotificationDispatchRequest>();
    fixture.subscription.getTarget().setConfig(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule.TASK_CREATED.listenerConfig());
    when(fixture.workflow.read(any(), any())).thenReturn(Optional.of(new com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Notice(
        "task-template", List.of("alice"), Map.of("taskId", "task", "eventTime", java.time.LocalDateTime.of(2026, 10, 30, 12, 0)))));
    when(fixture.dispatcher.sendWithResult(any(), any())).thenAnswer(call -> { request.set(call.getArgument(0)); policy.set(call.getArgument(1)); return complete(); });
    fixture.send(Map.of());
    assertEquals("task-template", request.get().template());
    assertEquals("task", request.get().payload().get("taskId"));
    when(fixture.workflow.eligibility(any(), any(), eq("alice"))).thenReturn(
        new com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Eligibility(false, "workflow-task-no-longer-active"));
    assertEquals(NotificationDeliveryPolicy.Disposition.SKIP, policy.get().evaluate("alice", "email", List.of("a@test")).disposition());
    verifyNoInteractions(fixture.employees, fixture.users, fixture.disclosure, fixture.addresses);
    when(fixture.workflow.eligibility(any(), any(), eq("alice"))).thenReturn(
        new com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Eligibility(true, "current"));
    assertEquals("notification-recipient-identity-unavailable", policy.get().evaluate("alice", "email", List.of("a@test")).diagnostic());
  }

  @Test
  void workflowRetainsOldResponsibilityAndNeverConfirmsIncompleteSendsOrAcceptsDynamicSourceReplacement() {
    var fixture = new Fixture(); fixture.subscription.getTarget().setConfig(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule.TASK_CREATED.listenerConfig());
    when(fixture.workflow.read(any(), any())).thenReturn(Optional.empty());
    fixture.send(Map.of()); verifyNoInteractions(fixture.dispatcher);
    when(fixture.workflow.read(any(), any())).thenReturn(Optional.of(new com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Notice(
        "task-template", List.of("alice"), Map.of())));
    when(fixture.dispatcher.sendWithResult(any(), any())).thenReturn(new NotificationDispatchResult("n", NotificationDispatchResult.Status.UNKNOWN, "unknown", List.of()));
    assertThrows(BusinessException.class, () -> fixture.send(Map.of()));
    reset(fixture.dispatcher); fixture.subscription.getTarget().setInputMapping(Map.of());
    assertThrows(BusinessException.class, () -> fixture.send(Map.of())); verifyNoInteractions(fixture.dispatcher);
    fixture.subscription.getTarget().setInputMapping(null);
    assertThrows(BusinessException.class, fixture::send);
  }

  private static NotificationDispatchResult complete() { return new NotificationDispatchResult("n", NotificationDispatchResult.Status.COMPLETE, "done", List.of()); }

  private static class Fixture {
    final EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    final BusinessEventDisclosure disclosure = mock(BusinessEventDisclosure.class);
    final EmployeeAggregator employees = mock(EmployeeAggregator.class);
    final UserCenter users = mock(UserCenter.class);
    final NotificationEmployeeAddressParser addresses = mock(NotificationEmployeeAddressParser.class);
    final EventSubscriptionDefinition subscription = new EventSubscriptionDefinition();
    final BusinessModelBehaviorProvider provider = mock(BusinessModelBehaviorProvider.class);
    final com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource workflow = mock(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class);
    final BusinessEventNotificationListener listener = new BusinessEventNotificationListener(() -> provider, dispatcher, disclosure, employees, users, addresses, () -> workflow);

    Fixture() {
      subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
      subscription.setName("notify"); subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(BusinessEventNotificationListener.NAME);
      subscription.getTarget().setConfig(Map.of("template", "notice"));
      when(provider.getBusinessModelBehavior("Order")).thenAnswer(call -> Optional.of(new BusinessModelBehavior("Order", List.of(), List.of(), List.of(subscription))));
    }

    void send() { send(Map.of("employeeIds", List.of("alice"), "payload", Map.of("message", "value"))); }

    void send(Map<String, Object> input) {
      var fact = BusinessEventOccurrence.create("fact", new BusinessEventIdentity("Order", "created"), "revision", BusinessEventSubject.model("Order"),
          Map.of("type", "object"), Map.of(), Instant.now(), "producer", "actor", null, null, null);
      listener.onEvent(fact, new BusinessEventDeliveryContext("d", "Order", "notify", "consumer", 1, null, null,
          subscription.getTarget().getConfig(), input));
    }
  }
}
