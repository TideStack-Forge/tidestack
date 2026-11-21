package com.ouroboros.workflow.approval.runtime.callback;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;

import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.callback.CallbackBindingProvider;

/**
 * 审批流程回调绑定目录（Phase 4 M23，D-01 落地）。
 *
 * <p>两级解析：① 注册表命中（投影元数据 {@link CallbackBinding}，随元数据刷新事件重建，
 * 机制同现有 metadata provider）；② 降级约定式（{@code recordType + ".processCallback"}，
 * statusField/statusMap=null，保持存量兼容）。直连路由前仍校验回调逻辑流存在性。
 */
public class ApprovalProcessCallbackBindingCatalog {

  /** 回调逻辑流命名后缀（与业务模型投影的 {@code ProcessCallbackLogic} 一致）。 */
  public static final String CALLBACK_FLOW_SUFFIX = ".processCallback";

  private final FlowModelProvider flowModelProvider;
  private final ObjectProvider<CallbackBindingProvider> callbackBindingProviders;
  private final ConcurrentHashMap<String, CallbackBinding> bindingsByRecordType =
      new ConcurrentHashMap<>();

  public ApprovalProcessCallbackBindingCatalog(
      FlowModelProvider flowModelProvider,
      ObjectProvider<CallbackBindingProvider> callbackBindingProviders) {
    this.flowModelProvider = flowModelProvider;
    this.callbackBindingProviders = callbackBindingProviders;
  }

  /**
   * 注册回调绑定元数据（按 recordType 覆盖）。
   */
  public void register(CallbackBinding binding) {
    if (binding != null && binding.recordType() != null) {
      bindingsByRecordType.put(binding.recordType(), binding);
    }
  }

  /**
   * 批量注册（元数据刷新重建用，覆盖旧值）。
   */
  public void registerAll(Collection<CallbackBinding> bindings) {
    bindingsByRecordType.clear();
    if (bindings != null) {
      bindings.forEach(this::register);
    }
  }

  /**
   * 元数据刷新事件：从全部 {@link CallbackBindingProvider} 聚合重建注册表。
   */
  @EventListener
  public void onOuroborosMetadataRefreshed(OuroborosMetadataRefreshedEvent event) {
    List<CallbackBinding> bindings = callbackBindingProviders.stream()
        .flatMap(provider -> provider.getCallbackBindings().stream())
        .toList();
    registerAll(bindings);
  }

  /**
   * 回调逻辑流是否存在（直连路由前校验，防止执行未知流程）。
   */
  public boolean hasCallbackFlow(String callbackFlow) {
    return callbackFlow != null && flowModelProvider.getFlowModel(callbackFlow).isPresent();
  }

  /** Explicit callback routes retain their formal owner; arbitrary flow names confer no model authority. */
  public Optional<CallbackBinding> resolveByCallbackFlow(String callbackFlow) {
    var matches = bindingsByRecordType.values().stream()
        .filter(binding -> java.util.Objects.equals(callbackFlow, binding.callbackFlow())).toList();
    if (matches.size() > 1) {
      throw new com.ouroboros.exception.BusinessException("审批回调逻辑流存在多个业务绑定: " + callbackFlow);
    }
    return matches.stream().findFirst();
  }

  /**
   * 按业务记录类型解析回调绑定：注册表命中优先（含 statusField/statusMap 元数据）；
   * 未命中降级约定式（{@code recordType + ".processCallback"}，元数据为 null）。
   */
  public Optional<CallbackBinding> resolveByRecordType(String recordType) {
    if (recordType == null || recordType.isBlank()) {
      return Optional.empty();
    }
    CallbackBinding registered = bindingsByRecordType.get(recordType);
    if (registered != null && hasCallbackFlow(registered.callbackFlow())) {
      return Optional.of(registered);
    }
    String flowName = recordType + CALLBACK_FLOW_SUFFIX;
    if (hasCallbackFlow(flowName)) {
      return Optional.of(new CallbackBinding(null, flowName, recordType, null, null));
    }
    return Optional.empty();
  }
}
