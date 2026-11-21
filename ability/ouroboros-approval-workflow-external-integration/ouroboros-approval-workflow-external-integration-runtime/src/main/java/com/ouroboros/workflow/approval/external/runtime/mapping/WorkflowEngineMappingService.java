package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.util.Optional;

import io.vavr.control.Try;

/**
 * 外部流程引擎映射服务：映射生命周期管理（DRAFT → VALIDATING → VALIDATED → ACTIVE → PAUSED/RETIRED，验证失败 INVALID）。
 */
public interface WorkflowEngineMappingService {

  /**
   * 创建映射草稿（status=DRAFT，version 从 1 开始）。
   */
  Try<WorkflowEngineMapping> create(WorkflowEngineMapping draft);

  /**
   * 执行激活前校验，结果写入 validationMessage；成功进入 VALIDATED，失败进入 INVALID。
   */
  Try<WorkflowEngineMapping> validate(String scope, String mappingId);

  /**
   * 只读校验（不落库、不修改状态）：复用 {@link #validate} 的校验逻辑，供批量验证等
   * 反复运行且不得污染 ACTIVE 映射的场景（Phase 4 M21-T1）。成功返回映射本身，失败返回校验异常。
   */
  Try<WorkflowEngineMapping> dryRunValidate(String scope, String mappingId);

  /**
   * 激活映射（要求 VALIDATED）：同 scope+flowKey 已有 ACTIVE 时先将其置 RETIRED。
   */
  Try<WorkflowEngineMapping> activate(String scope, String mappingId);

  /**
   * 暂停映射（要求 ACTIVE）：禁止新流程启动，允许查询和处理已存在实例。
   */
  Try<WorkflowEngineMapping> pause(String scope, String mappingId);

  /**
   * 下线映射（要求 ACTIVE/PAUSED）：只影响新实例，运行中实例继续使用其绑定的映射版本。
   */
  Try<WorkflowEngineMapping> retire(String scope, String mappingId);

  /**
   * 编辑 ACTIVE/PAUSED 映射生成新草稿（版本 +1，status=DRAFT），并一次应用请求体可编辑字段。
   */
  Try<WorkflowEngineMapping> createDraftWithChanges(String scope, String mappingId, WorkflowEngineMapping changes);

  /**
   * 更新草稿字段（仅 DRAFT/INVALID 状态，状态迁移由调用方保证）；
   * 覆盖可编辑字段：flowKey/adapterId/connectionId/外部定义/authority/resultBindingJson/
   * actionMappingsJson/resultMappingsJson/callbackJson。接入模式由适配器能力派生，不是可编辑字段。
   */
  Try<WorkflowEngineMapping> updateDraft(String scope, String mappingId, WorkflowEngineMapping draft);

  /**
   * 按 flowKey/status 过滤分页查询（参数可空；内存分页，沿用现有 searchList 模式）。
   */
  java.util.List<WorkflowEngineMapping> search(String scope, String flowKey, String status, int offset, int limit);

  /**
   * 按 scope + flowKey 查询 ACTIVE 映射（路由查询入口）。
   */
  Optional<WorkflowEngineMapping> findActive(String scope, String flowKey);

  /**
   * 查询全部 ACTIVE 映射（system-only：轮询补偿/聚合任务扫描用）。
   */
  java.util.List<WorkflowEngineMapping> findActiveAllForSystem();

  /**
   * 查询指定 scope 下的 ACTIVE 映射（用户态聚合查询用）。
   */
  java.util.List<WorkflowEngineMapping> findActiveByScope(String scope);

  /**
   * 查询指定 scope + flowKey 下的全部映射版本（绑定 route 查询用）。
   */
  java.util.List<WorkflowEngineMapping> findByScopeAndFlowKey(String scope, String flowKey);

  /**
   * 按 scope + ID 查询映射；不存在或无权访问返回空 Optional。
   */
  Optional<WorkflowEngineMapping> findById(String scope, String mappingId);

  /**
   * 按 ID 查询映射（system-only：binding/task/event 已持有可信 route key 时使用）。
   */
  Optional<WorkflowEngineMapping> findByIdForSystem(String mappingId);

  /**
   * 按状态查询全部映射（版本清理候选枚举用，Phase 4 M21-T3）。
   */
  java.util.List<WorkflowEngineMapping> findByStatusForSystem(String status);

  /**
   * 为 binding 引用保护取得 mapping 行锁。
   *
   * <p>实现必须在当前事务内以 epoch CAS 更新 mapping。返回 {@code false} 表示映射不存在
   * 或 CAS 在有限重试后未取得锁；成功返回后，调用方在同一事务内完成 binding 插入或删除。
   */
  boolean acquireBindingReferenceLock(String mappingId);

  /**
   * 仅当映射的绑定引用 epoch 仍等于预期值时删除映射。
   *
   * <p>清理流程必须使用该条件删除，避免在引用检查后被并发 binding 引用时仍删除映射。
   * 返回 {@code false} 表示映射不存在或 epoch 已变化，调用方应将本次清理计为跳过。
   */
  boolean deleteMappingIfBindingReferenceEpoch(String mappingId, Long expectedEpoch);

  /**
   * 删除映射（版本清理用，Phase 4 M21-T3）；不存在时 no-op，删除失败抛
   * {@link BusinessException}。调用方必须自行完成"无运行中实例绑定引用"的保护检查。
   */
  void deleteMappingForSystem(String mappingId);
}
