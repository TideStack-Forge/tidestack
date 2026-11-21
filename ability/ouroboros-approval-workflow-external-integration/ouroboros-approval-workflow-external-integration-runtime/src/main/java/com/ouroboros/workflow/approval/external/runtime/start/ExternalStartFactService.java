package com.ouroboros.workflow.approval.external.runtime.start;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCase;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;

import io.vavr.control.Try;

/**
 * 外部启动事实仓储：远程 HTTP start 前先 reserve，远程响应与本地 binding 回填按 claim token fencing。
 */
@Service
public class ExternalStartFactService extends SimpleModelServiceImpl<String, ExternalStartFact> {

  public static final int DEFAULT_LEASE_SECONDS = 300;
  private static final int MAX_RECOVERY_SCAN_LIMIT = 1_000;

  /** 启动事实也是 mapping 的运行时引用，reserve 必须与版本清理竞争同一行锁。 */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private WorkflowEngineMappingService mappingService;

  @org.springframework.beans.factory.annotation.Autowired
  private ApprovalCaseService approvalCaseService;

  public record Reservation(ExternalStartFact fact, boolean claimed) {
  }

  public record RecoveryClaim(ExternalStartFact fact, String claimToken) {
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Reservation> reserve(
      String scope,
      WorkflowEngineMapping mapping,
      String businessRecordType,
      String businessRecordId,
      String idempotencyKey,
      String requestFingerprint,
      String integrationMode,
      String trackingId,
      String actorUserId,
      String callbackFlow,
      String requestJson) {
    return reserve(scope, mapping, businessRecordType, businessRecordId, idempotencyKey,
        requestFingerprint, integrationMode, trackingId, actorUserId, callbackFlow, requestJson,
        null, null);
  }

