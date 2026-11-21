package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource;
import com.ouroboros.businessmodel.event.store.BusinessEventOccurrenceModel;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.FlowNotificationPayloads;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Reads only the canonical fact accepted by this Event, after current disclosure and consumer identity checks. */
public final class DefaultBusinessWorkflowNotificationSource implements BusinessWorkflowNotificationSource {
  private final BusinessModelProvider models;
  private final BusinessModelBehaviorProvider behaviors;
  private final BusinessEventDisclosure disclosure;
  private final WorkflowBusinessFactStore receipts;
  private final FlowEventOutboxService source;
  private final FlowTaskService tasks;
  private final FlowDefinitionService definitions;

  DefaultBusinessWorkflowNotificationSource(BusinessModelProvider models, BusinessModelBehaviorProvider behaviors, BusinessEventDisclosure disclosure,
      WorkflowBusinessFactStore receipts, FlowEventOutboxService source, FlowTaskService tasks, FlowDefinitionService definitions) {
    this.models = models; this.behaviors = behaviors; this.disclosure = disclosure; this.receipts = receipts;
    this.source = source; this.tasks = tasks; this.definitions = definitions;
  }

  @Override
  public Optional<Notice> read(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
    return com.ouroboros.businessmodel.publication.BusinessModelPublication.withPublishedContext(() ->
        verified(occurrence, delivery).map(fact -> fact.notice()));
  }

  @Override
  public Eligibility eligibility(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery, String employeeId) {
    return com.ouroboros.businessmodel.publication.BusinessModelPublication.withPublishedContext(() ->
        currentEligibility(occurrence, delivery, employeeId));
  }

  private Eligibility currentEligibility(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery, String employeeId) {
    var verified = verified(occurrence, delivery);
    if (verified.isEmpty()) { return skip("workflow-notification-legacy-owner"); }
    var fact = verified.get();
    if (!fact.notice().employeeIds().contains(employeeId)) { throw new BusinessException("工作流通知收件人不属于源事实"); }
    if (fact.task() == null) { return new Eligibility(true, "workflow-starter-recipient"); }
    var task = fact.task();
    if (task.getFlowNode() != null && !task.getFlowNode().isNotifyEnabled()) { return skip("workflow-node-notify-disabled"); }
    var current = tasks.getTaskDetail(task.getTaskId());
    if (current == null || Boolean.FALSE.equals(current.isActive())) { return skip("workflow-task-no-longer-active"); }
    if (current.isActive() == null || !Objects.equals(task.getTaskId(), current.getId())
        || !Objects.equals(task.getFlowKey(), current.getFlowKey()) || !Objects.equals(task.getInstanceId(), current.getInstanceId())
        || !Objects.equals(task.getNodeId(), current.getTaskDefinitionKey())) {
      throw new BusinessException("工作流通知无法核验当前任务归属或活动状态");
    }
    var definition = definitions.getStructuredFlowDefinition(task.getFlowKey())
        .getOrElseThrow(error -> new BusinessException("读取工作流通知当前节点失败", error));
    var node = definition.getNode(task.getNodeId());
    if (node == null) { throw new BusinessException("工作流通知当前节点不可核验"); }
    if (!node.isNotifyEnabled()) { return skip("workflow-node-notify-disabled"); }
    if ((current.getAssignee() == null || current.getAssignee().isBlank()) && current.getCandidateUsers() == null) {
      throw new BusinessException("工作流通知当前任务收件资格不可核验");
    }
    boolean applicable = current.getAssignee() != null && !current.getAssignee().isBlank()
        ? employeeId.equals(current.getAssignee()) : current.getCandidateUsers() != null && current.getCandidateUsers().contains(employeeId);
    return applicable ? new Eligibility(true, "workflow-current-task-recipient") : skip("workflow-recipient-no-longer-eligible");
  }

