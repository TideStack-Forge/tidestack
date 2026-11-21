package com.ouroboros.workflow.approval.runtime.notification;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.template.NotificationTemplateProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.definition.FlowDefinition.Notification;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.event.FlowNotificationPayloads;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component
@ConditionalOnBean(EmployeeNotificationDispatcher.class)
public class ApprovalNotificationListener {
  private static final Logger logger = LoggerFactory.getLogger(ApprovalNotificationListener.class);

  private final EmployeeNotificationDispatcher dispatcher;

  public ApprovalNotificationListener(EmployeeNotificationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @EventListener
  public void onTaskEvent(FlowTaskEvent event) {
    if (event == null || event.getState() == null) {
      return;
    }
    switch (event.getState()) {
      case CREATED -> onCreated(event);
      case TIMEOUT_REMIND -> onTimeoutRemind(event);
      case UNCREATED, APPROVED, REJECTED, HANDLED, PROCESSING, CANCELLED, SUSPENDED, RESUMED -> {
        // No notification template is configured for this task lifecycle event.
      }
    }
  }

  public void onCreated(FlowTaskEvent event) {
    if (event.getBusinessNotificationOwner() != null) { return; }
    FlowNode node = event.getFlowNode();
    if (node != null && !node.isNotifyEnabled()) {
      return;
    }

    Notification notification = event.getNotification();
    String template = notification == null ? null : notification.getTaskCreatedTemplate();
    send(ObjectUtils.defaultIfNull(event.getCandidateUsers(), Collections.<String>emptyList()), template, FlowNotificationPayloads.of(event));
  }

  public void onTimeoutRemind(FlowTaskEvent event) {
    if (event.getBusinessNotificationOwner() != null) { return; }
    FlowNode node = event.getFlowNode();
    if (node != null && !node.isNotifyEnabled()) {
      return;
    }

    Notification notification = event.getNotification();
    String template = notification == null ? null : notification.getTaskTimeoutRemindTemplate();
    send(ObjectUtils.defaultIfNull(event.getCandidateUsers(), Collections.<String>emptyList()), template, FlowNotificationPayloads.of(event));
  }

  @EventListener
  public void onInstanceEvent(FlowInstanceEvent event) {
    if (event == null || event.getEventType() == null) {
      return;
    }
    switch (event.getEventType()) {
      case APPROVED -> onApproved(event);
      case REJECTED -> onRejected(event);
      case STARTED, SUSPENDED, RESUMED, CANCELLED, FAILED -> {
        // No notification template is configured for this instance lifecycle event.
      }
    }
  }

  public void onApproved(FlowInstanceEvent event) {
    if (event.getBusinessNotificationOwner() != null) { return; }
    Notification notification = event.getNotification();
    String template = notification == null ? null : notification.getInstanceCompletedTemplate();
    send(starter(event), template, FlowNotificationPayloads.of(event));
  }

  public void onRejected(FlowInstanceEvent event) {
    if (event.getBusinessNotificationOwner() != null) { return; }
    Notification notification = event.getNotification();
    String template = notification == null ? null : notification.getInstanceRejectedTemplate();
    send(starter(event), template, FlowNotificationPayloads.of(event));
  }

  private Collection<String> starter(FlowInstanceEvent event) {
    if (StringUtils.isBlank(event.getStarter())) {
      return Collections.emptyList();
    }
    return Collections.singletonList(event.getStarter());
  }

  private void send(Collection<String> employeeIds, String template, Map<String, Object> payload) {
    if (StringUtils.isBlank(template) || ObjectUtils.isEmpty(employeeIds)) {
      return;
    }

    try {
      if (!templateExists(template)) {
        return;
      }
      dispatcher.send(employeeIds, template, payload);
    } catch (RuntimeException ex) {
      logger.warn(
          "Approval notification dispatch failed: flowKey={}, instanceId={}, taskId={}, templateKey={}",
          payload.get("flowKey"),
          payload.get("instanceId"),
          payload.get("taskId"),
          template,
          ex);
      throw ex;
    }
  }

  private boolean templateExists(String template) {
    return SPI.getCachedSortedServiceStream(NotificationTemplateProvider.class)
        .map(provider -> provider.getTemplate(template))
        .map(optional -> ObjectUtils.defaultIfNull(optional, Optional.empty()))
        .anyMatch(Optional::isPresent);
  }
}