  /** 带公共审批案件上下文的启动事实保留；旧调用方通过上面的兼容重载继续工作。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Reservation> reserve(
      String scope,
      WorkflowEngineMapping mapping,
      String businessRecordType,
      String businessRecordId,
      String idempotencyKey,
      String requestFingerprint,
      String integrationMode,
      String trackingId,
      String actorUserId,
      String callbackFlow,
      String requestJson,
      String approvalCaseId,
      Integer submissionRound) {
    ExternalStartFact fact = new ExternalStartFact();
    fact.setId(UUID.randomUUID().toString());
    fact.setScope(scope);
    fact.setFlowKey(mapping.getFlowKey());
    fact.setBusinessRecordType(businessRecordType);
    fact.setBusinessRecordId(businessRecordId);
    fact.setIdempotencyKey(blankToNull(idempotencyKey));
    fact.setBusinessStartRouteKey(businessStartRouteKey(
        scope, mapping.getFlowKey(), businessRecordType, businessRecordId, idempotencyKey, fact.getId()));
    fact.setIdempotencyStartRouteKey(idempotencyStartRouteKey(scope, idempotencyKey, fact.getId()));
    fact.setRequestFingerprint(requestFingerprint);
    fact.setMappingId(mapping.getId());
    fact.setMappingVersion(mapping.getVersion());
    fact.setAdapterId(mapping.getAdapterId());
    fact.setConnectionId(mapping.getConnectionId());
    fact.setIntegrationMode(integrationMode);
    fact.setApprovalCaseId(approvalCaseId);
    fact.setSubmissionRound(submissionRound);
    fact.setTrackingId(trackingId);
    fact.setActorUserId(actorUserId);
    fact.setCallbackFlow(callbackFlow);
    fact.setRequestJson(requestJson);
    fact.setStatus(ExternalStartFact.STATUS_STARTING);
    fact.setRemoteCallPhase(ExternalStartFact.PHASE_NOT_STARTED);
    fact.setClaimToken(UUID.randomUUID().toString());
    LocalDateTime now = LocalDateTime.now();
    fact.setClaimUntil(now.plusSeconds(DEFAULT_LEASE_SECONDS));
    fact.setCreatedAt(now);
    fact.setUpdatedAt(now);
    Try<Void> lock = acquireMappingReferenceLock(mapping.getId());
    if (lock.isFailure()) {
      return Try.failure(lock.getCause());
    }
    try {
      // Saga responsibility commits independently of the calling business transaction.
      var result = DataModelExecutionContext.withPlatformWrite(() -> getDataModel().insert(fact));
      if (result.isSuccess()) {
        var created = result.get();
        DataModelExecutionContext.withPlatformWrite(() -> { reserveCase(created); return null; });
        return Try.success(new Reservation(created, true));
      }
      Throwable cause = result.getCause();
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
        return existingReservation(fact);
      }
      return Try.failure(OuroborosExceptionMapper.toOuroborosException(
          cause, ex -> new BusinessException("创建外部启动事实失败: " + ex.getMessage(), ex)));
    } catch (RuntimeException ex) {
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
        return existingReservation(fact);
      }
      if (ex instanceof OuroborosException ouroborosException) {
        return Try.failure(OuroborosExceptionMapper.toOuroborosException(
            ouroborosException,
            cause -> new BusinessException("创建外部启动事实失败: " + cause.getMessage(), cause)));
      }
      return Try.failure(new BusinessException("创建外部启动事实失败: " + ex.getMessage(), ex));
    }
  }

  private void reserveCase(ExternalStartFact fact) {
    if (approvalCaseService == null || fact.getBusinessRecordType() == null && fact.getBusinessRecordId() == null) { return; }
    if (fact.getBusinessRecordType() == null || fact.getBusinessRecordId() == null) {
      throw new BusinessException("外部发起缺少完整业务记录上下文");
    }
    if (ExternalFactTransaction.resource(ExternalStartFact.class) != ExternalFactTransaction.resource(ApprovalCase.class)) {
      throw new BusinessException("外部启动意图与审批案件不属于同一提交资源");
    }
    ApprovalCaseService.Submission submission;
    if (fact.getApprovalCaseId() != null || fact.getSubmissionRound() != null) {
      var current = approvalCaseService.lockByBusinessRecord(
          fact.getBusinessRecordType(), fact.getBusinessRecordId(), fact.getFlowKey()).orElseThrow(
              () -> new BusinessException("外部发起提供的案件尚未正式提交"));
      if (!java.util.Objects.equals(current.getApprovalCaseId(), fact.getApprovalCaseId())
          || !java.util.Objects.equals(current.getSubmissionRound(), fact.getSubmissionRound())
          || current.getActiveExecutionId() != null || !"PENDING".equals(current.getBusinessStatus())) {
        throw new BusinessException("外部发起提供的审批案件轮次不适用");
      }
      submission = new ApprovalCaseService.Submission(current.getApprovalCaseId(), current.getSubmissionRound());
    } else {
      submission = approvalCaseService.reserveSubmission(fact.getBusinessRecordType(), fact.getBusinessRecordId(), fact.getFlowKey());
    }
    if (!attachApprovalCase(fact.getId(), fact.getClaimToken(), submission.approvalCaseId(), submission.submissionRound())) {
      throw new BusinessException("外部启动意图无法关联审批案件");
    }
    fact.setApprovalCaseId(submission.approvalCaseId()); fact.setSubmissionRound(submission.submissionRound());
  }

  private Try<Void> acquireMappingReferenceLock(String mappingId) {
    if (mappingService == null || mappingId == null || mappingId.isBlank()) {
      return Try.success(null);
    }
    try {
      return mappingService.acquireBindingReferenceLock(mappingId)
          ? Try.success(null)
          : Try.failure(new BusinessException(
              "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                  + "] 映射不存在或正在被清理，拒绝创建外部启动事实: " + mappingId));
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private Try<Reservation> existingReservation(ExternalStartFact requested) {
    Optional<ExternalStartFact> existing = findExisting(requested);
    if (existing.isEmpty()) {
      return Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 外部启动事实唯一键冲突但回查失败"));
    }
    ExternalStartFact fact = existing.get();
    if (!requested.getRequestFingerprint().equals(fact.getRequestFingerprint())) {
      return Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
          + "] 外部启动幂等键或业务并发键已被不同请求占用: factId=" + fact.getId()));
    }
    return Try.success(new Reservation(fact, false));
  }

  private Optional<ExternalStartFact> findExisting(ExternalStartFact requested) {
    if (requested.getIdempotencyKey() != null) {
      Optional<ExternalStartFact> byIdempotency = findByIdempotencyStartRouteKey(requested.getIdempotencyStartRouteKey());
      if (byIdempotency.isPresent()) {
        return byIdempotency;
      }
    }
    return findByBusinessStartRouteKey(requested.getBusinessStartRouteKey());
  }

  public Optional<ExternalStartFact> findById(String id) {
    try {
      return Optional.ofNullable(getByPrimaryKey(id, Map.of()));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部启动事实失败: " + id, ex);
    }
  }

  public Optional<ExternalStartFact> findByTrackingId(String scope, String trackingId) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "scope", Map.of("=", scope), "trackingId", Map.of("=", trackingId)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("按执行引用查询外部启动事实失败: " + trackingId, ex);
    }
  }

  public Optional<ExternalStartFact> findByBusinessStartRouteKey(String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "businessStartRouteKey", Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("按业务键查询外部启动事实失败", ex);
    }
  }

  public Optional<ExternalStartFact> findByIdempotencyStartRouteKey(String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "idempotencyStartRouteKey", Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("按幂等键查询外部启动事实失败", ex);
    }
  }

  /** 统计仍需要依赖历史 mapping 进行恢复的启动事实。 */
  public Long countNonTerminalByMappingId(String mappingId) {
    if (mappingId == null || mappingId.isBlank()) {
      return 0L;
    }
    try {
      long starting = count(Map.of(
          "mappingId", Map.of("=", mappingId),
          "status", Map.of("=", ExternalStartFact.STATUS_STARTING)));
      long unknown = count(Map.of(
          "mappingId", Map.of("=", mappingId),
          "status", Map.of("=", ExternalStartFact.STATUS_START_UNKNOWN)));
      return starting + unknown;
    } catch (OuroborosException ex) {
      throw new BusinessException("按映射统计未完成外部启动事实失败: " + mappingId, ex);
    }
  }

