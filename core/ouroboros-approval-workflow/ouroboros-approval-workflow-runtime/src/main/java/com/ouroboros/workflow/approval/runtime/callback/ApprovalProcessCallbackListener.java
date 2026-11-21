package com.ouroboros.workflow.approval.runtime.callback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.context.event.EventListener;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/**
 * 审批流程结果处理监听器（业务回调直连）。
 *
 * <p>路由优先级：
 * <ol>
 *   <li>事件带 {@code callbackFlow}：经 {@link ApprovalProcessCallbackBindingCatalog} 校验后，
 *       直接以统一结果事件载荷执行回调逻辑流（新契约）；</li>
 *   <li>事件无 {@code callbackFlow} 但带 {@code businessContext.recordType}：按约定
 *       {@code recordType + ".processCallback"} 解析回调逻辑流执行；</li>
 * </ol>
 */
public class ApprovalProcessCallbackListener {
  private static final Logger logger = LoggerFactory.getLogger(ApprovalProcessCallbackListener.class);

  private final LogicFlowExecutionFacade executionFacade;
  private final PlatformTransactionManager transactionManager;
  private final ApprovalProcessCallbackBindingCatalog bindingCatalog;
  private final List<ApprovalBusinessFactProjection> projections;

  public ApprovalProcessCallbackListener(
      LogicFlowExecutionFacade executionFacade,
      PlatformTransactionManager transactionManager,
      ApprovalProcessCallbackBindingCatalog bindingCatalog) {
    this(executionFacade, transactionManager, bindingCatalog, List.of());
  }

  public ApprovalProcessCallbackListener(LogicFlowExecutionFacade executionFacade,
      PlatformTransactionManager transactionManager, ApprovalProcessCallbackBindingCatalog bindingCatalog,
      List<ApprovalBusinessFactProjection> projections) {
    this.executionFacade = executionFacade;
    this.transactionManager = transactionManager;
    this.bindingCatalog = bindingCatalog;
    if (projections.size() > 1) { throw new IllegalArgumentException("审批事实只能有一个业务投影责任方"); }
    this.projections = List.copyOf(projections);
  }

  @EventListener
  public void onEvent(FlowInstanceEvent event) {
    if (event == null || event.getEventType() == null) {
      return;
    }
    switch (event.getEventType()) {
      case STARTED -> onStarted(event);
      case APPROVED -> onApproved(event);
      case REJECTED -> onRejected(event);
      case CANCELLED -> onCanceled(event);
      case FAILED -> onFailed(event);
      case SUSPENDED -> onSuspended(event);
      case RESUMED -> onResumed(event);
    }
  }

