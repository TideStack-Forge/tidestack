package com.ouroboros.workflow.approval.external.runtime.start;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryQuery;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalEffectCertainty;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStartRecovery;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.ExternalLeaseHeartbeat;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalFlowEventFactory;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

import io.vavr.control.Try;

/**
 * 外部启动恢复器：安全重放尚未发出的请求，并在远端已回复时只补本地绑定。
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ExternalStartRecoveryService {

  private final ExternalStartFactService factService;
  private final WorkflowEngineMappingService mappingService;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalResultBindingService resultBindingService;
  private final InstanceEventDispatcher eventDispatcher;
  private final TransactionTemplate transactionTemplate;
  private final ApprovalCaseService approvalCaseService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ExternalStartRecoveryService(
      ExternalStartFactService factService,
      WorkflowEngineMappingService mappingService,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager) {
    this(factService, mappingService, adapterResolver, instanceBindingService, resultBindingService,
        eventDispatcher, transactionManager, null);
  }

  @Autowired
  public ExternalStartRecoveryService(
      ExternalStartFactService factService,
      WorkflowEngineMappingService mappingService,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager,
      org.springframework.beans.factory.ObjectProvider<ApprovalCaseService> approvalCaseProvider) {
    this.factService = factService;
    this.mappingService = mappingService;
    this.adapterResolver = adapterResolver;
    this.instanceBindingService = instanceBindingService;
    this.resultBindingService = resultBindingService;
    this.eventDispatcher = eventDispatcher;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    this.approvalCaseService = approvalCaseProvider == null
        ? null : approvalCaseProvider.stream().findFirst().orElse(null);
  }

  public int recoverDue(int limit, int leaseSeconds) {
    List<ExternalStartFactService.RecoveryClaim> claims = factService.claimRecoverable(limit, leaseSeconds);
    int recovered = 0;
    for (ExternalStartFactService.RecoveryClaim claim : claims) {
      if (recover(claim).isSuccess()) {
        recovered++;
      }
    }
    return recovered;
  }

  public Try<String> recover(ExternalStartFactService.RecoveryClaim claim) {
    ExternalStartFact fact = claim.fact();
    WorkflowEngineMapping mapping = mappingService.findByIdForSystem(fact.getMappingId()).orElse(null);
    if (!matchesPinnedMapping(fact, mapping)) {
      factService.markUnknown(fact.getId(), claim.claimToken(), fact.getExternalInstanceId(), fact.getExternalUrl(),
          "启动事实对应的固定映射不存在或已改变，保留原恢复责任");
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 启动事实对应的固定映射不存在或已改变: factId=" + fact.getId()));
    }
    if (ExternalStartFact.PHASE_REPLIED.equals(fact.getRemoteCallPhase())
        && fact.getExternalInstanceId() != null && !fact.getExternalInstanceId().isBlank()) {
      return persistReplied(fact, mapping, claim.claimToken());
    }
    if (ExternalStartFact.PHASE_NOT_STARTED.equals(fact.getRemoteCallPhase())) {
      return startAndPersist(fact, mapping, claim.claimToken(), ExternalStartFactService.DEFAULT_LEASE_SECONDS);
    }
    return recoverInFlight(fact, mapping, claim.claimToken(), ExternalStartFactService.DEFAULT_LEASE_SECONDS);
  }

  private Try<String> recoverInFlight(
      ExternalStartFact fact, WorkflowEngineMapping mapping, String claimToken, int leaseSeconds) {
    ExternalWorkflowStartRecovery recovery;
    try {
      recovery = adapterResolver.resolvePort(mapping, ExternalWorkflowStartRecovery.class);
    } catch (RuntimeException ex) {
      return markUnknownWithoutRetry(fact, claimToken,
          "适配器未声明可靠的外部启动结果恢复能力；禁止自动再次发起");
    }
    if (recovery == null) {
      return markUnknownWithoutRetry(fact, claimToken,
          "适配器未声明可靠的外部启动结果恢复能力；禁止自动再次发起");
    }

    Try<ExternalStartRecoveryResult> recovered;
    try {
      recovered = recovery.recover(new ExternalStartRecoveryQuery(
          fact.getIdempotencyKey(), fact.getRequestFingerprint(), fact.getScope(), fact.getFlowKey(),
          fact.getBusinessRecordType(), fact.getBusinessRecordId(), fact.getMappingId(),
          fact.getMappingVersion()));
      if (recovered == null) {
        recovered = Try.failure(ExternalOperationFailure.unknown("外部启动恢复适配器返回空结果", null));
      }
    } catch (RuntimeException ex) {
      recovered = Try.failure(ExternalOperationFailure.unknown(
          "外部启动恢复查询抛出异常，远端结果未知", ex));
    }
    if (recovered.isFailure()) {
      return markUnknownWithoutRetry(fact, claimToken, stableError(recovered.getCause()));
    }

    ExternalStartRecoveryResult result = recovered.get();
    if (result == null) {
      return markUnknownWithoutRetry(fact, claimToken, "外部启动恢复适配器返回空结果");
    }
    return switch (result.resolution()) {
      case FOUND -> adoptRecoveredInstance(fact, mapping, claimToken, leaseSeconds, result.startResult());
      case NOT_FOUND -> retryConfirmedAbsent(fact, mapping, claimToken, leaseSeconds, result.diagnostic());
      case UNKNOWN -> markUnknownWithoutRetry(fact, claimToken,
          result.diagnostic() == null || result.diagnostic().isBlank()
              ? "外部启动恢复查询无法确认远端结果" : result.diagnostic());
    };
  }

  private Try<String> adoptRecoveredInstance(
      ExternalStartFact fact,
      WorkflowEngineMapping mapping,
      String claimToken,
      int leaseSeconds,
      ExternalStartResult result) {
    Optional<ExternalStartFactService.RecoveryClaim> adopted = factService.adoptLateResponse(
        fact.getId(), fact.getRequestFingerprint(), result.externalInstanceId(), result.externalUrl(), leaseSeconds);
    if (adopted.isEmpty()) {
      return Try.failure(new BusinessException("外部启动恢复结果无法接纳: factId=" + fact.getId()));
    }
    return persistReplied(adopted.get().fact(), mapping, adopted.get().claimToken());
  }

  private Try<String> retryConfirmedAbsent(
      ExternalStartFact fact,
      WorkflowEngineMapping mapping,
      String claimToken,
      int leaseSeconds,
      String diagnostic) {
    if (!factService.markNotStartedForRecovery(fact.getId(), claimToken)) {
      return Try.failure(new BusinessException("外部启动恢复 claim 已失效: factId=" + fact.getId()));
    }
    return startAndPersist(fact, mapping, claimToken, leaseSeconds);
  }

  private Try<String> markUnknownWithoutRetry(
      ExternalStartFact fact, String claimToken, String diagnostic) {
    factService.markUnknown(
        fact.getId(), claimToken, fact.getExternalInstanceId(), fact.getExternalUrl(), diagnostic);
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
            + "] 远程启动结果未知，已停止自动重放: factId=" + fact.getId()));
  }

  private Try<String> startAndPersist(
      ExternalStartFact fact, WorkflowEngineMapping mapping, String claimToken, int leaseSeconds) {
    ExternalStartCommand command;
    try {
      command = objectMapper.readValue(fact.getRequestJson(), ExternalStartCommand.class);
    } catch (Exception ex) {
      factService.markFailed(fact.getId(), claimToken, "启动命令无法反序列化: " + ex.getMessage());
      return Try.failure(new BusinessException("外部启动命令无法反序列化: factId=" + fact.getId(), ex));
    }
    if (!factService.markInFlight(fact.getId(), claimToken)) {
      return Try.failure(new BusinessException("外部启动恢复 claim 已失效: factId=" + fact.getId()));
    }
    ExternalWorkflowStarter starter;
    try {
      starter = adapterResolver.resolvePort(mapping, ExternalWorkflowStarter.class);
    } catch (RuntimeException ex) {
      factService.markFailed(fact.getId(), claimToken, stableError(ex));
      return Try.failure(ex);
    }
    Try<ExternalStartResult> started;
    try (ExternalLeaseHeartbeat ignored = ExternalLeaseHeartbeat.start(
        () -> factService.renewClaim(fact.getId(), claimToken, leaseSeconds), leaseSeconds)) {
      started = invokeStarter(starter, command);
    }
    if (started.isFailure()) {
      Throwable cause = started.getCause();
      if (isUncertainStartFailure(cause)) {
        factService.markUnknown(fact.getId(), claimToken, null, null, stableError(cause));
      } else {
        factService.markFailed(fact.getId(), claimToken, stableError(cause));
      }
      return Try.failure(cause);
    }
    ExternalStartResult result = started.get();
    String effectiveClaimToken = claimToken;
    ExternalStartFact effectiveFact = fact;
    if (!factService.markReplied(
        fact.getId(), claimToken, result.externalInstanceId(), result.externalUrl())) {
      Optional<ExternalStartFactService.RecoveryClaim> adopted = factService.adoptLateResponse(
          fact.getId(), fact.getRequestFingerprint(), result.externalInstanceId(), result.externalUrl(),
          leaseSeconds);
      if (adopted.isEmpty()) {
        return Try.failure(new BusinessException("外部启动恢复 claim 已失效且迟到响应无法接纳: factId=" + fact.getId()));
      }
      effectiveFact = adopted.get().fact();
      effectiveClaimToken = adopted.get().claimToken();
    } else {
      Optional<ExternalStartFact> replied = factService.findById(fact.getId());
      if (replied.isEmpty()) {
        return Try.failure(new BusinessException("远程启动已回复但启动事实回查失败: factId=" + fact.getId()));
      }
      effectiveFact = replied.get();
    }
    return persistReplied(effectiveFact, mapping, effectiveClaimToken);
  }

  private Try<String> persistReplied(
      ExternalStartFact fact, WorkflowEngineMapping mapping, String claimToken) {
    try {
      String trackingId = transactionTemplate == null
          ? persistLocalState(fact, mapping, claimToken)
          : ExternalFactTransaction.execute(transactionTemplate, ExternalStartFact.class, () -> persistLocalState(fact, mapping, claimToken));
      return Try.success(trackingId);
    } catch (RuntimeException ex) {
      factService.markUnknown(
          fact.getId(), claimToken, fact.getExternalInstanceId(), fact.getExternalUrl(), stableError(ex));
      return Try.failure(ex);
    }
  }

  private String persistLocalState(
      ExternalStartFact fact, WorkflowEngineMapping mapping, String claimToken) {
    if (ExternalStartFact.MODE_RESULT_ONLY.equals(fact.getIntegrationMode())) {
      persistResultBinding(fact, mapping);
    } else if (ExternalStartFact.MODE_FULL_PROCESS.equals(fact.getIntegrationMode())) {
      persistInstanceBinding(fact, mapping);
    } else {
      throw new BusinessException("未知外部启动模式: " + fact.getIntegrationMode());
    }
    if (!factService.markStarted(fact.getId(), claimToken, fact.getTrackingId())) {
      throw new BusinessException("外部启动恢复 claim 已失效: factId=" + fact.getId());
    }
    return fact.getTrackingId();
  }

  private void persistResultBinding(ExternalStartFact fact, WorkflowEngineMapping mapping) {
    Optional<ExternalResultBinding> existing = resultBindingService.findByExternalInstance(
        fact.getAdapterId(), fact.getConnectionId(), fact.getExternalInstanceId());
    if (existing.isPresent()) {
      requireSameTrackingId(fact, existing.get().getTrackingId());
      bindApprovalCaseExecution(fact);
      return;
    }
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(fact.getTrackingId());
    binding.setScope(fact.getScope());
    binding.setFlowKey(fact.getFlowKey());
    binding.setMappingId(fact.getMappingId());
    binding.setMappingVersion(fact.getMappingVersion());
    binding.setAdapterId(fact.getAdapterId());
    binding.setConnectionId(fact.getConnectionId());
    binding.setBusinessRecordType(fact.getBusinessRecordType());
    binding.setBusinessRecordId(fact.getBusinessRecordId());
    binding.setExternalInstanceId(fact.getExternalInstanceId());
    binding.setIdempotencyKey(fact.getIdempotencyKey());
    binding.setExternalDefinitionId(mapping.getExternalDefinitionId());
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    binding.setExternalUrl(fact.getExternalUrl());
    binding.setApprovalCaseId(fact.getApprovalCaseId());
    binding.setSubmissionRound(fact.getSubmissionRound());
    ExternalResultBinding created = resultBindingService.create(binding)
        .getOrElseThrow(cause -> new BusinessException("恢复外部结果绑定失败", cause));
    requireSameTrackingId(fact, created.getTrackingId());
    bindApprovalCaseExecution(fact);
    eventDispatcher.enqueueEvent(ExternalFlowEventFactory.started(fact));
  }

  private void persistInstanceBinding(ExternalStartFact fact, WorkflowEngineMapping mapping) {
    Optional<ExternalInstanceBinding> existing = instanceBindingService.findByExternalInstance(
        fact.getAdapterId(), fact.getConnectionId(), fact.getExternalInstanceId());
    if (existing.isPresent()) {
      requireSameTrackingId(fact, existing.get().getLocalInstanceId());
      bindApprovalCaseExecution(fact);
      return;
    }
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(fact.getTrackingId());
    binding.setFlowKey(fact.getFlowKey());
    binding.setScope(fact.getScope());
    binding.setMappingId(fact.getMappingId());
    binding.setMappingVersion(fact.getMappingVersion());
    binding.setAdapterId(fact.getAdapterId());
    binding.setConnectionId(fact.getConnectionId());
    binding.setExternalInstanceId(fact.getExternalInstanceId());
    binding.setExternalDefinitionId(mapping.getExternalDefinitionId());
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    binding.setExternalUrl(fact.getExternalUrl());
    binding.setApprovalCaseId(fact.getApprovalCaseId());
    binding.setSubmissionRound(fact.getSubmissionRound());
    binding.setBusinessRecordType(fact.getBusinessRecordType());
    binding.setBusinessRecordId(fact.getBusinessRecordId());
    binding.setCallbackFlow(fact.getCallbackFlow());
    ExternalInstanceBinding created = instanceBindingService.create(binding)
        .getOrElseThrow(cause -> new BusinessException("恢复外部实例绑定失败", cause));
    requireSameTrackingId(fact, created.getLocalInstanceId());
    bindApprovalCaseExecution(fact);
    eventDispatcher.enqueueEvent(ExternalFlowEventFactory.started(fact));
  }

  private void bindApprovalCaseExecution(ExternalStartFact fact) {
    if (approvalCaseService == null || fact == null
        || fact.getApprovalCaseId() == null || fact.getSubmissionRound() == null) {
      return;
    }
    approvalCaseService.bindExecution(
        fact.getApprovalCaseId(), fact.getSubmissionRound(), fact.getTrackingId());
  }

  private void requireSameTrackingId(ExternalStartFact fact, String actualTrackingId) {
    if (!fact.getTrackingId().equals(actualTrackingId)) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] 外部实例已绑定到不同本地事实: factId=" + fact.getId());
    }
  }

  private boolean matchesPinnedMapping(ExternalStartFact fact, WorkflowEngineMapping mapping) {
    return mapping != null
        && fact.getMappingVersion().equals(mapping.getVersion())
        && fact.getAdapterId().equals(mapping.getAdapterId())
        && fact.getConnectionId().equals(mapping.getConnectionId());
  }

  private boolean isUncertainStartFailure(Throwable cause) {
    return findEffectCertainty(cause) == ExternalEffectCertainty.UNKNOWN;
  }

  /** 把适配器越界抛出的异常收敛到可恢复的 UNKNOWN 事实。 */
  private Try<ExternalStartResult> invokeStarter(
      ExternalWorkflowStarter starter, ExternalStartCommand command) {
    try {
      Try<ExternalStartResult> result = starter.start(command);
      return result == null
          ? Try.failure(ExternalOperationFailure.unknown("外部启动适配器返回空结果", null))
          : result;
    } catch (RuntimeException ex) {
      return Try.failure(ExternalOperationFailure.unknown(
          "外部启动适配器抛出异常，远程副作用未知", ex));
    }
  }

  private ExternalEffectCertainty findEffectCertainty(Throwable cause) {
    Throwable cursor = cause;
    while (cursor != null) {
      if (cursor instanceof ExternalOperationFailure failure) {
        return failure.certainty();
      }
      cursor = cursor.getCause();
    }
    return ExternalEffectCertainty.DEFINITELY_NOT_APPLIED;
  }

  private String stableError(Throwable cause) {
    Throwable diagnostic = cause instanceof ExternalOperationFailure failure && failure.getCause() != null
        ? failure.getCause() : cause;
    String message = diagnostic.getMessage();
    return message == null || message.isBlank()
        ? cause.getClass().getSimpleName() + ": " + diagnostic.getClass().getSimpleName()
        : cause.getClass().getSimpleName() + ": " + diagnostic.getClass().getSimpleName() + ": " + message;
  }
}
