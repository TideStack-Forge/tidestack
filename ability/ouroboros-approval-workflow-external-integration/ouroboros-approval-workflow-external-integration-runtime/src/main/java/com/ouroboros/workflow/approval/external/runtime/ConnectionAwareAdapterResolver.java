package com.ouroboros.workflow.approval.external.runtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

/**
 * 连接感知的适配器解析器。
 *
 * <p>所有运行时调用都必须通过本类按 {@code adapterId + connectionId} 读取连接并调用
 * {@link com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider#create}。
 * 注册表只保存 provider、descriptor 和能力快照，不保存可复用的 adapter 实例。
 */
@Component
public class ConnectionAwareAdapterResolver implements com.ouroboros.workflow.approval.external.ConnectionAdapterResolver {

  private final WorkflowEngineAdapterRegistry registry;
  private final EnterpriseConnectionCatalog connectionConfigProvider;

  @Autowired
  public ConnectionAwareAdapterResolver(
      WorkflowEngineAdapterRegistry registry,
      EnterpriseConnectionCatalog connectionConfigProvider) {
    this.registry = registry;
    this.connectionConfigProvider = connectionConfigProvider;
  }

  public ApprovalWorkflowEngineAdapter resolve(String adapterId, String connectionId) {
    RegisteredAdapter registered = requireRegistered(adapterId);
    ResolvedEnterpriseConnection connection = connectionConfigProvider.resolve(connectionId, registered.provider().provider())
        .orElseThrow(() -> new BusinessException(
            "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
                + "] 连接配置不存在: " + adapterId + "/" + connectionId));
    ApprovalWorkflowEngineAdapter adapter = registered.provider().create(connection);
    if (adapter == null) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 连接级适配器创建失败: " + adapterId + "/" + connectionId);
    }
    return adapter;
  }

  public ApprovalWorkflowEngineAdapter resolve(WorkflowEngineMapping mapping) {
    return resolve(mapping.getAdapterId(), mapping.getConnectionId());
  }

  public <T> T resolvePort(String adapterId, String connectionId, Class<T> portType) {
    ApprovalWorkflowEngineAdapter adapter = resolve(adapterId, connectionId);
    if (!portType.isInstance(adapter)) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
              + "] 适配器不支持端口 " + portType.getSimpleName() + ": " + adapterId);
    }
    return portType.cast(adapter);
  }

  public <T> T resolvePort(WorkflowEngineMapping mapping, Class<T> portType) {
    return resolvePort(mapping.getAdapterId(), mapping.getConnectionId(), portType);
  }

  private RegisteredAdapter requireRegistered(String adapterId) {
    return registry.getAdapter(adapterId).orElseThrow(() -> new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器不存在: " + adapterId));
  }
}