  public void onStarted(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  public void onApproved(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  public void onRejected(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  public void onCanceled(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  public void onFailed(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  /**
   * 暂停也是业务模型可见的生命周期状态，必须经过同一回调映射更新 statusField。
   */
  public void onSuspended(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  /**
   * 恢复回到审批中，复用统一回调映射写入 processing 状态。
   */
  public void onResumed(FlowInstanceEvent event) {
    dispatchCallback(event);
  }

  private void dispatchCallback(FlowInstanceEvent event) {
    if (event == null) {
      return;
    }

    // 直连路由：事件显式携带回调逻辑流
    if (StringUtils.isNotBlank(event.getCallbackFlow())) {
      dispatchDirect(event, event.getCallbackFlow());
      return;
    }
    // 直连路由：按业务记录类型解析回调绑定（注册表元数据优先，降级约定式）
    if (event.getBusinessContext() != null && StringUtils.isNotBlank(event.getBusinessContext().recordType())) {
      Optional<CallbackBinding> resolved = bindingCatalog.resolveByRecordType(event.getBusinessContext().recordType());
      if (resolved.isPresent()) {
        dispatchDirect(event, resolved.get().callbackFlow(), resolved.get());
        return;
      }
    }
    // callbackUrl 伪 HTTP 路由已从正式契约移除；缺少 callback binding 表示无需回调。
  }

  /**
   * 直连路由：校验回调逻辑流存在后，以统一结果事件载荷执行。
   */
  private void dispatchDirect(FlowInstanceEvent event, String callbackFlow) {
    Optional<CallbackBinding> formal = event.getBusinessContext() == null ? Optional.empty()
        : bindingCatalog.resolveByRecordType(event.getBusinessContext().recordType());
    if (formal.isEmpty()) { formal = bindingCatalog.resolveByCallbackFlow(callbackFlow); }
    if (formal.isPresent() && (!callbackFlow.equals(formal.get().callbackFlow())
        || formal.get().flowKey() != null && !formal.get().flowKey().equals(event.getFlowKey()))) {
      throw new BusinessException("审批事实与正式回调绑定不一致");
    }
    dispatchDirect(event, callbackFlow, formal.orElse(null));
  }

  /**
   * 直连路由（含回调绑定元数据）：statusField/statusMap 注入统一结果事件载荷，
   * 供 processStatusRouter 参数化模板消费（当前模板编译期固化，注入为运行时参数化接入点）。
   */
  private void dispatchDirect(FlowInstanceEvent event, String callbackFlow, CallbackBinding binding) {
    if (!bindingCatalog.hasCallbackFlow(callbackFlow)) {
      throw new BusinessException(
          "审批业务回调逻辑流不存在: flowKey=" + event.getFlowKey()
              + ", instanceId=" + event.getInstanceId()
              + ", callbackFlow=" + callbackFlow);
    }
    Runnable callback = () -> execute(event, List.of(buildPayload(event, binding)), callbackFlow);
    if (!projections.isEmpty() && projections.getFirst().projectInstance(event, binding, callback)) { return; }
    dispatch(event, List.of(buildPayload(event, binding)), callbackFlow);
  }

  @EventListener
  public void onTaskEvent(com.ouroboros.workflow.approval.model.events.FlowTaskEvent event) {
    if (event != null && !projections.isEmpty()) { projections.getFirst().projectTask(event); }
  }

  private void dispatch(FlowInstanceEvent event, List<Object> inputs, String logicFlow) {
    var transactionDefinition = new DefaultTransactionDefinition();
    transactionDefinition.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
    TransactionStatus transactionStatus = null;

    try {
      transactionStatus = transactionManager.getTransaction(transactionDefinition);
      execute(event, inputs, logicFlow);
      transactionManager.commit(transactionStatus);
    } catch (RuntimeException ex) {
      rollback(transactionStatus, ex);
      throw ex;
    }
  }

  private void execute(FlowInstanceEvent event, List<Object> inputs, String logicFlow) {
    Try<Object> result = executionFacade.execute(logicFlow, inputs);
    if (result.isFailure()) {
      throw new IllegalStateException("Approval process callback execution failed: flowKey="
          + event.getFlowKey() + ", instanceId=" + event.getInstanceId()
          + ", callbackFlow=" + logicFlow, result.getCause());
    }
  }

  private void rollback(TransactionStatus transactionStatus, RuntimeException failure) {
    if (transactionStatus == null || transactionStatus.isCompleted()) {
      return;
    }
    try {
      transactionManager.rollback(transactionStatus);
    } catch (RuntimeException rollbackError) {
      failure.addSuppressed(rollbackError);
      logger.error("Approval process callback rollback failed", rollbackError);
    }
  }

  /**
   * 统一结果事件载荷（业务回调直连的规范输入）。
   */
  private Map<String, Object> buildPayload(FlowInstanceEvent event) {
    return buildPayload(event, null);
  }

  private Map<String, Object> buildPayload(FlowInstanceEvent event, CallbackBinding binding) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("flowKey", event.getFlowKey());
    payload.put("eventFactId", event.getEventFactId());
    payload.put("stateSequence", event.getStateSequence());
    payload.put("approvalCaseId", event.getApprovalCaseId());
    payload.put("submissionRound", event.getSubmissionRound());
    payload.put("instanceId", event.getInstanceId());
    payload.put("eventType", Optional.ofNullable(event.getEventType()).map(Enum::name).orElse(null));
    payload.put("eventTime", event.getEventTime());
    payload.put("starter", event.getStarter());
    payload.put("lastTaskId", event.getLastTaskId());
    payload.put("outcome", StringUtils.defaultIfBlank(event.getOutcome(), defaultOutcome(event.getEventType())));
    if (event.getBusinessContext() != null) {
      var businessContext = new LinkedHashMap<String, Object>();
      businessContext.put("recordType", event.getBusinessContext().recordType());
      businessContext.put("recordId", event.getBusinessContext().recordId());
      payload.put("businessContext", businessContext);
    }
    payload.put("trackingId", event.getTrackingId());
    payload.put("externalUrl", event.getExternalUrl());
    if (binding != null) {
      payload.put("statusField", binding.statusField());
      payload.put("statusMap", binding.statusMap());
    }
    return payload;
  }

  private String defaultOutcome(FlowInstanceEvent.EventType eventType) {
    if (eventType == FlowInstanceEvent.EventType.APPROVED) {
      return "approved";
    }
    if (eventType == FlowInstanceEvent.EventType.REJECTED) {
      return "rejected";
    }
    if (eventType == FlowInstanceEvent.EventType.CANCELLED) {
      return "canceled";
    }
    if (eventType == FlowInstanceEvent.EventType.FAILED) {
      return "failed";
    }
    return null;
  }

}
