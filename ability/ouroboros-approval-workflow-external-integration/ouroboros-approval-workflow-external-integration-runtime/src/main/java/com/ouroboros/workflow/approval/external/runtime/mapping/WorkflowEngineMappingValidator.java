package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

import io.vavr.control.Try;

/**
 * 映射激活前校验：定义存在性、接入模式能力、authority 能力、动作/结果覆盖、定义校验与连接健康检查。
 * 校验中任何外部 HTTP 调用不得放在数据库事务内（由调用方保证）。
 */
@Component
public class WorkflowEngineMappingValidator {

  public static final String AUTHORITY_EXTERNAL_ENGINE = "EXTERNAL_ENGINE";
  public static final String AUTHORITY_OUROBOROS = "OUROBOROS";
  public static final String MODE_RESULT_ONLY = "RESULT_ONLY";
  public static final String MODE_FULL_PROCESS = "FULL_PROCESS";

  /** 平台规范动作集（与设计文档一致；remind 为可选动作）。契约测试等外部引用本常量，避免重复声明漂移。 */
  public static final Set<String> CANONICAL_ACTIONS = Set.of(
      "approve", "reject", "rollback", "transfer", "addSign", "freeApprove",
      "complete", "cancel", "pause", "resume", "remind");

  /** 结果映射必须覆盖的终态（外部状态键）。 */
  static final Set<String> REQUIRED_RESULT_STATUSES = Set.of("RUNNING", "APPROVED", "REJECTED", "CANCELED", "ERROR");

  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final WorkflowModelService workflowModelService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public WorkflowEngineMappingValidator(
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      WorkflowModelService workflowModelService) {
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.workflowModelService = workflowModelService;
  }

