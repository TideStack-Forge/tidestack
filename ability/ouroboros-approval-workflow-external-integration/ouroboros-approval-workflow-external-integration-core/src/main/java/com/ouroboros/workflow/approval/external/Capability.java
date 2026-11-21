package com.ouroboros.workflow.approval.external;

/**
 * 外部引擎能力枚举。
 *
 * <p>端口级能力由 Registry 按适配器实现的端口派生（{@code instanceof} 判定），本枚举不再包含
 * 交互类能力（如 LINK），LINK 由 executionMode 与启动/查询结果中的 externalUrl 表达。
 *
 * <p><b>声明与实现的对应关系</b>：
 * <ul>
 *   <li>有专属端口的取值（DEFINITION_*、EXTERNAL_START*、EXTERNAL_INSTANCE_QUERY、
 *       EXTERNAL_RECORD_QUERY、EXTERNAL_TASK_QUERY、EXTERNAL_TASK_ACTION、CALLBACK）：
 *       descriptor 声明后必须在启动期实现对应端口，否则
 *       {@code EngineAdapterCapabilities.derive} 抛 {@code EXTERNAL_CAPABILITY_UNSUPPORTED}。</li>
 *   <li>无专属端口的取值（{@link #EXTERNAL_FINAL_RESULT}、{@link #EXTERNAL_INSTANCE_ACTION}、
 *       {@link #EXTERNAL_INSTANCE_CANCEL}）：语义由规范动作与结果映射表达，没有可校验的端口，
 *       只能由 descriptor 显式声明。目前所有 provider 都不声明后两者，对应的实例动作入口
 *       恒定返回"能力不支持"——这是**未落地的预留项**，不是已实现的对外承诺。</li>
 *   <li>{@link #POLLING} 由 {@link ExternalInstanceReader} 承载但**不由 ports 派生**：
 *       只实现"查询已知实例详情"的适配器不应被当作支持增量扫描，因此它必须显式声明，
 *       声明后启动期校验 {@code ExternalInstanceReader} 端口。</li>
 * </ul>
 */
public enum Capability {

  /** 外部流程定义发现。由 {@link ExternalProcessDefinitionDiscovery} 派生。 */
  DEFINITION_DISCOVERY,

  /** 外部流程定义验证。 */
  DEFINITION_VALIDATION,

  /** 外部流程定义表单读取。 */
  DEFINITION_FORM,

  /** 外部流程启动。由 {@link ExternalWorkflowStarter} 派生。 */
  EXTERNAL_START,

  /** 外部流程启动结果恢复。由 {@link ExternalWorkflowStartRecovery} 派生。 */
  EXTERNAL_START_RECOVERY,

  /** 外部实例详情查询。由 {@link ExternalInstanceDetailReader} 派生。 */
  EXTERNAL_INSTANCE_QUERY,

  /** 外部终态结果获取（回调或轮询归一化后的结果解析）。无专属端口，须显式声明。 */
  EXTERNAL_FINAL_RESULT,

  /** 外部审批记录/时间线查询。由 {@link ExternalRecordReader} 派生。 */
  EXTERNAL_RECORD_QUERY,

  /** 外部任务查询。由 {@link ExternalTaskReader} 派生。 */
  EXTERNAL_TASK_QUERY,

  /** 动作前读取外部权威任务快照，用于动态处理人/状态授权校验；须由适配器显式声明。 */
  EXTERNAL_TASK_AUTHORIZATION,

  /** 外部任务动作执行。由 {@link ExternalWorkflowActionExecutor} 派生。 */
  EXTERNAL_TASK_ACTION,

  /** 外部实例级动作（暂停/恢复等）。无专属端口，须显式声明；当前无 provider 声明。 */
  EXTERNAL_INSTANCE_ACTION,

  /** 外部发起人撤销。无专属端口，须显式声明；当前无 provider 声明。 */
  EXTERNAL_INSTANCE_CANCEL,

  /** 回调推送接收。由 {@link ExternalCallbackCodec} 派生。 */
  CALLBACK,

  /**
   * 按时间窗/游标轮询补偿。由 {@link ExternalInstanceReader} 承载但不由 ports 派生：
   * 只支持"查询已知实例详情"的适配器不应被误判为支持增量扫描，故须显式声明后再校验端口。
   */
  POLLING
}
