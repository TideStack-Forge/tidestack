package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;

/**
 * 统一结果事件工厂：收敛 5 处手写 {@link FlowInstanceEvent} 构建，保证载荷字段一致。
 *
 * <p>businessRecordType/businessRecordId 同时为空时事件不携带 businessContext；
 * starter 与 callbackFlow 可空，工厂按 null 跳过。
 */
public final class ExternalFlowEventFactory {

  private ExternalFlowEventFactory() {
  }

  /** New starts and their recovery share the persisted journal's identity and reply time. */
  public static FlowInstanceEvent started(ExternalStartFact fact) {
    FlowInstanceEvent event = ExternalStartFact.MODE_RESULT_ONLY.equals(fact.getIntegrationMode())
        ? terminalWithTracking(fact.getFlowKey(), fact.getTrackingId(), EventType.STARTED, null, fact.getActorUserId(),
            fact.getBusinessRecordType(), fact.getBusinessRecordId(), fact.getCallbackFlow(), fact.getExternalUrl(),
            fact.getApprovalCaseId(), fact.getSubmissionRound(), fact.getId())
        : started(fact.getFlowKey(), fact.getTrackingId(), fact.getActorUserId(), fact.getBusinessRecordType(),
            fact.getBusinessRecordId(), fact.getCallbackFlow(), fact.getExternalUrl(), fact.getApprovalCaseId(),
            fact.getSubmissionRound(), fact.getId());
    event.setEventTime(fact.getRepliedAt() == null ? fact.getCreatedAt() : fact.getRepliedAt());
    return event;
  }

  /** 启动事件（STARTED）。 */
  public static FlowInstanceEvent started(
      String flowKey,
      String instanceId,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow,
      String externalUrl) {
    FlowInstanceEvent event = base(flowKey, instanceId, EventType.STARTED, null, starter,
        businessRecordType, businessRecordId, callbackFlow);
    event.setExternalUrl(externalUrl);
    return event;
  }

  public static FlowInstanceEvent started(
      String flowKey,
      String instanceId,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow,
      String externalUrl,
      String approvalCaseId,
      Integer submissionRound,
      String eventFactId) {
    FlowInstanceEvent event = started(flowKey, instanceId, starter, businessRecordType,
        businessRecordId, callbackFlow, externalUrl);
    setCaseContext(event, approvalCaseId, submissionRound, eventFactId);
    return event;
  }

  /** 终态事件（无 trackingId/externalUrl 的实例绑定场景）。 */
  public static FlowInstanceEvent terminal(
      String flowKey,
      String instanceId,
      EventType eventType,
      String outcome,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow) {
    return base(flowKey, instanceId, eventType, outcome, starter,
        businessRecordType, businessRecordId, callbackFlow);
  }

  /** 终态事件（结果型绑定场景：携带 trackingId 与外部详情地址）。 */
  public static FlowInstanceEvent terminalWithTracking(
      String flowKey,
      String trackingId,
      EventType eventType,
      String outcome,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow,
      String externalUrl) {
    FlowInstanceEvent event = base(flowKey, trackingId, eventType, outcome, starter,
        businessRecordType, businessRecordId, callbackFlow);
    event.setTrackingId(trackingId);
    event.setExternalUrl(externalUrl);
    return event;
  }

  public static FlowInstanceEvent terminalWithTracking(
      String flowKey,
      String trackingId,
      EventType eventType,
      String outcome,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow,
      String externalUrl,
      String approvalCaseId,
      Integer submissionRound,
      String eventFactId) {
    FlowInstanceEvent event = terminalWithTracking(flowKey, trackingId, eventType, outcome, starter,
        businessRecordType, businessRecordId, callbackFlow, externalUrl);
    setCaseContext(event, approvalCaseId, submissionRound, eventFactId);
    return event;
  }

  public static FlowInstanceEvent terminal(
      String flowKey,
      String instanceId,
      EventType eventType,
      String outcome,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow,
      String approvalCaseId,
      Integer submissionRound,
      String eventFactId) {
    FlowInstanceEvent event = terminal(flowKey, instanceId, eventType, outcome, starter,
        businessRecordType, businessRecordId, callbackFlow);
    setCaseContext(event, approvalCaseId, submissionRound, eventFactId);
    return event;
  }

  private static void setCaseContext(
      FlowInstanceEvent event, String approvalCaseId, Integer submissionRound, String eventFactId) {
    event.setApprovalCaseId(approvalCaseId);
    event.setSubmissionRound(submissionRound);
    event.setEventFactId(eventFactId);
  }

  private static FlowInstanceEvent base(
      String flowKey,
      String instanceId,
      EventType eventType,
      String outcome,
      String starter,
      String businessRecordType,
      String businessRecordId,
      String callbackFlow) {
    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setFlowKey(flowKey);
    event.setInstanceId(instanceId);
    event.setEventType(eventType);
    event.setEventTime(LocalDateTime.now());
    event.setOutcome(outcome);
    event.setStarter(starter);
    if (businessRecordType != null && businessRecordId != null) {
      event.setBusinessContext(new FlowInstanceEvent.BusinessContext(businessRecordType, businessRecordId));
    }
    event.setCallbackFlow(callbackFlow);
    return event;
  }
}
