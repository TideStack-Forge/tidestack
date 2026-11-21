package com.ouroboros.workflow.approval.external.runtime;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;

/**
 * Provider 的 Spring Bean 桥接：把容器内注册的 Provider Bean 暴露给 {@link WorkflowEngineAdapterRegistry}。
 *
 * <p>适配器需要 Spring 生命周期时，通过本桥接显式注册；SPI 服务文件仍是正式发现入口，
 * 禁止通过静态全局变量持有 Spring Bean。
 */
@Component
public class WorkflowEngineAdapterProviderBeanBridge {

  private final List<ApprovalWorkflowEngineAdapterProvider> beanProviders;

  @Autowired
  public WorkflowEngineAdapterProviderBeanBridge(
      @Autowired(required = false) List<ApprovalWorkflowEngineAdapterProvider> beanProviders) {
    this.beanProviders = beanProviders == null ? List.of() : List.copyOf(beanProviders);
  }

  /**
   * Spring 容器中注册的 Provider Bean（只读快照）。
   */
  public List<ApprovalWorkflowEngineAdapterProvider> beanProviders() {
    return beanProviders;
  }
}
