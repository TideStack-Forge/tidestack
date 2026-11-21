package com.ouroboros.workflow.approval.external.runtime;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionDiscovery;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalRecordReader;
import com.ouroboros.workflow.approval.external.ExternalTaskReader;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStartRecovery;

/**
 * 能力派生：按适配器实例实现的端口（{@code instanceof}）生成端口级能力集，
 * 并合并 descriptor 声明的细粒度能力。端口与能力声明是单一事实源，不允许各说各话。
 */
final class EngineAdapterCapabilities {

  private EngineAdapterCapabilities() {
  }

  /**
   * 细粒度能力 → 必须实现的端口。声明了这些能力但未实现对应端口 → 启动期校验失败
   * （设计"不允许 descriptor 与端口实现各说各话"）。映射见下方 {@code PORT_BY_CAPABILITY}，
   * 其中 EXTERNAL_INSTANCE_QUERY 映射 {@link ExternalInstanceDetailReader}，POLLING 映射
   * {@link ExternalInstanceReader}，避免只支持已知实例详情的 provider 被误报为支持增量扫描。
   * 其余能力（EXTERNAL_FINAL_RESULT、EXTERNAL_INSTANCE_ACTION、
   * EXTERNAL_INSTANCE_CANCEL）由规范动作表达，无端口对应，不校验。
   */
  static final Map<Capability, Class<?>> PORT_BY_CAPABILITY = Map.ofEntries(
      Map.entry(Capability.DEFINITION_DISCOVERY, ExternalProcessDefinitionDiscovery.class),
      Map.entry(Capability.DEFINITION_VALIDATION, ExternalProcessDefinitionValidator.class),
      Map.entry(Capability.DEFINITION_FORM, ExternalProcessDefinitionFormReader.class),
      Map.entry(Capability.EXTERNAL_START, ExternalWorkflowStarter.class),
      Map.entry(Capability.EXTERNAL_START_RECOVERY, ExternalWorkflowStartRecovery.class),
      Map.entry(Capability.EXTERNAL_INSTANCE_QUERY, ExternalInstanceDetailReader.class),
      Map.entry(Capability.EXTERNAL_RECORD_QUERY, ExternalRecordReader.class),
      Map.entry(Capability.EXTERNAL_TASK_QUERY, ExternalTaskReader.class),
      Map.entry(Capability.EXTERNAL_TASK_AUTHORIZATION, ExternalTaskReader.class),
      Map.entry(Capability.EXTERNAL_TASK_ACTION, ExternalWorkflowActionExecutor.class),
      Map.entry(Capability.CALLBACK, ExternalCallbackCodec.class),
      Map.entry(Capability.POLLING, ExternalInstanceReader.class));

  /**
   * 从适配器实例派生能力集；descriptor 声明的细粒度能力与端口实现不一致时抛
   * {@link PlatformException}（启动期失败，禁止留给运行时）。
   *
   * @param adapter            适配器实例（不得为 null）
   * @param extraCapabilities  descriptor 声明的细粒度能力（可空）
   * @param adapterId          适配器 ID（仅用于错误消息）
   */
  static Set<Capability> derive(
      ApprovalWorkflowEngineAdapter adapter, Set<Capability> extraCapabilities, String adapterId) {
    if (extraCapabilities != null) {
      for (Capability capability : extraCapabilities) {
        Class<?> port = PORT_BY_CAPABILITY.get(capability);
        if (port != null && !port.isInstance(adapter)) {
          throw new PlatformException(
              "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
                  + "] descriptor 声明能力 " + capability + " 但适配器未实现端口 "
                  + port.getSimpleName() + ": " + adapterId);
        }
      }
    }
    EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
    if (adapter instanceof ExternalProcessDefinitionDiscovery) {
      capabilities.add(Capability.DEFINITION_DISCOVERY);
    }
    if (adapter instanceof ExternalProcessDefinitionValidator) {
      capabilities.add(Capability.DEFINITION_VALIDATION);
    }
    if (adapter instanceof ExternalProcessDefinitionFormReader) {
      capabilities.add(Capability.DEFINITION_FORM);
    }
    if (adapter instanceof ExternalWorkflowStarter) {
      capabilities.add(Capability.EXTERNAL_START);
    }
    if (adapter instanceof ExternalWorkflowStartRecovery) {
      capabilities.add(Capability.EXTERNAL_START_RECOVERY);
    }
    if (adapter instanceof ExternalInstanceDetailReader) {
      capabilities.add(Capability.EXTERNAL_INSTANCE_QUERY);
    }
    if (adapter instanceof ExternalRecordReader) {
      capabilities.add(Capability.EXTERNAL_RECORD_QUERY);
    }
    if (adapter instanceof ExternalTaskReader) {
      capabilities.add(Capability.EXTERNAL_TASK_QUERY);
    }
    if (adapter instanceof ExternalWorkflowActionExecutor) {
      capabilities.add(Capability.EXTERNAL_TASK_ACTION);
    }
    if (adapter instanceof ExternalCallbackCodec) {
      capabilities.add(Capability.CALLBACK);
    }
    if (extraCapabilities != null) {
      capabilities.addAll(extraCapabilities);
    }
    return Set.copyOf(capabilities);
  }
}
