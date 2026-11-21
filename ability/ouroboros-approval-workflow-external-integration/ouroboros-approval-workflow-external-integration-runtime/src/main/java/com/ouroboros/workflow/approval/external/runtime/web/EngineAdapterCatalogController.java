package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.web.EnterpriseConnectionEditor;
import com.ouroboros.security.annotation.Authority;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionDiscovery;
import com.ouroboros.workflow.approval.external.DefinitionDiscoveryRequest;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionPage;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;

import io.vavr.control.Try;

/**
 * 适配器目录与外部流程定义/连接健康检查入口（M13，管理入口的只读部分）。
 *
 * <p>目录公开只读，不返回任何凭据；写操作（映射 CRUD/激活）见
 * {@code ApprovalWorkflowEngineMappingController}。
 */
@RestController
@RequestMapping("/api/approval-workflow/engine-adapters")
public class EngineAdapterCatalogController {

  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final EnterpriseConnectionCatalog connectionConfigProvider;
  private final EngineMappingPermissionGuard permissionGuard;
  private final EnterpriseConnectionProviderRegistry connectionProviders;

  @Autowired
  public EngineAdapterCatalogController(
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      EnterpriseConnectionCatalog connectionConfigProvider,
      EngineMappingPermissionGuard permissionGuard,
      EnterpriseConnectionProviderRegistry connectionProviders) {
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.connectionConfigProvider = connectionConfigProvider;
    this.permissionGuard = permissionGuard;
    this.connectionProviders = connectionProviders;
  }

  @GetMapping
  @Authority("systemSetting.configurationManagement")
  public ItemsWebApiResult<Map<String, Object>> listAdapters() {
    return ItemsWebApiResult.ok(
        registry.listAdapters().stream().map(RegisteredAdapter::catalogView).toList());
  }

  /** 返回 provider 注册的连接 schema；响应只包含字段定义，不包含任何凭据值。 */
  @GetMapping("/{adapterId}/connection-schema")
  @Authority("systemSetting.configurationManagement")
  public WebApiResult<Map<String, Object>> connectionSchema(@PathVariable String adapterId) {
    return WebApiResult.ok(connectionProviders.schemaProviderOf(requireAdapter(adapterId).provider().provider())
        .orElseThrow(() -> new BusinessException("企业连接 provider 未安装")).connectionSchema());
  }

  /**
   * 返回配置中心 combo 行中 provider 专属字段的 AMIS schema。
   *
   * <p>公共连接字段仍由配置元数据直接提供；这里仅把注册 provider 的字段描述转换成
   * 原生 AMIS 控件，因此新增 provider 不需要修改配置中心的静态 metadata。
  */
  @GetMapping("/connection-editor-schema")
  @Authority("systemSetting.configurationManagement")
  public WebApiResult<Map<String, Object>> connectionEditorSchema(
      @RequestParam(required = false) String provider) {
    if (provider == null || provider.isBlank()) {
      return WebApiResult.ok(Map.of("type", "group", "body", List.of()));
    }
    RegisteredAdapter registered = registry.getAdapterByProvider(provider).orElse(null);
    if (registered == null) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
              + "] provider 不存在: " + provider);
    }
    return WebApiResult.ok(new EnterpriseConnectionEditor().render(connectionProviders.schemaProviderOf(provider)
        .orElseThrow(() -> new BusinessException("企业连接 provider 未安装")).connectionSchema()));
  }

  /**
   * 返回统一连接目录的安全摘要。流程映射页面使用 connectionId 作为唯一可编辑路由；
   * adapterId 由服务端根据 provider 派生。
   *
   * <p>与同类目录端点一致地声明权限点：该端点暴露全部 connectionId 与 provider，
   * 且被映射页下拉直接使用，缺权限标注会让它在 RBAC 元数据里不可见（除超管外一律 403）。
   */
  @GetMapping("/connections")
  @Authority("systemSetting.configurationManagement")
  public ItemsWebApiResult<EnterpriseConnectionCatalog.ConnectionSummary> listConnections() {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_VIEW);
    return ItemsWebApiResult.ok(connectionConfigProvider.listSummaries());
  }

  @GetMapping("/{adapterId}/connections/{connectionId}/definitions")
  public ResponseEntity<ExternalProcessDefinitionPage> discoverDefinitions(
      @PathVariable String adapterId,
      @PathVariable String connectionId,
      @RequestParam(required = false) String keyword) {
    requireAdapter(adapterId);
    ExternalProcessDefinitionDiscovery catalog = adapterResolver.resolvePort(
        adapterId, connectionId, ExternalProcessDefinitionDiscovery.class);
    Try<ExternalProcessDefinitionPage> discovered =
        catalog.discoverDefinitions(new DefinitionDiscoveryRequest(connectionId, keyword));
    if (discovered.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 外部定义发现失败: " + discovered.getCause().getMessage(), discovered.getCause());
    }
    return ResponseEntity.ok(discovered.get());
  }

  @PostMapping("/{adapterId}/connections/{connectionId}/health")
  public ResponseEntity<ExternalHealth> checkHealth(
      @PathVariable String adapterId,
      @PathVariable String connectionId) {
    permissionGuard.requirePermission(EngineMappingPermissions.CONNECTION_HEALTH);
    RegisteredAdapter registered = registry.getAdapter(adapterId).orElse(null);
    if (registered == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }
    Try<ExternalHealth> health = adapterResolver.resolve(adapterId, connectionId).health();
    if (health.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 连接健康检查失败: " + health.getCause().getMessage(), health.getCause());
    }
    return ResponseEntity.ok(health.get());
  }

  private RegisteredAdapter requireAdapter(String adapterId) {
    RegisteredAdapter registered = registry.getAdapter(adapterId).orElse(null);
    if (registered == null) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器不存在: " + adapterId);
    }
    return registered;
  }

}
