package com.ouroboros.workflow.approval.external;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;


/**
 * 外部流程引擎适配器提供者。
 *
 * <p>每一种外部引擎实现都必须注册一个 Provider：提供稳定的适配器描述，并按连接创建适配器实例。
 * 注册约定见设计文档「Provider 注册与发现」：除 SPI 服务文件外，需要 Spring 生命周期的模块还必须
 * 在 {@code META-INF/spring.factories} 注册其自动配置。
 */
public interface ApprovalWorkflowEngineAdapterProvider {

  /**
   * 连接 schema 使用的稳定 provider 名称。它是连接配置中的唯一平台标识；领域映射仍通过
   * 已注册适配器解析连接，不再让调用方同时提交 provider 和 adapter。
   */
  default String provider() {
    return descriptor().adapterId();
  }

  /**
   * 适配器静态描述。只通过本方法暴露，不从实现类名推断。
   */
  ApprovalWorkflowEngineAdapterDescriptor descriptor();

  /**
   * 按连接创建适配器实例。Provider 保持无状态；确有状态需要由调用方传入连接承载。
   *
   * @param connection 连接配置；运行时必须由 connection-aware resolver 传入真实连接
   */
  ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection);
}
