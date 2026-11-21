package com.ouroboros.workflow.approval.external.runtime.start;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.FlowInstanceVariables;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalEffectCertainty;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.ExternalLeaseHeartbeat;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalFlowEventFactory;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

import io.vavr.control.Try;

/**
 * 结果型接入启动实现。
 *
 * <p>顺序：接收统一路由选定的 ACTIVE 映射 → 按映射解析业务记录 → 原子 reserve 启动事实 →
 * 外部启动（事务外）→ 绑定落库 + STARTED 统一结果事件（同一事务）→ 按 claim token 完成启动事实。
 */
@Service
public class ResultOnlyStartExecutor {

  private final ExternalResultBindingService bindingService;
  private final ExternalStartFactService startFactService;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final InstanceEventDispatcher eventDispatcher;
  private final TransactionTemplate transactionTemplate;
  private final ApprovalCaseService approvalCaseService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ResultOnlyStartExecutor(
      ExternalResultBindingService bindingService,
      ExternalStartFactService startFactService,
      ConnectionAwareAdapterResolver adapterResolver,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager) {
    this(bindingService, startFactService, adapterResolver, eventDispatcher, transactionManager, null);
  }

  @Autowired
  public ResultOnlyStartExecutor(
      ExternalResultBindingService bindingService,
      ExternalStartFactService startFactService,
      ConnectionAwareAdapterResolver adapterResolver,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager,
      org.springframework.beans.factory.ObjectProvider<ApprovalCaseService> approvalCaseProvider) {
    this.bindingService = bindingService;
    this.startFactService = startFactService;
    this.adapterResolver = adapterResolver;
    this.eventDispatcher = eventDispatcher;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    this.approvalCaseService = approvalCaseProvider == null
        ? null : approvalCaseProvider.stream().findFirst().orElse(null);
  }

