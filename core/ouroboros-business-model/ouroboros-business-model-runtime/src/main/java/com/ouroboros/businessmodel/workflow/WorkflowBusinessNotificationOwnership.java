package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessEventExecutionMode;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner;
import com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwnership;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** One owner is fixed before source JSON persistence and revalidated before Event/Outbox handoff. */
public final class WorkflowBusinessNotificationOwnership implements FlowBusinessNotificationOwnership {
  private final BusinessModelProvider models;
  private final BusinessModelBehaviorProvider behaviors;
  private final Supplier<BusinessEventSubscriptionRuntime> runtime;

  WorkflowBusinessNotificationOwnership(BusinessModelProvider models, BusinessModelBehaviorProvider behaviors,
      Supplier<BusinessEventSubscriptionRuntime> runtime) {
    this.models = models; this.behaviors = behaviors; this.runtime = runtime;
  }

  @Override
  public Optional<FlowBusinessNotificationOwner> capture(FlowTaskEvent event) {
    if (event.getFactKind() != FlowTaskEvent.FactKind.STATE_CHANGED || event.getNotification() == null
        || event.getFlowNode() != null && !event.getFlowNode().isNotifyEnabled()) { return Optional.empty(); }
    Rule rule = event.getState() == TaskState.CREATED ? Rule.TASK_CREATED
        : event.getState() == TaskState.TIMEOUT_REMIND ? Rule.TASK_TIMEOUT_REMIND : null;
    if (rule == null) { return Optional.empty(); }
    String template = rule == Rule.TASK_CREATED ? event.getNotification().getTaskCreatedTemplate() : event.getNotification().getTaskTimeoutRemindTemplate();
    return capture(event.getProjectionVersion(), event.getBusinessContext(), event.getFlowKey(), rule, template, event.getCandidateUsers());
  }

  @Override
  public Optional<FlowBusinessNotificationOwner> capture(FlowInstanceEvent event) {
    if (event.getNotification() == null) { return Optional.empty(); }
    Rule rule = event.getEventType() == FlowInstanceEvent.EventType.APPROVED ? Rule.INSTANCE_COMPLETED
        : event.getEventType() == FlowInstanceEvent.EventType.REJECTED ? Rule.INSTANCE_REJECTED : null;
    if (rule == null) { return Optional.empty(); }
    String template = rule == Rule.INSTANCE_COMPLETED ? event.getNotification().getInstanceCompletedTemplate() : event.getNotification().getInstanceRejectedTemplate();
    return capture(event.getProjectionVersion(), event.getBusinessContext(), event.getFlowKey(), rule, template,
        event.getStarter() == null ? List.of() : List.of(event.getStarter()));
  }

  private Optional<FlowBusinessNotificationOwner> capture(Integer version, FlowInstanceEvent.BusinessContext context,
      String flowKey, Rule rule, String template, List<String> recipients) {
    if (!Integer.valueOf(1).equals(version) || context == null || template == null || template.isBlank()
        || recipients == null || recipients.isEmpty() || recipients.stream().anyMatch(value -> value == null || value.isBlank())) {
      return Optional.empty();
    }
    return BusinessModelPublication.withPublishedContext(() -> {
      var model = models.getBusinessModel(context.recordType()).orElse(null);
      if (model == null || !Objects.equals(flowKey,
          model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null))) { return Optional.empty(); }
      var behavior = behaviors.getBusinessModelBehavior(model.getFullName()).orElse(null);
      var subscription = behavior == null ? null : behavior.getEventSubscriptions().get(rule.subscriptionName());
      if (!matches(model.getFullName(), rule, subscription)) { return Optional.empty(); }
      var delivery = runtime.get();
      if (delivery == null || delivery.inspectAttempt(model.getFullName(), rule.subscriptionName())
          .filter(attempt -> attempt.executionMode() == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT).isEmpty()) { return Optional.empty(); }
      return Optional.of(new FlowBusinessNotificationOwner(1, model.getFullName(), subscription.getName(),
          delivery.resolveUser(model.getFullName()).orElseThrow(() -> new BusinessException("业务 Event 消费身份未配置")), BusinessBehaviorContracts.subscriptionFingerprint(subscription)));
    });
  }

  @Override
  public void requireHandoff(FlowBusinessNotificationOwner owner) {
    BusinessModelPublication.withPublishedContext(() -> {
      var behavior = behaviors.getBusinessModelBehavior(owner.modelFullName()).orElse(null);
      var subscription = behavior == null ? null : behavior.getEventSubscriptions().get(owner.subscriptionName());
      var rule = subscription == null ? null : Rule.forEvent(subscription.getSourceEvent().getEventName()).orElse(null);
      var delivery = runtime.get();
      if (rule == null || !matches(owner.modelFullName(), rule, subscription)
          || delivery == null || !Objects.equals(owner.user(), delivery.resolveUser(owner.modelFullName()).orElse(null))
          || !Objects.equals(owner.ruleRevision(), BusinessBehaviorContracts.subscriptionFingerprint(subscription))
          || delivery.inspectAttempt(owner.modelFullName(), owner.subscriptionName())
              .filter(attempt -> attempt.executionMode() == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT).isEmpty()) {
        throw new BusinessException("工作流通知持久责任无法交接到原业务订阅，保留审批 Outbox 责任");
      }
      return null;
    });
  }

  private boolean matches(String model, Rule rule, EventSubscriptionDefinition subscription) {
    return subscription != null && !Boolean.FALSE.equals(subscription.getIsEnabled())
        && Objects.equals(rule.subscriptionName(), subscription.getName())
        && Objects.equals(model, subscription.getSourceEvent().getModelFullName()) && Objects.equals(rule.eventName(), subscription.getSourceEvent().getEventName())
        && Objects.equals(BusinessWorkflowNotificationSource.LISTENER_NAME, subscription.getTarget().getListenerName())
        && Objects.equals(rule.listenerConfig(), subscription.getTarget().getConfig()) && (subscription.getTarget().getInputMapping() == null || subscription.getTarget().getInputMapping().isEmpty()) && subscription.getTarget().getActionName() == null && subscription.getTarget().getModelFullName() == null
        && subscription.getFilter() == null;
  }
}