  /** 原子领取过期 STARTING 或 START_UNKNOWN 事实，所有恢复写回均由新 token fencing。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<RecoveryClaim> claimForRecovery(String factId, int leaseSeconds) {
    Optional<ExternalStartFact> factOpt = findById(factId);
    if (factOpt.isEmpty()) {
      return Optional.empty();
    }
    ExternalStartFact fact = factOpt.get();
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    if (ExternalStartFact.STATUS_START_UNKNOWN.equals(fact.getStatus())) {
      where.put("status", Map.of("=", ExternalStartFact.STATUS_START_UNKNOWN));
    } else if (ExternalStartFact.STATUS_STARTING.equals(fact.getStatus())
        && fact.getClaimUntil() != null
        && !fact.getClaimUntil().isAfter(now)) {
      where.put("status", Map.of("=", ExternalStartFact.STATUS_STARTING));
      where.put("claimUntil", Map.of("<", now.plusNanos(1)));
    } else {
      return Optional.empty();
    }
    String claimToken = UUID.randomUUID().toString();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalStartFact.STATUS_STARTING);
    data.put("claimToken", claimToken);
    data.put("claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)));
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "领取外部启动恢复事实失败: " + factId)) {
      return Optional.empty();
    }
    return findById(factId).map(claimed -> new RecoveryClaim(claimed, claimToken));
  }

  public List<RecoveryClaim> claimRecoverable(int limit, int leaseSeconds) {
    int size = Math.min(Math.max(limit, 0), MAX_RECOVERY_SCAN_LIMIT);
    if (size == 0) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<ExternalStartFact> candidates = new ArrayList<>();
    try {
      candidates.addAll((List<ExternalStartFact>) searchList(candidateStatement(
          Map.of("status", Map.of("=", ExternalStartFact.STATUS_START_UNKNOWN)),
          orderBy("createdAt", "id"), size)));
      candidates.addAll((List<ExternalStartFact>) searchList(candidateStatement(
          Map.of(
              "status", Map.of("=", ExternalStartFact.STATUS_STARTING),
              "claimUntil", Map.of("<=", now)),
          orderBy("claimUntil", "createdAt", "id"), size)));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询可恢复外部启动事实失败", ex);
    }
    List<RecoveryClaim> claimed = new ArrayList<>();
    for (ExternalStartFact candidate : candidates) {
      if (claimed.size() >= size) {
        break;
      }
      claimForRecovery(candidate.getId(), leaseSeconds).ifPresent(claimed::add);
    }
    return claimed;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markInFlight(String factId, String claimToken) {
    return updateClaimed(factId, claimToken, Map.of(
        "remoteCallPhase", ExternalStartFact.PHASE_IN_FLIGHT,
        "updatedAt", LocalDateTime.now()));
  }

  /** 将已确认远程未找到的 UNKNOWN 事实恢复为可安全重发的 NOT_STARTED 阶段。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markNotStartedForRecovery(String factId, String claimToken) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("status", Map.of("=", ExternalStartFact.STATUS_STARTING));
    where.put("claimToken", Map.of("=", claimToken));
    where.put("remoteCallPhase", Map.of("=", ExternalStartFact.PHASE_IN_FLIGHT));
    return conditionalUpdate(where, Map.of(
        "remoteCallPhase", ExternalStartFact.PHASE_NOT_STARTED,
        "updatedAt", LocalDateTime.now()), "恢复外部启动事实重试阶段失败: " + factId);
  }

  /** 将新建启动事实与公共审批案件轮次关联；关联本身也受启动事实 claim fencing 保护。 */
  @Transactional
  public boolean attachApprovalCase(
      String factId, String claimToken, String approvalCaseId, int submissionRound) {
    if (approvalCaseId == null || approvalCaseId.isBlank() || submissionRound < 1) {
      throw new BusinessException("审批案件上下文非法: factId=" + factId);
    }
    return updateClaimed(factId, claimToken, Map.of(
        "approvalCaseId", approvalCaseId,
        "submissionRound", submissionRound,
        "updatedAt", LocalDateTime.now()));
  }

