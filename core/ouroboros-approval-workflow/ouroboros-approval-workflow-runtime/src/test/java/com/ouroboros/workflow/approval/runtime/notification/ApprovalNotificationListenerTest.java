package com.ouroboros.workflow.approval.runtime.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class ApprovalNotificationListenerTest {
  @Test
  void onCreatedSendsTaskCreatedTemplateToCandidateUsers() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowTaskEvent event = taskEvent();
    listener.onCreated(event);

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(dispatcher).send(
        eq(Arrays.asList("emp-1", "emp-2")),
        eq("tpl-task-created"),
        payloadCaptor.capture());
    assertEquals("flow-a", payloadCaptor.getValue().get("flowKey"));
    assertEquals("instance-1", payloadCaptor.getValue().get("instanceId"));
    assertEquals("task-1", payloadCaptor.getValue().get("taskId"));
    assertEquals("handle-a", payloadCaptor.getValue().get("nodeId"));
    assertEquals("CREATED", payloadCaptor.getValue().get("state"));
  }

  @Test
  void onCreatedSkipsWhenNodeNotifyIsFalse() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowTaskEvent event = taskEvent();
    event.getFlowNode().setNotify(false);
    listener.onCreated(event);

    verify(dispatcher, never()).send(any(Collection.class), any(String.class), any(Map.class));
  }

  @Test
  void onTimeoutRemindUsesTaskTimeoutRemindTemplate() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowTaskEvent event = taskEvent();
    event.setState(TaskState.TIMEOUT_REMIND);
    listener.onTimeoutRemind(event);

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(dispatcher).send(
        eq(Arrays.asList("emp-1", "emp-2")),
        eq("tpl-timeout-remind"),
        payloadCaptor.capture());
    assertEquals("TIMEOUT_REMIND", payloadCaptor.getValue().get("state"));
  }

  @Test
  void onInstanceApprovedAndRejectedSendConfiguredTemplatesToStarter() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowInstanceEvent approved = instanceEvent(FlowInstanceEvent.EventType.APPROVED);
    FlowInstanceEvent rejected = instanceEvent(FlowInstanceEvent.EventType.REJECTED);
    listener.onApproved(approved);
    listener.onRejected(rejected);

    verify(dispatcher).send(eq(Arrays.asList("starter-1")), eq("tpl-instance-completed"), any(Map.class));
    verify(dispatcher).send(eq(Arrays.asList("starter-1")), eq("tpl-instance-rejected"), any(Map.class));
  }

  @Test
  void onInstanceEventIncludesOutcomeInPayload() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowInstanceEvent event = instanceEvent(FlowInstanceEvent.EventType.APPROVED);
    event.setOutcome("finance-approved");
    listener.onApproved(event);

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(dispatcher).send(eq(Arrays.asList("starter-1")), eq("tpl-instance-completed"), payloadCaptor.capture());
    assertEquals("finance-approved", payloadCaptor.getValue().get("outcome"));
  }

  @Test
  void missingTemplateOrNotificationSkipsDispatch() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowTaskEvent taskEvent = taskEvent();
    taskEvent.setNotification(null);
    FlowInstanceEvent instanceEvent = instanceEvent(FlowInstanceEvent.EventType.APPROVED);
    instanceEvent.setNotification(new Notification());
    listener.onCreated(taskEvent);
    listener.onApproved(instanceEvent);

    verify(dispatcher, never()).send(any(Collection.class), any(String.class), any(Map.class));
  }

  @Test
  void missingTemplateProviderEntrySkipsDispatch() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);

    FlowTaskEvent event = taskEvent();
    event.getNotification().setTaskCreatedTemplate("missing-template");
    listener.onCreated(event);

    verify(dispatcher, never()).send(any(Collection.class), any(String.class), any(Map.class));
  }

  @Test
  void dispatchFailurePropagatesToEventDispatcher() {
    EmployeeNotificationDispatcher dispatcher = mock(EmployeeNotificationDispatcher.class);
    ApprovalNotificationListener listener = new ApprovalNotificationListener(dispatcher);
    doThrow(new IllegalStateException("send failed"))
        .when(dispatcher)
        .send(any(Collection.class), eq("tpl-task-created"), any(Map.class));

    assertThrows(IllegalStateException.class, () -> listener.onCreated(taskEvent()));
  }

  @Test
  void durableBusinessOwnerDisablesAllLegacyNotificationHandlersButProjectionVersionAloneDoesNot() {
    var dispatcher = mock(EmployeeNotificationDispatcher.class);
    var listener = new ApprovalNotificationListener(dispatcher);
    var owner = new com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner(1, "cases.Document", "notice", "service", "revision");
    var task = taskEvent(); task.setProjectionVersion(1); task.setBusinessNotificationOwner(owner);
    listener.onCreated(task); listener.onTimeoutRemind(task); listener.onTaskEvent(task);
    var instance = instanceEvent(FlowInstanceEvent.EventType.APPROVED); instance.setProjectionVersion(1); instance.setBusinessNotificationOwner(owner);
    listener.onApproved(instance); listener.onRejected(instance); listener.onInstanceEvent(instance);
    org.mockito.Mockito.verifyNoInteractions(dispatcher);
    task.setBusinessNotificationOwner(null); listener.onCreated(task);
    verify(dispatcher).send(eq(Arrays.asList("emp-1", "emp-2")), eq("tpl-task-created"), any(Map.class));
    instance.setBusinessNotificationOwner(null); listener.onApproved(instance);
    verify(dispatcher).send(eq(Arrays.asList("starter-1")), eq("tpl-instance-completed"), any(Map.class));
  }

  private FlowTaskEvent taskEvent() {
    Notification notification = new Notification();
    notification.setTaskCreatedTemplate("tpl-task-created");
    notification.setTaskTimeoutRemindTemplate("tpl-timeout-remind");

    HandleNode node = new HandleNode();
    node.setId("handle-a");
    node.setName("办理");

    FlowTaskEvent event = new FlowTaskEvent();
    event.setFlowKey("flow-a");
    event.setInstanceId("instance-1");
    event.setTaskId("task-1");
    event.setCandidateUsers(Arrays.asList("emp-1", "emp-2"));
    event.setState(TaskState.CREATED);
    event.setFlowNode(node);
    event.setNotification(notification);
    return event;
  }

  private FlowInstanceEvent instanceEvent(FlowInstanceEvent.EventType eventType) {
    Notification notification = new Notification();
    notification.setInstanceCompletedTemplate("tpl-instance-completed");
    notification.setInstanceRejectedTemplate("tpl-instance-rejected");

    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setFlowKey("flow-a");
    event.setInstanceId("instance-1");
    event.setStarter("starter-1");
    event.setEventType(eventType);
    event.setNotification(notification);
    return event;
  }
}
