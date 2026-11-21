package com.ouroboros.workflow.approval.external.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;

/**
 * 外部流程引擎适配器注册表。
 *
 * <p>启动期发现全部 Provider（SPI 服务文件 + Spring Bean 桥接），按 {@code adapterId} 去重；
 * 重复、descriptor 不合法、provider 构造失败时启动失败，禁止静默覆盖。能力集按适配器实现端口派生，
 * 注册后为只读快照，不允许运行时再注册。运行时不得复用注册表中的 adapter 实例，必须经
 * {@link ConnectionAwareAdapterResolver} 按连接创建。
 */
@Service
public class WorkflowEngineAdapterRegistry {

  private static final ResolvedEnterpriseConnection CAPABILITY_PROBE_CONNECTION =
      new ResolvedEnterpriseConnection("__capability_probe__", Map.of("provider", "probe"), null);
  private static final Set<String> SUPPORTED_INTEGRATION_MODES = Set.of("RESULT_ONLY", "FULL_PROCESS");
  /** 派生接入模式：能力不足即 RESULT_ONLY。 */
  public static final String MODE_RESULT_ONLY = "RESULT_ONLY";
  /** 派生接入模式：实例查询 + 任务查询能力齐备。 */
  public static final String MODE_FULL_PROCESS = "FULL_PROCESS";
  /** 交互执行模式：平台可代理外部任务动作（适配器实现动作执行端口）。 */
  public static final String EXECUTION_PROXY = "PROXY";
  /** 交互执行模式：平台只能跳转外部处理，动作在外部客户端完成。 */
  public static final String EXECUTION_LINK = "LINK";

  private final WorkflowEngineAdapterProviderBeanBridge bridge;
  private volatile Map<String, RegisteredAdapter> adapters = Map.of();

  public WorkflowEngineAdapterRegistry(WorkflowEngineAdapterProviderBeanBridge bridge) {
    this.bridge = bridge;
  }

  /**
   * Spring 启动期初始化：SPI 发现 + Bean 桥接聚合、去重、校验、能力派生。
   */
  @PostConstruct
  public void initialize() {
    List<ApprovalWorkflowEngineAdapterProvider> providers = new ArrayList<>();
    SPI.getServiceStream(ApprovalWorkflowEngineAdapterProvider.class).forEach(providers::add);
    providers.addAll(bridge.beanProviders());
    initialize(providers);
  }