  /** 当前 worker 持有 claim 且 lease 尚未过期时续租；旧 worker 或已接管 worker 不能续租。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean renewClaim(String factId, String claimToken, int leaseSeconds) {
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("status", Map.of("=", ExternalStartFact.STATUS_STARTING));
    where.put("claimToken", Map.of("=", claimToken));
    where.put("claimUntil", Map.of(">=", now));
    return conditionalUpdate(where, Map.of(
        "claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)),
        "updatedAt", now), "续租外部启动事实失败: " + factId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markReplied(String factId, String claimToken, String externalInstanceId, String externalUrl) {
    LocalDateTime repliedAt = LocalDateTime.now();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("remoteCallPhase", externalInstanceId == null && externalUrl == null
        ? ExternalStartFact.PHASE_IN_FLIGHT : ExternalStartFact.PHASE_REPLIED);
    if (externalInstanceId != null) {
      data.put("externalInstanceId", externalInstanceId);
    }
    if (externalUrl != null) {
      data.put("externalUrl", externalUrl);
    }
    data.put("repliedAt", repliedAt);
    data.put("updatedAt", repliedAt);
    Map<String, Object> where = claimedWhere(factId, claimToken);
    where.put("repliedAt", null);
    return conditionalUpdate(where, data, "记录外部启动回复失败: " + factId);
  }

  /**
   * 接纳已经越过旧 lease 返回的远程成功。只有请求指纹相同且当前事实尚未记录外部实例时才允许，
   * 并立即发放新的 claim token，防止迟到 worker 继续使用旧 token 写入。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<RecoveryClaim> adoptLateResponse(
      String factId,
      String requestFingerprint,
      String externalInstanceId,
      String externalUrl,
      int leaseSeconds) {
    if (factId == null || factId.isBlank()
        || requestFingerprint == null || requestFingerprint.isBlank()
        || (externalInstanceId == null || externalInstanceId.isBlank())
            && (externalUrl == null || externalUrl.isBlank())) {
      return Optional.empty();
    }
    Optional<ExternalStartFact> currentOpt = findById(factId);
    if (currentOpt.isEmpty()) {
      return Optional.empty();
    }
    ExternalStartFact current = currentOpt.get();
    if (!requestFingerprint.equals(current.getRequestFingerprint())
        || (current.getExternalInstanceId() != null && !current.getExternalInstanceId().isBlank())
        || (!ExternalStartFact.STATUS_STARTING.equals(current.getStatus())
            && !ExternalStartFact.STATUS_START_UNKNOWN.equals(current.getStatus()))) {
      return Optional.empty();
    }
    String claimToken = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("requestFingerprint", Map.of("=", requestFingerprint));
    where.put("externalInstanceId", null);
    where.put("status", Map.of("=", current.getStatus()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalStartFact.STATUS_STARTING);
    data.put("remoteCallPhase", ExternalStartFact.PHASE_REPLIED);
    data.put("repliedAt", now);
    data.put("externalInstanceId", externalInstanceId);
    data.put("externalUrl", externalUrl);
    data.put("claimToken", claimToken);
    data.put("claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)));
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "接纳迟到外部启动响应失败: " + factId)) {
      return Optional.empty();
    }
    return findById(factId).map(adopted -> new RecoveryClaim(adopted, claimToken));
  }

  @Transactional
  public boolean markStarted(String factId, String claimToken, String trackingId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalStartFact.STATUS_STARTED);
    data.put("trackingId", trackingId);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("lastError", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(factId, claimToken, data);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markUnknown(
      String factId,
      String claimToken,
      String externalInstanceId,
      String externalUrl,
      String error) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalStartFact.STATUS_START_UNKNOWN);
    data.put("remoteCallPhase", externalInstanceId == null && externalUrl == null
        ? ExternalStartFact.PHASE_IN_FLIGHT : ExternalStartFact.PHASE_REPLIED);
    if (externalInstanceId != null) {
      data.put("externalInstanceId", externalInstanceId);
    }
    if (externalUrl != null) {
      data.put("externalUrl", externalUrl);
    }
    data.put("lastError", error);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(factId, claimToken, data);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailed(String factId, String claimToken, String error) {
    ExternalStartFact fact = findById(factId).orElse(null);
    if (fact == null) { return false; }
    if (!ExternalStartFact.STATUS_STARTING.equals(fact.getStatus())
        || !java.util.Objects.equals(fact.getClaimToken(), claimToken)) { return false; }
    if (!ExternalStartFact.PHASE_NOT_STARTED.equals(fact.getRemoteCallPhase())
        && !ExternalStartFact.PHASE_IN_FLIGHT.equals(fact.getRemoteCallPhase())) {
      throw new BusinessException("已经收到远端成功回复的启动意图不能按未创建执行回退");
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalStartFact.STATUS_FAILED);
    data.put("businessStartRouteKey", terminalBusinessRouteKey(factId));
    data.put("lastError", error);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    Map<String, Object> where = claimedWhere(factId, claimToken);
    where.put("remoteCallPhase", fact.getRemoteCallPhase());
    if (!conditionalUpdate(where, data, "记录外部启动失败: " + factId)) { return false; }
    DataModelExecutionContext.withPlatformWrite(() -> {
      if (approvalCaseService != null && fact.getApprovalCaseId() != null && fact.getSubmissionRound() != null) {
        if (ExternalFactTransaction.resource(ExternalStartFact.class) != ExternalFactTransaction.resource(ApprovalCase.class)) {
          throw new BusinessException("外部启动失败与案件回退不属于同一提交资源");
        }
        approvalCaseService.markStartFailed(fact.getApprovalCaseId(), fact.getSubmissionRound(), error);
      }
      return null;
    });
    return true;
  }

  /** 流程绑定进入终态时释放当前业务占用；启动事实本身继续保留为历史审计事实。 */
  @Transactional
  public boolean releaseBusinessReservation(
      String scope,
      String flowKey,
      String businessRecordType,
      String businessRecordId,
      String trackingId) {
    if (businessRecordType == null || businessRecordType.isBlank()
        || businessRecordId == null || businessRecordId.isBlank()) {
      return false;
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("businessStartRouteKey", Map.of(
        "=", businessStartRouteKey(scope, flowKey, businessRecordType, businessRecordId)));
    where.put("status", Map.of("=", ExternalStartFact.STATUS_STARTED));
    where.put("trackingId", Map.of("=", trackingId));
    return conditionalUpdate(where, Map.of(
        "businessStartRouteKey", terminalBusinessRouteKey(trackingId),
        "updatedAt", LocalDateTime.now()), "释放外部启动业务占用失败: " + trackingId);
  }

  private boolean updateClaimed(String factId, String claimToken, Map<String, Object> data) {
    Map<String, Object> where = claimedWhere(factId, claimToken);
    try {
      var result = DataModelExecutionContext.withPlatformWrite(() -> getDataModel().update(where, data));
      if (result.isFailure()) {
        throw new BusinessException("更新外部启动事实失败: " + factId, result.getCause());
      }
      Long rows = result.get();
      return rows != null && rows > 0;
    } catch (OuroborosException ex) {
      throw new BusinessException("更新外部启动事实失败: " + factId, ex);
    }
  }

  private Map<String, Object> claimedWhere(String factId, String claimToken) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("status", Map.of("=", ExternalStartFact.STATUS_STARTING));
    where.put("claimToken", Map.of("=", claimToken));
    return where;
  }