  /** 统一路由传入已解析的正式映射，避免在发起期间重新选择映射版本。 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Try<ResultOnlyStartResult> startMapped(
      String scope, WorkflowEngineMapping mapping, ResultOnlyStartCommand command) {
    if (!WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      return Try.failure(new BusinessException("结果型执行器只能执行外部权威映射"));
    }

    ExternalWorkflowStarter starter;
    try {
      starter = adapterResolver.resolvePort(mapping, ExternalWorkflowStarter.class);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }

    // 按映射 resultBindingJson 解析业务记录（businessRecordIdPath 从表单提取）
    Try<ResolvedBusinessRecord> resolvedTry = resolveBusinessRecord(mapping, command);
    if (resolvedTry.isFailure()) {
      return Try.failure(resolvedTry.getCause());
    }
    ResolvedBusinessRecord resolved = resolvedTry.get();
    String requestFingerprint = requestFingerprint(command, mapping, resolved, scope);
    ExternalStartCommand externalCommand = new ExternalStartCommand(
        command.userId(),
        new ExternalProcessDefinitionRef(
            mapping.getExternalDefinitionNamespace(),
            mapping.getExternalDefinitionId(),
            mapping.getExternalDefinitionVersion(),
            mapping.getExternalDefinitionSchemaHash()),
        resolved.recordType() == null ? null
            : new ExternalStartCommand.ExternalBusinessRecord(resolved.recordType(), resolved.recordId()),
        command.formData(),
        FlowInstanceVariables.customVariables(command.instanceVariables()),
        requestFingerprint,
        command.cc(),
        command.approvers(),
        command.targetSelectActioners());
    String trackingId = UUID.randomUUID().toString();
    String approvalCaseId = stringVariable(command, FlowInstanceVariables.APPROVAL_CASE_ID);
    Integer submissionRound = integerVariable(command, FlowInstanceVariables.SUBMISSION_ROUND);
    Try<ExternalStartFactService.Reservation> reservationTry = approvalCaseId == null && submissionRound == null
        ? startFactService.reserve(
            scope, mapping, resolved.recordType(), resolved.recordId(), command.idempotencyKey(),
            requestFingerprint, ExternalStartFact.MODE_RESULT_ONLY, trackingId, command.userId(), callbackFlow(command),
            requestJson(externalCommand))
        : startFactService.reserve(
            scope, mapping, resolved.recordType(), resolved.recordId(), command.idempotencyKey(),
            requestFingerprint, ExternalStartFact.MODE_RESULT_ONLY, trackingId, command.userId(), callbackFlow(command),
            requestJson(externalCommand), approvalCaseId, submissionRound);
    if (reservationTry.isFailure()) {
      return Try.failure(reservationTry.getCause());
    }
    ExternalStartFact fact = reservationTry.get().fact();
    if (!reservationTry.get().claimed()) {
      return Try.success(resultFromFact(fact));
    }
    if (!startFactService.markInFlight(fact.getId(), fact.getClaimToken())) {
      return Try.success(resultFromFact(startFactService.findById(fact.getId()).orElse(fact)));
    }

    // 外部 HTTP 调用在事务外执行；调用期间续租，避免正常慢请求被 recovery 抢占。
    Try<ExternalStartResult> startTry;
    try (ExternalLeaseHeartbeat ignored = ExternalLeaseHeartbeat.start(
        () -> startFactService.renewClaim(
            fact.getId(), fact.getClaimToken(), ExternalStartFactService.DEFAULT_LEASE_SECONDS),
        ExternalStartFactService.DEFAULT_LEASE_SECONDS)) {
      startTry = invokeStarter(starter, externalCommand);
    }
    if (startTry.isFailure()) {
      Throwable cause = startTry.getCause();
      if (isUncertainStartFailure(cause)) {
        startFactService.markUnknown(fact.getId(), fact.getClaimToken(), null, null, stableError(cause));
        return Try.success(resultFromFact(startFactService.findById(fact.getId()).orElse(fact)));
      }
      startFactService.markFailed(fact.getId(), fact.getClaimToken(), stableError(cause));
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部启动失败: "
              + cause.getMessage(), cause));
    }
    ExternalStartResult startResult = startTry.get();
    if (!startFactService.markReplied(
        fact.getId(), fact.getClaimToken(), startResult.externalInstanceId(), startResult.externalUrl())) {
      Optional<ExternalStartFactService.RecoveryClaim> adopted = startFactService.adoptLateResponse(
          fact.getId(), fact.getRequestFingerprint(), startResult.externalInstanceId(),
          startResult.externalUrl(), ExternalStartFactService.DEFAULT_LEASE_SECONDS);
      if (adopted.isPresent()) {
        ExternalStartFact adoptedFact = adopted.get().fact();
        Try<ResultOnlyStartResult> persisted = persistStartTx(
            command, mapping, startResult, scope, resolved.recordType(), resolved.recordId(), adoptedFact);
        if (persisted.isSuccess()) {
          return persisted;
        }
        startFactService.markUnknown(
            adoptedFact.getId(), adopted.get().claimToken(), startResult.externalInstanceId(),
            startResult.externalUrl(), stableError(persisted.getCause()));
      }
      return Try.success(resultFromFact(startFactService.findById(fact.getId()).orElse(fact)));
    }

    Try<ResultOnlyStartResult> persisted = persistStartTx(
        command, mapping, startResult, scope, resolved.recordType(), resolved.recordId(),
        fact);
    if (persisted.isFailure()) {
      startFactService.markUnknown(
          fact.getId(), fact.getClaimToken(), startResult.externalInstanceId(), startResult.externalUrl(),
          stableError(persisted.getCause()));
      return Try.success(resultFromFact(startFactService.findById(fact.getId()).orElse(fact)));
    }
    return Try.success(new ResultOnlyStartResult(
        persisted.get().trackingId(), persisted.get().externalInstanceId(), persisted.get().externalUrl(),
        ExternalStartFact.STATUS_STARTED, fact.getId()));
  }

  /**
   * 绑定落库与 STARTED 统一结果事件。
   *
   * <p>事务边界由 {@link #persistStartTx} 统一管理（外部 HTTP 调用在事务外执行）；
   * 直接调用本方法时不提供事务保证，仅供内部与测试使用。
   */
  public Try<ResultOnlyStartResult> persistStart(
      ResultOnlyStartCommand command,
      WorkflowEngineMapping mapping,
      ExternalStartResult startResult,
      String scope,
      String recordType,
      String recordId,
      String trackingId,
      String startFactId) {
    return persistStart(command, mapping, startResult, scope, recordType, recordId,
        trackingId, startFactId, null, null);
  }