  private Optional<Fact> verified(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
    if (!Objects.equals(CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity(), delivery.user())
        || !disclosure.canRead(occurrence)) { throw new BusinessException("工作流通知源事实披露未获授权"); }
    if (!occurrence.subject().isInstance() || !WorkflowBusinessEventProducer.NAME.equals(occurrence.producerRef())
        || !occurrence.identity().modelFullName().equals(occurrence.subject().modelFullName())
        || !occurrence.identity().modelFullName().equals(delivery.consumerModelFullName())
        || occurrence.causationId() == null || !occurrence.causationId().equals(occurrence.payloadFields().get("eventFactId"))) {
      throw new BusinessException("工作流通知不是正式业务投影事实");
    }
    requireCanonicalOccurrence(occurrence);
    String flowKey = (String) occurrence.payloadFields().get("flowKey");
    var receipt = receipts.findReceipt(occurrence.identity().modelFullName(), occurrence.subject().id(), flowKey, occurrence.causationId())
        .orElseThrow(() -> new BusinessException("工作流通知缺少已提交的业务接收凭据"));
    if (!Objects.equals(receipt.getBusinessEventId(), occurrence.eventId())
        || !Objects.equals(receipt.getInstanceId(), occurrence.payloadFields().get("instanceId"))) {
      throw new BusinessException("工作流通知与业务接收凭据不一致");
    }
    var outbox = source.findById(receipt.getSourceFactId()).orElseThrow(() -> new BusinessException("工作流通知源事实不可用"));
    if (FlowEventOutbox.EVENT_KIND_TASK.equals(outbox.getEventKind())) {
      var task = source.fromTaskJson(outbox).orElseThrow(() -> new BusinessException("工作流通知任务事实无法读取"));
      String eventName = task.getFactKind() == FlowTaskEvent.FactKind.STATE_CHANGED && task.getState() == TaskState.CREATED
          ? "workflowTaskCreated" : task.getFactKind() == FlowTaskEvent.FactKind.STATE_CHANGED && task.getState() == TaskState.TIMEOUT_REMIND
              ? "workflowTaskTimeoutReminded" : null;
      requireSource(occurrence, task.getBusinessContext(), task.getFlowKey(), task.getInstanceId(), eventName);
      if (!Objects.equals(task.getTaskId(), occurrence.payloadFields().get("taskId"))) { throw new BusinessException("工作流通知任务身份不一致"); }
      if (!requireOwner(occurrence, delivery, task.getBusinessNotificationOwner())) { return Optional.empty(); }
      var configuration = task.getNotification();
      String template = configuration == null ? null : task.getState() == TaskState.CREATED
          ? configuration.getTaskCreatedTemplate() : configuration.getTaskTimeoutRemindTemplate();
      return Optional.of(new Fact(notice(eventName, template, task.getCandidateUsers(), FlowNotificationPayloads.of(task), delivery), task));
    }
    if (!FlowEventOutbox.EVENT_KIND_INSTANCE.equals(outbox.getEventKind())) { throw new BusinessException("工作流通知源事实种类无效"); }
    var instance = source.fromInstanceJson(outbox).orElseThrow(() -> new BusinessException("工作流通知实例事实无法读取"));
    String eventName = instance.getEventType() == FlowInstanceEvent.EventType.APPROVED ? "workflowApproved"
        : instance.getEventType() == FlowInstanceEvent.EventType.REJECTED ? "workflowRejected" : null;
    requireSource(occurrence, instance.getBusinessContext(), instance.getFlowKey(), instance.getInstanceId(), eventName);
    if (!requireOwner(occurrence, delivery, instance.getBusinessNotificationOwner())) { return Optional.empty(); }
    var configuration = instance.getNotification();
    String template = configuration == null ? null : instance.getEventType() == FlowInstanceEvent.EventType.APPROVED
        ? configuration.getInstanceCompletedTemplate() : configuration.getInstanceRejectedTemplate();
    return Optional.of(new Fact(notice(eventName, template, instance.getStarter() == null ? List.of() : List.of(instance.getStarter()),
        FlowNotificationPayloads.of(instance), delivery), null));
  }

  private void requireCanonicalOccurrence(BusinessEventOccurrence<?> occurrence) {
    var model = TypedDataModelCenter.getDataModel(String.class, BusinessEventOccurrenceModel.class)
        .orElseThrow(() -> new BusinessException("业务 Event 事实模型不可用"));
    var stored = model.get(occurrence.eventId()).getOrElseThrow(error -> new BusinessException("读取业务 Event 通知凭据失败", error));
    if (stored == null || !same(Json.toMap(stored.getOccurrenceJson()), occurrence.toMap())
        || !same(Json.toMap(stored.getSchemaJson()), occurrence.payloadSchema())) {
      throw new BusinessException("工作流通知载荷与已提交业务 Event 不一致");
    }
  }

  private void requireSource(BusinessEventOccurrence<?> occurrence, FlowInstanceEvent.BusinessContext context,
      String flowKey, String instanceId, String eventName) {
    var model = models.getBusinessModel(occurrence.identity().modelFullName())
        .orElseThrow(() -> new BusinessException("工作流通知所属模型未发布"));
    if (!Objects.equals(eventName, occurrence.identity().eventName()) || context == null
        || !Objects.equals(context.recordType(), model.getFullName()) || !Objects.equals(context.recordId(), occurrence.subject().id().toString())
        || !Objects.equals(flowKey, occurrence.payloadFields().get("flowKey")) || !Objects.equals(instanceId, occurrence.payloadFields().get("instanceId"))
        || !Objects.equals(flowKey, model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null))) {
      throw new BusinessException("工作流通知源事实与当前业务绑定不一致");
    }
  }

  private boolean requireOwner(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery, FlowBusinessNotificationOwner owner) {
    if (owner == null) { return false; }
    var model = behaviors.getBusinessModelBehavior(occurrence.identity().modelFullName()).orElseThrow();
    var subscription = model.getEventSubscriptions().get(delivery.subscriptionName());
    if (subscription == null || Boolean.FALSE.equals(subscription.getIsEnabled())
        || !Objects.equals(owner.modelFullName(), delivery.consumerModelFullName())
        || !Objects.equals(owner.subscriptionName(), delivery.subscriptionName())
        || !Objects.equals(subscription.getTarget().getConfig(), delivery.listenerConfig())
        || !Objects.equals(owner.user(), delivery.user())
        || !Objects.equals(owner.ruleRevision(), BusinessBehaviorContracts.subscriptionFingerprint(subscription))) {
      throw new BusinessException("工作流通知持久责任与当前订阅不一致");
    }
    return true;
  }

  private Notice notice(String eventName, String template, List<String> recipients, Map<String, Object> payload, BusinessEventDeliveryContext delivery) {
    var rule = Rule.forEvent(eventName).orElseThrow(() -> new BusinessException("工作流通知事实没有对应内置规则"));
    if (template == null || template.isBlank() || !Objects.equals(rule.listenerConfig(), delivery.listenerConfig())
        || recipients == null || recipients.isEmpty() || recipients.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new BusinessException("工作流通知源规则或收件人无效");
    }
    return new Notice(template, recipients.stream().distinct().sorted().toList(), payload);
  }

  private boolean same(Map<String, Object> left, Map<String, Object> right) {
    return BusinessBehaviorContracts.schemaFingerprint(left).equals(BusinessBehaviorContracts.schemaFingerprint(right));
  }

  private Eligibility skip(String diagnostic) { return new Eligibility(false, diagnostic); }
  private record Fact(Notice notice, FlowTaskEvent task) { }
}