  private boolean conditionalUpdate(Map<String, Object> where, Map<String, Object> data, String message) {
    try {
      var result = DataModelExecutionContext.withPlatformWrite(() -> getDataModel().update(where, data));
      if (result.isFailure()) {
        throw new BusinessException(message, result.getCause());
      }
      Long rows = result.get();
      return rows != null && rows == 1;
    } catch (OuroborosException ex) {
      throw new BusinessException(message, ex);
    }
  }

  private Map<String, String> orderBy(String... fields) {
    Map<String, String> order = new LinkedHashMap<>();
    for (String field : fields) {
      order.put(field, "asc");
    }
    return order;
  }

  private Map<String, Object> candidateStatement(
      Map<String, Object> where, Map<String, String> order, int limit) {
    Map<String, Object> statement = new LinkedHashMap<>();
    statement.put("WHERE", where);
    statement.put("ORDER", order);
    statement.put("LIMIT", limit);
    return statement;
  }

  public String businessStartRouteKey(String scope, String flowKey, String businessRecordType, String businessRecordId) {
    return ExternalRouteKeys.of("start-business", scope, flowKey, businessRecordType, businessRecordId);
  }

  private String businessStartRouteKey(
      String scope,
      String flowKey,
      String businessRecordType,
      String businessRecordId,
      String idempotencyKey,
      String fallbackId) {
    if (businessRecordType == null || businessRecordType.isBlank()
        || businessRecordId == null || businessRecordId.isBlank()) {
      return idempotencyKey == null || idempotencyKey.isBlank()
          ? ExternalRouteKeys.of("start-business-absent", fallbackId)
          : ExternalRouteKeys.of("start-business-idempotency", scope, flowKey, idempotencyKey);
    }
    return ExternalRouteKeys.of("start-business", scope, flowKey, businessRecordType, businessRecordId);
  }

  public String idempotencyStartRouteKey(String scope, String idempotencyKey, String fallbackId) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return ExternalRouteKeys.of("start-no-idempotency", fallbackId);
    }
    return ExternalRouteKeys.of("start-idempotency", scope, idempotencyKey);
  }

  private String terminalBusinessRouteKey(String factId) {
    return ExternalRouteKeys.of("start-terminal-business", factId);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