  private Try<ResultOnlyStartResult> persistStart(
      ResultOnlyStartCommand command,
      WorkflowEngineMapping mapping,
      ExternalStartResult startResult,
      String scope,
      String recordType,
      String recordId,
      String trackingId,
      String startFactId,
      String approvalCaseId,
      Integer submissionRound) {
    Optional<ExternalResultBinding> existing = bindingService.findByExternalInstance(
        mapping.getAdapterId(), mapping.getConnectionId(), startResult.externalInstanceId());
    if (existing.isPresent()) {
      ExternalResultBinding binding = existing.get();
      if (!Objects.equals(trackingId, binding.getTrackingId()) || !sameBindingIdentity(
          binding, mapping, command, scope, recordType, recordId, startResult,
          approvalCaseId, submissionRound)) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                + "] 外部实例已绑定到其他本地业务事实: externalInstanceId="
                + startResult.externalInstanceId()));
      }
      bindApprovalCaseExecution(
          binding.getApprovalCaseId(), binding.getSubmissionRound(), binding.getTrackingId());
      return Try.success(new ResultOnlyStartResult(
          binding.getTrackingId(), binding.getExternalInstanceId(), binding.getExternalUrl(),
          ExternalStartFact.STATUS_STARTED, startFactId));
    }
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(trackingId);
    binding.setScope(scope);
    binding.setFlowKey(command.flowKey());
    binding.setMappingId(mapping.getId());
    binding.setMappingVersion(mapping.getVersion());
    binding.setAdapterId(mapping.getAdapterId());
    binding.setConnectionId(mapping.getConnectionId());
    binding.setBusinessRecordType(recordType);
    binding.setBusinessRecordId(recordId);
    binding.setExternalInstanceId(startResult.externalInstanceId());
    binding.setIdempotencyKey(command.idempotencyKey());
    binding.setExternalDefinitionId(mapping.getExternalDefinitionId());
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    binding.setExternalUrl(startResult.externalUrl());
    binding.setApprovalCaseId(approvalCaseId);
    binding.setSubmissionRound(submissionRound);
    Try<ExternalResultBinding> createTry = bindingService.create(binding);
    if (createTry.isFailure()) {
      return Try.failure(createTry.getCause());
    }
    ExternalResultBinding created = createTry.get();
    if (!Objects.equals(trackingId, created.getTrackingId())) {
      return Try.failure(new BusinessException("外部结果绑定不能改变启动意图的 trackingId"));
    }
    bindApprovalCaseExecution(created.getApprovalCaseId(), created.getSubmissionRound(), created.getTrackingId());

    var startedEvent = ExternalFlowEventFactory.started(startFactService.findById(startFactId)
        .orElseThrow(() -> new BusinessException("外部启动意图不存在")));
    eventDispatcher.enqueueEvent(startedEvent);

    return Try.success(new ResultOnlyStartResult(
        created.getTrackingId(), created.getExternalInstanceId(), created.getExternalUrl(),
        ExternalStartFact.STATUS_STARTED, startFactId));
  }

  private void bindApprovalCaseExecution(String approvalCaseId, Integer submissionRound, String executionId) {
    if (approvalCaseService == null || approvalCaseId == null || submissionRound == null) {
      return;
    }
    approvalCaseService.bindExecution(approvalCaseId, submissionRound, executionId);
  }

  private boolean sameBindingIdentity(
      ExternalResultBinding existing,
      WorkflowEngineMapping mapping,
      ResultOnlyStartCommand command,
      String scope,
      String recordType,
      String recordId,
      ExternalStartResult startResult,
      String approvalCaseId,
      Integer submissionRound) {
    return Objects.equals(existing.getScope(), scope)
        && Objects.equals(existing.getFlowKey(), command.flowKey())
        && Objects.equals(existing.getMappingId(), mapping.getId())
        && Objects.equals(existing.getMappingVersion(), mapping.getVersion())
        && Objects.equals(existing.getAdapterId(), mapping.getAdapterId())
        && Objects.equals(existing.getConnectionId(), mapping.getConnectionId())
        && Objects.equals(existing.getBusinessRecordType(), recordType)
        && Objects.equals(existing.getBusinessRecordId(), recordId)
        && Objects.equals(existing.getExternalInstanceId(), startResult.externalInstanceId())
        && Objects.equals(existing.getIdempotencyKey(), command.idempotencyKey())
        && Objects.equals(existing.getExternalDefinitionId(), mapping.getExternalDefinitionId())
        && Objects.equals(existing.getApprovalCaseId(), approvalCaseId)
        && Objects.equals(existing.getSubmissionRound(), submissionRound);
  }

  /**
   * 事务化持久化入口：绑定落库与 STARTED 事件在同一事务；事务缺失时（单测直连）退化为直接执行。
   */
  private Try<ResultOnlyStartResult> persistStartTx(
      ResultOnlyStartCommand command,
      WorkflowEngineMapping mapping,
      ExternalStartResult startResult,
      String scope,
      String recordType,
      String recordId,
      ExternalStartFact fact) {
    if (transactionTemplate == null) {
      Try<ResultOnlyStartResult> persisted = persistStart(
          command, mapping, startResult, scope, recordType, recordId, fact.getTrackingId(), fact.getId(),
          fact.getApprovalCaseId(), fact.getSubmissionRound());
      if (persisted.isSuccess()
          && !startFactService.markStarted(fact.getId(), fact.getClaimToken(), persisted.get().trackingId())) {
        return Try.failure(new BusinessException(
            "外部启动事实完成失败或 claim 已失效: factId=" + fact.getId()));
      }
      return persisted;
    }
    try {
      ResultOnlyStartResult saved = ExternalFactTransaction.execute(transactionTemplate, ExternalStartFact.class, () -> {
        Try<ResultOnlyStartResult> result =
            persistStart(command, mapping, startResult, scope, recordType, recordId, fact.getTrackingId(), fact.getId(),
                fact.getApprovalCaseId(), fact.getSubmissionRound());
        if (result.isFailure()) {
          throw new IllegalStateException(result.getCause().getMessage(), result.getCause());
        }
        if (!startFactService.markStarted(fact.getId(), fact.getClaimToken(), result.get().trackingId())) {
          throw new BusinessException("外部启动事实完成失败或 claim 已失效: factId=" + fact.getId());
        }
        return result.get();
      });
      return Try.success(saved);
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private ResultOnlyStartResult resultFromFact(ExternalStartFact fact) {
    return new ResultOnlyStartResult(
        fact.getTrackingId(), fact.getExternalInstanceId(), fact.getExternalUrl(), fact.getStatus(), fact.getId());
  }

  private String requestJson(ExternalStartCommand command) {
    try {
      return objectMapper.writeValueAsString(command);
    } catch (Exception ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 外部启动命令序列化失败: " + ex.getMessage(), ex);
    }
  }

  private String requestFingerprint(
      ResultOnlyStartCommand command, WorkflowEngineMapping mapping, ResolvedBusinessRecord resolved, String scope) {
    try {
      Map<String, Object> payload = new java.util.TreeMap<>();
      payload.put("scope", scope);
      payload.put("flowKey", command.flowKey());
      payload.put("mappingId", mapping.getId());
      payload.put("mappingVersion", mapping.getVersion());
      payload.put("adapterId", mapping.getAdapterId());
      payload.put("connectionId", mapping.getConnectionId());
      payload.put("recordType", resolved.recordType());
      payload.put("recordId", resolved.recordId());
      payload.put("idempotencyKey", command.idempotencyKey());
      payload.put("userId", command.userId());
      payload.put("formData", command.formData());
      Map<String, Object> variables = new java.util.TreeMap<>(
          command.instanceVariables() == null ? Map.of() : command.instanceVariables());
      variables.remove(FlowInstanceVariables.BUSINESS_RECORD_TYPE);
      variables.remove(FlowInstanceVariables.BUSINESS_RECORD_ID);
      variables.remove(FlowInstanceVariables.IDEMPOTENCY_KEY);
      if (!variables.isEmpty()) {
        payload.put("instanceVariables", variables);
      }
      payload.put("cc", command.cc());
      payload.put("approvers", command.approvers());
      payload.put("targetSelectActioners", command.targetSelectActioners());
      return ExternalRouteKeys.of("start-fingerprint", objectMapper.writeValueAsString(payload));
    } catch (Exception ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 外部启动请求指纹生成失败: " + ex.getMessage(), ex);
    }
  }

  private boolean isUncertainStartFailure(Throwable cause) {
    return findEffectCertainty(cause) == ExternalEffectCertainty.UNKNOWN;
  }

  private String callbackFlow(ResultOnlyStartCommand command) {
    Object value = command.instanceVariables() == null ? null
        : command.instanceVariables().get(FlowInstanceVariables.CALLBACK_FLOW);
    return value == null ? null : String.valueOf(value);
  }

  private String stringVariable(ResultOnlyStartCommand command, String name) {
    Object value = command.instanceVariables() == null ? null : command.instanceVariables().get(name);
    return value == null ? null : String.valueOf(value);
  }

  private Integer integerVariable(ResultOnlyStartCommand command, String name) {
    String value = stringVariable(command, name);
    return value == null ? null : Integer.valueOf(value);
  }

  /** 适配器不得把网络边界异常泄漏为未收敛的 STARTING 事实。 */
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
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName() + ": " + diagnostic.getClass().getSimpleName();
    }
    return cause.getClass().getSimpleName() + ": " + diagnostic.getClass().getSimpleName() + ": " + message;
  }

  /**
   * 按映射 resultBindingJson 解析业务记录：配置了 businessRecordType/businessRecordIdPath 时
   * 优先使用配置并从表单数据提取业务记录 ID。
   */
  Try<ResolvedBusinessRecord> resolveBusinessRecord(WorkflowEngineMapping mapping, ResultOnlyStartCommand command) {
    String recordType = command.businessRecordType();
    String recordId = command.businessRecordId();
    if (StringUtils.isNotBlank(mapping.getResultBindingJson())) {
      try {
        JsonNode config = objectMapper.readTree(mapping.getResultBindingJson());
        String configType = config.path("businessRecordType").asText(null);
        String idPath = config.path("businessRecordIdPath").asText(null);
        if (StringUtils.isNotBlank(configType)) {
          recordType = configType;
        }
        if (StringUtils.isNotBlank(idPath)) {
          Object extracted = extractByPath(command.formData(), idPath);
          if (extracted == null) {
            return Try.failure(new BusinessException(
                "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
                    + "] 无法按映射配置提取业务记录 ID: " + idPath + "（映射 " + mapping.getId() + "）"));
          }
          recordId = String.valueOf(extracted);
        }
      } catch (BusinessException ex) {
        return Try.failure(ex);
      } catch (Exception ex) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 结果绑定配置解析失败: "
                + mapping.getId() + " - " + ex.getMessage(), ex));
      }
    }
    if (StringUtils.isBlank(recordType) && StringUtils.isBlank(recordId)) {
      return Try.success(new ResolvedBusinessRecord(null, null));
    }
    if (StringUtils.isBlank(recordType) || StringUtils.isBlank(recordId)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 业务记录类型或 ID 缺失: "
              + "recordType=" + recordType + ", recordId=" + recordId));
    }
    return Try.success(new ResolvedBusinessRecord(recordType, recordId));
  }

  /**
   * 按路径表达式从表单数据提取值。支持 {@code "$.a.b"}、{@code "a.b"} 与直接键名。
   */
  private Object extractByPath(Map<String, Object> formData, String path) {
    if (formData == null) {
      return null;
    }
    String normalized = path.trim();
    if (normalized.startsWith("$.")) {
      normalized = normalized.substring(2);
    }
    if (!normalized.contains(".")) {
      return formData.get(normalized);
    }
    Object current = formData;
    for (String segment : normalized.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(segment);
    }
    return current;
  }

  /**
   * 解析后的业务记录定位。
   */
  record ResolvedBusinessRecord(String recordType, String recordId) {
  }
}