  /**
   * 执行全部校验；任一失败返回失败（message 已包含具体原因）。
   */
  public Try<Void> run(WorkflowEngineMapping mapping) {
    try {
      validateAdapterExists(mapping);
      validateFlowKeyExists(mapping);
      validateModeConstraints(mapping);
      validateDescriptorConstraints(mapping);
      validateCapabilities(mapping);
      validateActionMappings(mapping);
      validateResultMappings(mapping);
      validateDefinition(mapping);
      validateHealth(mapping);
      return Try.success(null);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
  }

  private void validateAdapterExists(WorkflowEngineMapping mapping) {
    if (!registry.hasAdapter(mapping.getAdapterId())) {
      throw fail("适配器不存在: " + mapping.getAdapterId(), "ENGINE_ADAPTER_NOT_FOUND");
    }
  }

  private void validateFlowKeyExists(WorkflowEngineMapping mapping) {
    if (workflowModelService == null || workflowModelService.getByPrimaryKey(mapping.getFlowKey()) == null) {
      throw fail("流程标识不存在于 WorkflowModel: " + mapping.getFlowKey(), "WORKFLOW_MAPPING_NOT_ACTIVE");
    }
  }

  /**
   * 接入模式不是可配置项：它由适配器实际具备的端口能力派生。因此这里只校验"派生结果"与映射
   * 声明的动作映射是否自洽——RESULT_ONLY 不可能有任务动作。authority 同样派生，不再是管理员字段。
   */
  private void validateModeConstraints(WorkflowEngineMapping mapping) {
    String authority = mapping.getAuthority();
    if (!AUTHORITY_EXTERNAL_ENGINE.equals(authority) && !AUTHORITY_OUROBOROS.equals(authority)) {
      throw fail("authority 必须为 EXTERNAL_ENGINE 或 OUROBOROS: " + authority, "WORKFLOW_MAPPING_NOT_ACTIVE");
    }
    if (AUTHORITY_EXTERNAL_ENGINE.equals(authority)
        && WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY.equals(integrationModeOf(mapping))
        && hasActionMappings(mapping)) {
      throw fail("RESULT_ONLY 不允许配置任务动作映射: " + mapping.getId(), "WORKFLOW_MAPPING_NOT_ACTIVE");
    }
  }

  /** 映射的接入模式：由注册表按适配器能力派生。 */
  public String integrationModeOf(WorkflowEngineMapping mapping) {
    return registry.integrationModeOf(mapping.getAdapterId());
  }

  /**
   * 激活期能力校验：按 authority/integrationMode 对照 Registry 派生能力，缺失能力在激活前拦截。
   */
  private void validateCapabilities(WorkflowEngineMapping mapping) {
    Set<Capability> capabilities = registry.capabilitiesOf(mapping.getAdapterId());
    Set<Capability> required = EnumSet.noneOf(Capability.class);
    if (AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      required.add(Capability.EXTERNAL_START);
      required.add(Capability.DEFINITION_VALIDATION);
      if (WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY.equals(integrationModeOf(mapping))) {
        required.add(Capability.EXTERNAL_FINAL_RESULT);
        if (!capabilities.contains(Capability.CALLBACK) && !capabilities.contains(Capability.POLLING)) {
          required.add(Capability.CALLBACK);
          required.add(Capability.POLLING);
        }
      } else if (WorkflowEngineAdapterRegistry.MODE_FULL_PROCESS.equals(integrationModeOf(mapping))) {
        required.add(Capability.EXTERNAL_INSTANCE_QUERY);
        required.add(Capability.EXTERNAL_TASK_QUERY);
      }
    }
    Set<Capability> missing = required.stream()
        .filter(capability -> !capabilities.contains(capability))
        .collect(java.util.stream.Collectors.toSet());
    if (!missing.isEmpty()) {
      throw fail("适配器缺少激活所需能力: " + missing.stream().map(Enum::name).sorted().toList()
          + "（映射 " + mapping.getId() + "，authority=" + mapping.getAuthority()
          + ", integrationMode=" + integrationModeOf(mapping) + "）", "EXTERNAL_CAPABILITY_UNSUPPORTED");
    }
  }

  /**
   * 描述符约束（M13）：接入模式支持、全局禁用动作、FULL_PROCESS 任务动作的连接应用类型。
   * 镜像模式（authority=OUROBOROS）不受接入模式与禁用动作限制（镜像动作前缀校验见 Phase 3 M18）。
   */
  private void validateDescriptorConstraints(WorkflowEngineMapping mapping) {
    if (!AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      return;
    }
    RegisteredAdapter registered = registry.getAdapter(mapping.getAdapterId()).orElseThrow();
    var descriptor = registered.descriptor();

    if (hasActionMappings(mapping) && !descriptor.disabledActions().isEmpty()) {
      JsonNode array = parseArray(mapping.getActionMappingsJson());
      for (JsonNode item : array) {
        String systemAction = item.path("systemAction").asText(null);
        if (systemAction != null && descriptor.disabledActions().contains(systemAction)) {
          throw fail("动作已被适配器禁用: " + systemAction + "（适配器 " + mapping.getAdapterId() + "）",
              "EXTERNAL_ACTION_NOT_MAPPED");
        }
      }
    }

  }

  private void validateActionMappings(WorkflowEngineMapping mapping) {
    if (!hasActionMappings(mapping)) {
      return;
    }
    // authority=OUROBOROS 表示"本地内置引擎执行"，动作由内置引擎自己完成，
    // 不存在外部动作可映射。此前这里要求动作必须形如 CUSTOM_SHORTCUT:agree|refuse ——
    // 那是镜像快捷回调的约定，而处理该回调的代码已随镜像投影一并删除（无任何消费者），
    // 于是该约束只剩"接受一份永远不会被执行的配置"。改为直接拒绝。
    if (AUTHORITY_OUROBOROS.equals(mapping.getAuthority())) {
      throw fail("authority=OUROBOROS 的映射由本地内置引擎执行，不允许配置动作映射: "
          + mapping.getId(), "EXTERNAL_ACTION_NOT_MAPPED");
    }
    RegisteredAdapter registered = registry.getAdapter(mapping.getAdapterId()).orElseThrow();
    JsonNode array = parseArray(mapping.getActionMappingsJson());
    for (JsonNode item : array) {
      String systemAction = item.path("systemAction").asText(null);
      String externalAction = item.path("externalAction").asText(null);
      if (systemAction == null || systemAction.isBlank() || externalAction == null || externalAction.isBlank()) {
        throw fail("动作映射条目缺少 systemAction/externalAction: " + mapping.getId(), "EXTERNAL_ACTION_NOT_MAPPED");
      }
      if (!CANONICAL_ACTIONS.contains(systemAction)) {
        throw fail("动作不在平台规范动作集内: " + systemAction, "EXTERNAL_ACTION_NOT_MAPPED");
      }
      if (AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())
          && !registered.descriptor().canonicalActions().contains(systemAction)) {
        throw fail("适配器未声明支持该动作: " + systemAction, "EXTERNAL_ACTION_NOT_MAPPED");
      }
    }
  }

