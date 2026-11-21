package com.ouroboros.workflow.approval.external;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 外部引擎 fake 共享的纯内存语义核心（B1：生产 fake 与测试 fake 状态机去重）。
 *
 * <p>实例状态机与幂等去重的唯一事实来源：实例状态、幂等键→启动结果、任务、记录
 * 四类状态均在此持有。start 幂等键去重（同键返回原实例）、execute 结果码推进终态
 * （仅 APPROVED/REJECTED/CANCELED 翻到 FINISHED，未知结果码不推进）为本核心语义。
 *
 * <p>纯 Java 实现，无 Spring 依赖。适配器层仅保留端口面（类型构造）与控制面
 * （options 开关 / setter 钩子），状态语义一律委托本核心。
 */
public final class InMemoryExternalEngineCore {

  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_FINISHED = "FINISHED";

  /** 适配器声明的动作词汇表（与 WorkflowEngineMappingValidator.CANONICAL_ACTIONS 同步维护）。 */
  public static final List<String> SUPPORTED_ACTIONS =
      List.of("approve", "reject", "transfer", "remind", "cancel");

  /** 推进终态的结果码：execute 返回这些码时实例翻到 FINISHED，其余码不推进。 */
  private static final Set<String> TERMINAL_RESULT_CODES = Set.of("APPROVED", "REJECTED", "CANCELED");

  private final AtomicInteger instanceCounter = new AtomicInteger();
  private final AtomicInteger taskCounter = new AtomicInteger();
  private final Map<String, String> instanceStatuses = new LinkedHashMap<>();
  private final Map<String, String> instanceResults = new LinkedHashMap<>();
  private final Map<String, ExternalStartResult> idempotentResults = new LinkedHashMap<>();
  private final Map<String, ExternalActionCommand> idempotentActionCommands = new LinkedHashMap<>();
  private final Map<String, ExternalActionResult> idempotentActionResults = new LinkedHashMap<>();
  private final List<ExternalTaskSnapshot> tasks = new ArrayList<>();
  private final List<ExternalRecordPage.ExternalRecordEntry> records = new ArrayList<>();

  /**
   * 幂等创建实例并登记首个待办任务：同 idempotencyKey 返回原启动结果，不重复
   * 创建实例/任务；idempotencyKey 为 null 时每次创建新实例。
   *
   * @param idempotencyKey   幂等键（可空）
   * @param definitionId     启动的流程定义 ID（fake 语义不按定义分支，保留以锚定 start 语义）
   * @param instanceIdPrefix 实例 ID 前缀（适配器端口面，如 "fake-"）
   * @param instanceUrlBase  实例详情 URL 基址（适配器端口面）
   * @param originatorUserId 发起人（写入首个待办任务 assignee）
   * @param initialStatus    初始状态（同时写入实例与首个任务）
   */
  public ExternalStartResult start(
      String idempotencyKey,
      String definitionId,
      String instanceIdPrefix,
      String instanceUrlBase,
      String originatorUserId,
      String initialStatus) {
    if (idempotencyKey != null) {
      ExternalStartResult existing = idempotentResults.get(idempotencyKey);
      if (existing != null) {
        return existing;
      }
    }
    String instanceId = instanceIdPrefix + instanceCounter.incrementAndGet();
    ExternalStartResult result = new ExternalStartResult(instanceId, instanceUrlBase + instanceId, null);
    if (idempotencyKey != null) {
      idempotentResults.put(idempotencyKey, result);
    }
    instanceStatuses.put(instanceId, initialStatus);
    String taskId = instanceIdPrefix + "task-" + taskCounter.incrementAndGet();
    tasks.add(new ExternalTaskSnapshot(
        taskId, "node-1", originatorUserId, initialStatus,
        instanceUrlBase + instanceId + "/task/" + taskId,
        Map.of("externalInstanceId", instanceId)));
    return result;
  }

  /** 实例当前状态；未知实例返回 null。 */
  public String getStatus(String externalInstanceId) {
    return instanceStatuses.get(externalInstanceId);
  }

  /** 写入实例状态。 */
  public void setStatus(String externalInstanceId, String status) {
    instanceStatuses.put(externalInstanceId, status);
  }

  /** 全部实例状态（活视图，供查询轮询端口遍历）。 */
  public Map<String, String> getStatuses() {
    return instanceStatuses;
  }

  /** 实例当前规范结果；运行中或未知实例返回 null。 */
  public String getResult(String externalInstanceId) {
    return instanceResults.get(externalInstanceId);
  }

  /** 幂等键对应的启动结果；无记录返回 null。 */
  public ExternalStartResult getIdempotencyResult(String idempotencyKey) {
    return idempotentResults.get(idempotencyKey);
  }

  /** 写入幂等键→启动结果。 */
  public void putIdempotencyResult(String idempotencyKey, ExternalStartResult result) {
    idempotentResults.put(idempotencyKey, result);
  }

  /** 追加外部任务。 */
  public void addTask(ExternalTaskSnapshot task) {
    tasks.add(task);
  }

  /** 全部外部任务（活视图）。 */
  public List<ExternalTaskSnapshot> getTasks() {
    return tasks;
  }

  /** 追加审批记录（时间线项，时间由 fake 固定）。 */
  public void addRecord(String nodeId, String operator, String action, String comment) {
    records.add(new ExternalRecordPage.ExternalRecordEntry(
        nodeId, operator, action, comment, "2026-08-26T00:00:00"));
  }

  /** 全部审批记录（活视图）。 */
  public List<ExternalRecordPage.ExternalRecordEntry> getRecords() {
    return records;
  }

  /**
   * 按 execute 结果码推进实例状态：仅终态码（APPROVED/REJECTED/CANCELED）把实例
   * 翻到 FINISHED；未知结果码或 null 不推进（与运行时"未知状态不自动结束"规则一致）。
   */
  public void advanceOnResult(String externalInstanceId, String resultStatus) {
    if (resultStatus != null && TERMINAL_RESULT_CODES.contains(resultStatus)) {
      instanceStatuses.put(externalInstanceId, STATUS_FINISHED);
      instanceResults.put(externalInstanceId, resultStatus);
    }
  }

  /** 按动作幂等键执行一次；同键异参直接拒绝。 */
  public ExternalActionResult executeIdempotently(
      ExternalActionCommand command, ExternalActionResult result) {
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("外部动作幂等键不得为空");
    }
    ExternalActionCommand existingCommand = idempotentActionCommands.get(command.idempotencyKey());
    if (existingCommand != null) {
      if (!existingCommand.equals(command)) {
        throw new IllegalArgumentException("同一外部动作幂等键对应了不同命令: " + command.idempotencyKey());
      }
      return idempotentActionResults.get(command.idempotencyKey());
    }
    idempotentActionCommands.put(command.idempotencyKey(), command);
    idempotentActionResults.put(command.idempotencyKey(), result);
    if (result.accepted() && command.externalInstanceRef() != null) {
      advanceOnResult(command.externalInstanceRef().externalInstanceId(), result.resultStatus());
    }
    return result;
  }
}