  /**
   * 供测试使用的初始化入口：以显式 Provider 列表构建注册表（跳过类路径 SPI 发现）。
   *
   * @param providers 显式 Provider 列表（顺序无关，按 adapterId 去重）
   */
  public void initialize(List<ApprovalWorkflowEngineAdapterProvider> providers) {
    Map<String, RegisteredAdapter> index = new LinkedHashMap<>();
    Map<String, String> providerAdapters = new LinkedHashMap<>();
    for (ApprovalWorkflowEngineAdapterProvider provider : providers) {
      ApprovalWorkflowEngineAdapterDescriptor descriptor = provider.descriptor();
      validateDescriptor(descriptor);
      if (index.containsKey(descriptor.adapterId())) {
        throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
            + "] 适配器 adapterId 重复，禁止静默覆盖: " + descriptor.adapterId());
      }
      String providerName = provider.provider();
      if (providerName == null || !providerName.matches("[a-z0-9-]+")) {
        throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
            + "] 适配器 provider 非法（须匹配 [a-z0-9-]+）: " + providerName);
      }
      String previousAdapter = providerAdapters.putIfAbsent(providerName, descriptor.adapterId());
      if (previousAdapter != null) {
        throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
            + "] provider 重复注册，禁止静默选择第一个: " + providerName
            + " (" + previousAdapter + ", " + descriptor.adapterId() + ")");
      }
      ApprovalWorkflowEngineAdapter adapter = provider.create(CAPABILITY_PROBE_CONNECTION);
      if (adapter == null) {
        throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器实例创建失败: adapterId=" + descriptor.adapterId());
      }
      Set<Capability> capabilities = EngineAdapterCapabilities.derive(
          adapter, descriptor.extraCapabilities(), descriptor.adapterId());
      RegisteredAdapter previous = index.putIfAbsent(
          descriptor.adapterId(),
          new RegisteredAdapter(descriptor, capabilities, provider));
      if (previous != null) {
        throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
            + "] 适配器 adapterId 重复，禁止静默覆盖: " + descriptor.adapterId());
      }
    }
    this.adapters = Collections.unmodifiableMap(index);
  }

  /**
   * 已注册适配器只读列表。
   */
  public List<RegisteredAdapter> listAdapters() {
    return List.copyOf(adapters.values());
  }

  /**
   * 按 adapterId 获取已注册适配器。
   */
  public Optional<RegisteredAdapter> getAdapter(String adapterId) {
    return Optional.ofNullable(adapters.get(adapterId));
  }

  /** 按连接配置中的 provider 查找唯一领域适配器。 */
  public Optional<RegisteredAdapter> getAdapterByProvider(String provider) {
    if (provider == null || provider.isBlank()) {
      return Optional.empty();
    }
    return adapters.values().stream()
        .filter(registered -> provider.equals(registered.provider().provider()))
        .findFirst();
  }

  /**
   * 指定适配器的能力集（空集表示不存在）。
   */
  public Set<Capability> capabilitiesOf(String adapterId) {
    RegisteredAdapter registered = adapters.get(adapterId);
    return registered == null ? Set.of() : registered.capabilities();
  }

  /**
   * 接入模式由适配器实际具备的端口能力派生，不是管理员可填的配置项。
   *
   * <p>"能适配到什么程度"取决于外部引擎提供的能力：实现并声明了实例查询与任务查询端口的适配器
   * 才具备 FULL_PROCESS 语义，其余一律 RESULT_ONLY。这样同一条事实只有一处表达，不会再出现
   * "映射里填了 FULL_PROCESS 但适配器根本没有任务端口"这种需要靠校验才发现的不一致。
   *
   * @return {@link #MODE_FULL_PROCESS} 或 {@link #MODE_RESULT_ONLY}
   */
  public String integrationModeOf(String adapterId) {
    Set<Capability> capabilities = capabilitiesOf(adapterId);
    boolean fullProcess = capabilities.contains(Capability.EXTERNAL_INSTANCE_QUERY)
        && capabilities.contains(Capability.EXTERNAL_TASK_QUERY);
    return fullProcess ? MODE_FULL_PROCESS : MODE_RESULT_ONLY;
  }

  /**
   * 交互执行模式同样由能力派生：适配器实现了外部动作执行端口才能在平台内代理动作，
   * 否则任务只能跳转外部处理（LINK）。这与接入模式一样，不是管理员可填的配置。
   */
  public String executionModeOf(String adapterId) {
    return capabilitiesOf(adapterId).contains(Capability.EXTERNAL_TASK_ACTION)
        ? EXECUTION_PROXY : EXECUTION_LINK;
  }

  /**
   * 适配器是否已注册。
   */
  public boolean hasAdapter(String adapterId) {
    return adapters.containsKey(adapterId);
  }

  private void validateDescriptor(ApprovalWorkflowEngineAdapterDescriptor descriptor) {
    if (descriptor == null) {
      throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器 descriptor 为空");
    }
    String adapterId = descriptor.adapterId();
    if (adapterId == null || !adapterId.matches("[a-z0-9-]+")) {
      throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器 adapterId 非法（须匹配 [a-z0-9-]+）: " + adapterId);
    }
    if (descriptor.displayName() == null || descriptor.displayName().isBlank()) {
      throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器 displayName 为空: " + adapterId);
    }
    if (descriptor.version() == null || descriptor.version().isBlank()) {
      throw new PlatformException("[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND + "] 适配器 version 为空: " + adapterId);
    }
  }
}
