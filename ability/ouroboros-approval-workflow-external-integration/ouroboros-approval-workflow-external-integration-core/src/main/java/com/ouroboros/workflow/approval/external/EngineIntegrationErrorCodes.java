package com.ouroboros.workflow.approval.external;

/**
 * 审批流外部引擎对接统一错误码常量（平台注册见 integration-runtime 的错误码元数据）。
 */
public final class EngineIntegrationErrorCodes {

  /** 适配器不存在。 */
  public static final String ENGINE_ADAPTER_NOT_FOUND = "ENGINE_ADAPTER_NOT_FOUND";

  /** 连接不可用（健康检查失败）。 */
  public static final String ENGINE_CONNECTION_UNAVAILABLE = "ENGINE_CONNECTION_UNAVAILABLE";

  /** 映射未激活。 */
  public static final String WORKFLOW_MAPPING_NOT_ACTIVE = "WORKFLOW_MAPPING_NOT_ACTIVE";

  /** 流程未配置路由（unmapped-policy=reject 或无映射）。 */
  public static final String WORKFLOW_ROUTE_NOT_CONFIGURED = "WORKFLOW_ROUTE_NOT_CONFIGURED";

  /** 历史错误码，保留标识兼容；统一发起入口不再产生此错误。 */
  public static final String WORKFLOW_RESULT_ONLY_ENTRY_REQUIRED = "WORKFLOW_RESULT_ONLY_ENTRY_REQUIRED";

  /** 外部流程定义不存在。 */
  public static final String EXTERNAL_DEFINITION_NOT_FOUND = "EXTERNAL_DEFINITION_NOT_FOUND";

  /** 外部引擎不支持该能力。 */
  public static final String EXTERNAL_CAPABILITY_UNSUPPORTED = "EXTERNAL_CAPABILITY_UNSUPPORTED";

  /** 规范动作未配置外部动作映射。 */
  public static final String EXTERNAL_ACTION_NOT_MAPPED = "EXTERNAL_ACTION_NOT_MAPPED";

  /** 需要跳转外部处理。 */
  public static final String EXTERNAL_INTERACTION_REQUIRED = "EXTERNAL_INTERACTION_REQUIRED";

  /** 外部事件乱序（晚于已处理状态）。 */
  public static final String EXTERNAL_EVENT_STALE = "EXTERNAL_EVENT_STALE";

  /** 外部事件状态未知（不自动结束流程）。 */
  public static final String EXTERNAL_EVENT_UNKNOWN = "EXTERNAL_EVENT_UNKNOWN";

  /** 外部动作冲突（状态不满足执行条件）。 */
  public static final String EXTERNAL_ACTION_CONFLICT = "EXTERNAL_ACTION_CONFLICT";

  /** 平台用户到外部用户标识缺少映射（禁止原样透传）。 */
  public static final String EXTERNAL_USER_MAPPING_MISSING = "EXTERNAL_USER_MAPPING_MISSING";

  /** 外部实例/任务绑定缺失，无法解析外部引用。 */
  public static final String EXTERNAL_BINDING_MISSING = "EXTERNAL_BINDING_MISSING";

  /** 映射配置校验失败（动作/结果映射缺失或非法）。 */
  public static final String EXTERNAL_MAPPING_INVALID = "EXTERNAL_MAPPING_INVALID";

  /** 缺少映射管理权限点（管理入口独立权限）。 */
  public static final String EXTERNAL_PERMISSION_DENIED = "EXTERNAL_PERMISSION_DENIED";

  /** 映射/绑定持久化失败（删除映射、迁移绑定写入等本地存储错误）。 */
  public static final String WORKFLOW_MAPPING_PERSISTENCE_FAILED = "WORKFLOW_MAPPING_PERSISTENCE_FAILED";

  private EngineIntegrationErrorCodes() {
  }
}