  private void validateResultMappings(WorkflowEngineMapping mapping) {
    if (!hasResultMappings(mapping)) {
      throw fail("resultMappings 不能为空，必须覆盖运行中、成功、拒绝、取消、错误终态: " + mapping.getId(),
          EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID);
    }
    JsonNode array = parseArray(mapping.getResultMappingsJson());
    Set<String> covered = new java.util.HashSet<>();
    for (JsonNode item : array) {
      String status = item.path("externalStatus").asText(null);
      if (status != null) {
        covered.add(status);
      }
    }
    for (String required : REQUIRED_RESULT_STATUSES) {
      if (!covered.contains(required)) {
        throw fail("resultMappings 缺少必需终态: " + required + "（映射 " + mapping.getId() + "）",
            EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID);
      }
    }
  }

  private void validateDefinition(WorkflowEngineMapping mapping) {
    ExternalProcessDefinitionValidator catalog = adapterResolver.resolvePort(
        mapping, ExternalProcessDefinitionValidator.class);
    ExternalProcessDefinitionRef ref = new ExternalProcessDefinitionRef(
        mapping.getExternalDefinitionNamespace(),
        mapping.getExternalDefinitionId(),
        mapping.getExternalDefinitionVersion(),
        mapping.getExternalDefinitionSchemaHash());
    Try<?> result = catalog.validateDefinition(ref);
    if (result.isFailure()) {
      throw fail("外部定义校验失败: " + mapping.getExternalDefinitionId() + " - " + result.getCause().getMessage(),
          "EXTERNAL_DEFINITION_NOT_FOUND");
    }
  }

  private void validateHealth(WorkflowEngineMapping mapping) {
    Try<ExternalHealth> healthTry = adapterResolver.resolve(mapping).health();
    if (healthTry.isFailure() || !healthTry.get().healthy()) {
      throw fail("连接健康检查未通过: " + mapping.getConnectionId(), "ENGINE_CONNECTION_UNAVAILABLE");
    }
  }

  private boolean hasActionMappings(WorkflowEngineMapping mapping) {
    return mapping.getActionMappingsJson() != null && !mapping.getActionMappingsJson().isBlank();
  }

  private boolean hasResultMappings(WorkflowEngineMapping mapping) {
    return mapping.getResultMappingsJson() != null && !mapping.getResultMappingsJson().isBlank();
  }

  private JsonNode parseArray(String json) {
    try {
      JsonNode node = objectMapper.readTree(json);
      if (!node.isArray()) {
        throw fail("映射 JSON 必须是数组", EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID);
      }
      return node;
    } catch (Exception ex) {
      throw fail("映射 JSON 解析失败", EngineIntegrationErrorCodes.EXTERNAL_MAPPING_INVALID);
    }
  }

  private BusinessException fail(String message, String code) {
    return new BusinessException("[" + code + "] " + message);
  }
}
