package com.ouroboros.workflow.approval.external.runtime;

import java.util.Map;
import java.util.Set;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;

/**
 * 已注册适配器视图（只读）：描述 + 派生能力快照 + provider 引用。
 */
public final class RegisteredAdapter {

  private final ApprovalWorkflowEngineAdapterDescriptor descriptor;
  private final Set<Capability> capabilities;
  private final ApprovalWorkflowEngineAdapterProvider provider;

  RegisteredAdapter(
      ApprovalWorkflowEngineAdapterDescriptor descriptor,
      Set<Capability> capabilities,
      ApprovalWorkflowEngineAdapterProvider provider) {
    this.descriptor = descriptor;
    this.capabilities = Set.copyOf(capabilities);
    this.provider = provider;
  }

  public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
    return descriptor;
  }

  public Set<Capability> capabilities() {
    return capabilities;
  }

  public ApprovalWorkflowEngineAdapterProvider provider() {
    return provider;
  }

  /**
   * 目录视图（供配置页面与映射激活校验使用）。
   *
   * <p>接入模式与交互模式由 Registry 派生，不在这里重复输出描述里的声明。
   */
  public Map<String, Object> catalogView() {
    return Map.ofEntries(
        Map.entry("adapterId", descriptor.adapterId()),
        Map.entry("provider", provider.provider()),
        Map.entry("displayName", descriptor.displayName()),
        Map.entry("version", descriptor.version()),
        Map.entry("capabilities", capabilities.stream().map(Enum::name).sorted().toList()),
        Map.entry("canonicalActions", descriptor.canonicalActions()),
        Map.entry("disabledActions", descriptor.disabledActions()));
  }

  public boolean hasCapability(Capability capability) {
    return capabilities.contains(capability);
  }
}
