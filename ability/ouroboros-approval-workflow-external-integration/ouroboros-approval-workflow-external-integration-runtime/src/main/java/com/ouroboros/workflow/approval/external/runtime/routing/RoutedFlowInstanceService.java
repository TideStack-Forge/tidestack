package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowInstanceVariables;
import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowInitialFormResolver;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowStartResult;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.CcTarget;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalRecordPage;
import com.ouroboros.workflow.approval.external.ExternalRecordReader;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalEffectCertainty;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.ExternalLeaseHeartbeat;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.external.runtime.ExternalStatusMapper;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionRequest;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalFlowEventFactory;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartExecutor;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartCommand;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

import io.vavr.control.Try;

/**
 * 流程实例路由门面。
 *
 * <p>统一发起按正式映射路由到内置、完整外部或结果型外部执行器；
 * 无映射按 {@code unmapped-policy}（internal 委托内置 / reject 拒绝）；其余委托内置引擎。
 */
@Service
@Primary
public class RoutedFlowInstanceService implements FlowInstanceService {

  /** 未映射策略：internal（默认，走内置引擎）或 reject（禁止发起）。 */
  public static final String UNMAPPED_POLICY_INTERNAL = "internal";
  public static final String UNMAPPED_POLICY_REJECT = "reject";

  @Autowired
  private FlowDefinitionService definitions;
  @Autowired(required = false)
  private List<FlowFormService> formServices = List.of();

  private final FlowInstanceService internal;
  private final WorkflowEngineMappingService mappingService;
  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalResultBindingService resultBindingService;
  private final InstanceEventDispatcher eventDispatcher;
  private final TransactionTemplate transactionTemplate;
  private final TransactionTemplate externalInvocation;
  private final String unmappedPolicy;
  private final WorkflowScopeResolver scopeResolver;
  private final ExternalUserIdentityResolver identityResolver;
  private final ExternalStartFactService startFactService;
  private final ExternalActionExecutionService actionExecutionService;
  private final ApprovalCaseService approvalCaseService;
  private final ResultOnlyStartExecutor resultStarts;
  private final ExternalBindingRouteResolver bindingRouteResolver;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  public RoutedFlowInstanceService(
      ObjectProvider<BuiltInFlowInstanceService> internalProvider,
      WorkflowEngineMappingService mappingService,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager,
      @Value("${ouroboros.workflow.approval.external.unmapped-policy:internal}") String unmappedPolicy,
      WorkflowScopeResolver scopeResolver,
      ExternalUserIdentityResolver identityResolver,
      ExternalStartFactService startFactService,
      ExternalActionExecutionService actionExecutionService) {
    this(internalProvider, mappingService, registry, adapterResolver, instanceBindingService,
        resultBindingService, eventDispatcher, transactionManager, unmappedPolicy, scopeResolver,
        identityResolver, startFactService, actionExecutionService, null);
  }

  public RoutedFlowInstanceService(
      ObjectProvider<BuiltInFlowInstanceService> internalProvider,
      WorkflowEngineMappingService mappingService,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager,
      @Value("${ouroboros.workflow.approval.external.unmapped-policy:internal}") String unmappedPolicy,
      WorkflowScopeResolver scopeResolver,
      ExternalUserIdentityResolver identityResolver,
      ExternalStartFactService startFactService,
      ExternalActionExecutionService actionExecutionService,
      ObjectProvider<ApprovalCaseService> approvalCaseProvider) {
    this(internalProvider, mappingService, registry, adapterResolver, instanceBindingService,
        resultBindingService, eventDispatcher, transactionManager, unmappedPolicy, scopeResolver,
        identityResolver, startFactService, actionExecutionService, approvalCaseProvider, null);
  }

  @Autowired
  public RoutedFlowInstanceService(
      ObjectProvider<BuiltInFlowInstanceService> internalProvider,
      WorkflowEngineMappingService mappingService,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      InstanceEventDispatcher eventDispatcher,
      PlatformTransactionManager transactionManager,
      @Value("${ouroboros.workflow.approval.external.unmapped-policy:internal}") String unmappedPolicy,
      WorkflowScopeResolver scopeResolver,
      ExternalUserIdentityResolver identityResolver,
      ExternalStartFactService startFactService,
      ExternalActionExecutionService actionExecutionService,
      ObjectProvider<ApprovalCaseService> approvalCaseProvider,
      ResultOnlyStartExecutor resultStarts) {
    this.internal = internalProvider.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到内置流程实例服务 Bean"));
    this.mappingService = mappingService;
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.instanceBindingService = instanceBindingService;
    this.resultBindingService = resultBindingService;
    this.eventDispatcher = eventDispatcher;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    this.externalInvocation = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    if (externalInvocation != null) { externalInvocation.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED); }
    this.unmappedPolicy = unmappedPolicy == null ? UNMAPPED_POLICY_INTERNAL : unmappedPolicy;
    this.scopeResolver = scopeResolver;
    this.identityResolver = identityResolver;
    this.startFactService = startFactService;
    this.actionExecutionService = actionExecutionService;
    this.resultStarts = resultStarts;
    this.approvalCaseService = approvalCaseProvider == null
        ? null : approvalCaseProvider.stream().findFirst().orElse(null);
    this.bindingRouteResolver = new ExternalBindingRouteResolver(mappingService);
  }

  private String resolveScope() {
    return scopeResolver.resolve();
  }

  /**
   * 读路径的作用域：未装配解析器时返回空，表示平台未启用外部协作，读路径降级为内置结果。
   *
   * <p>只容忍 {@link WorkflowScopeUnconfiguredException}；已装配解析器抛出的权限异常仍然传播。
   */
  private Optional<String> resolveScopeForRead() {
    try {
      return Optional.of(resolveScope());
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      return Optional.empty();
    }
  }

  @Override
  public Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData,
      CompletableFuture<Void> initTaskDeferred) {
    return start(new FlowStartCommand(flowKey, userId, taskAssignee, instanceVariables, formData, null, null))
        .map(FlowStartResult::acceptedExecutionId);
  }

  @Override
  public Try<String> start(
      String flowKey,
      String userId,
      Map<String, List<String>> taskAssignee,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData,
      Map<String, Object> cc) {
    return start(new FlowStartCommand(flowKey, userId, taskAssignee, instanceVariables, formData, cc, null))
        .map(FlowStartResult::acceptedExecutionId);
  }

  /**
   * 内部委托发起。无抄送时 {@code initTaskDeferred} 传已完成 future：内置引擎（flowable/activiti6）
   * 仅将其作为"完成后立即初始化首任务"的触发器（消费方式：null 兜底 completedFuture + thenRun 初始化），
   * 不依赖延迟时机——历史 5 秒 sleep 为伪异步，与 cc 非空路径传 null 行为等价，故以已完成 future 替代。
   */
  @Override
  public Try<FlowStartResult> start(FlowStartCommand command) {
    String flowKey = command.flowKey();
    String scope;
    try {
      scope = resolveScope();
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      if (UNMAPPED_POLICY_REJECT.equalsIgnoreCase(unmappedPolicy)) {
        return unmappedFailure(flowKey);
      }
      return startBuiltIn(command);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    Optional<WorkflowEngineMapping> mappingOpt;
    try {
      mappingOpt = mappingService.findActive(scope, flowKey);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    if (mappingOpt.isPresent()) {
      WorkflowEngineMapping mapping = mappingOpt.get();
      if (WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
        if (WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY.equals(
            registry.integrationModeOf(mapping.getAdapterId()))) {
          return startResultOnly(scope, mapping, command);
        }
        return startExternal(scope, mapping, command, null);
      }
    }
    if (mappingOpt.isEmpty() && UNMAPPED_POLICY_REJECT.equalsIgnoreCase(unmappedPolicy)) {
      return unmappedFailure(flowKey);
    }
    return startBuiltIn(command);
  }

  private <T> Try<T> unmappedFailure(String flowKey) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.WORKFLOW_ROUTE_NOT_CONFIGURED
            + "] 流程未配置映射，且 unmapped-policy=reject 禁止发起: " + flowKey));
  }

  private Try<FlowStartResult> startBuiltIn(FlowStartCommand command) {
    if (command.cc() == null) {
      return internal.start(command.flowKey(), command.userId(), command.taskAssignee(),
          command.instanceVariables(), command.formData(), CompletableFuture.completedFuture(null))
          .map(FlowStartResult::started);
    }
    return internal.start(command.flowKey(), command.userId(), command.taskAssignee(),
        command.instanceVariables(), command.formData(), command.cc()).map(FlowStartResult::started);
  }

  private Try<FlowStartResult> startResultOnly(String scope, WorkflowEngineMapping mapping, FlowStartCommand command) {
    if (resultStarts == null) {
      return Try.failure(new BusinessException("未装配外部结果型启动执行器"));
    }
    return resultStarts.startMapped(scope, mapping, new ResultOnlyStartCommand(
        command.flowKey(), stringVariable(command.instanceVariables(), FlowInstanceVariables.BUSINESS_RECORD_TYPE),
        stringVariable(command.instanceVariables(), FlowInstanceVariables.BUSINESS_RECORD_ID), command.userId(),
        command.formData(), command.idempotencyKey(), toCcTargets(command.cc()), command.approvers(), command.taskAssignee(),
        command.instanceVariables()))
        .map(result -> new FlowStartResult(result.trackingId(), FlowStartResult.Status.valueOf(result.status()),
            result.externalInstanceId(), result.externalUrl(), result.startFactId(), false));
  }

  /** 迁移入口使用原实例 ID 作为本地事实 ID，确保重试不会生成另一条本地绑定。 */
  public Try<String> startExternalForMigration(
      WorkflowEngineMapping mapping, String localInstanceId, String userId) {
    return startExternal(
        mapping.getScope(), mapping, new FlowStartCommand(mapping.getFlowKey(), userId, null,
            Map.of("migrationLocalInstanceId", localInstanceId), Map.of(), null, localInstanceId), localInstanceId)
        .map(FlowStartResult::acceptedExecutionId);
  }

  private Try<FlowStartResult> startExternal(
      String scope,
      WorkflowEngineMapping mapping,
      FlowStartCommand command,
      String requestedLocalInstanceId) {
    return externalInvocation == null
        ? startExternalCommand(scope, mapping, command, requestedLocalInstanceId)
        : externalInvocation.execute(status -> startExternalCommand(
            scope, mapping, command, requestedLocalInstanceId));
  }

  private Try<FlowStartResult> startExternalCommand(String scope, WorkflowEngineMapping mapping,
      FlowStartCommand start, String requestedLocalInstanceId) {
    String flowKey = start.flowKey();
    String userId = start.userId();
    Map<String, Object> instanceVariables = start.instanceVariables();
    ExternalWorkflowStarter starter;
    try {
      starter = adapterResolver.resolvePort(mapping, ExternalWorkflowStarter.class);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    ExternalStartCommand command = buildExternalStartCommand(mapping, start);
    String localInstanceId = requestedLocalInstanceId == null
        ? UUID.randomUUID().toString() : requestedLocalInstanceId;
    String requestJson;
    try {
      requestJson = objectMapper.writeValueAsString(command);
    } catch (Exception ex) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED + "] 外部启动命令序列化失败", ex));
    }
    ExternalStartCommand.ExternalBusinessRecord businessRecord = command.businessRecord();
    String approvalCaseId = stringVariable(instanceVariables, FlowInstanceVariables.APPROVAL_CASE_ID);
    Integer submissionRound = integerVariable(instanceVariables, FlowInstanceVariables.SUBMISSION_ROUND);
    Try<ExternalStartFactService.Reservation> reservationTry;
    if (approvalCaseId == null && submissionRound == null) {
      reservationTry = startFactService.reserve(
          scope, mapping,
          businessRecord == null ? null : businessRecord.recordType(),
          businessRecord == null ? null : businessRecord.recordId(),
          command.idempotencyKey(), startRequestFingerprint(mapping, start),
          ExternalStartFact.MODE_FULL_PROCESS, localInstanceId, userId,
          stringVariable(instanceVariables, FlowInstanceVariables.CALLBACK_FLOW), requestJson);
    } else {
      reservationTry = startFactService.reserve(
          scope, mapping,
          businessRecord == null ? null : businessRecord.recordType(),
          businessRecord == null ? null : businessRecord.recordId(),
          command.idempotencyKey(), startRequestFingerprint(mapping, start),
          ExternalStartFact.MODE_FULL_PROCESS, localInstanceId, userId,
          stringVariable(instanceVariables, FlowInstanceVariables.CALLBACK_FLOW), requestJson,
          approvalCaseId, submissionRound);
    }
    if (reservationTry.isFailure()) {
      return Try.failure(reservationTry.getCause());
    }
    ExternalStartFact fact = reservationTry.get().fact();
    if (!reservationTry.get().claimed()) {
      if (fact.getTrackingId() == null || fact.getTrackingId().isBlank()) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                + "] 外部启动事实已存在但本地实例 ID 尚未确定: factId=" + fact.getId()));
      }
      return Try.success(startResult(fact));
    }
    if (!startFactService.markInFlight(fact.getId(), fact.getClaimToken())) {
      return existingTrackingId(fact);
    }

    Try<ExternalStartResult> startTry;
    try (ExternalLeaseHeartbeat ignored = ExternalLeaseHeartbeat.start(
        () -> startFactService.renewClaim(
            fact.getId(), fact.getClaimToken(), ExternalStartFactService.DEFAULT_LEASE_SECONDS),
        ExternalStartFactService.DEFAULT_LEASE_SECONDS)) {
      startTry = invokeStarter(starter, command);
    }
    if (startTry.isFailure()) {
      Throwable cause = startTry.getCause();
      if (isUncertainStartFailure(cause)) {
        startFactService.markUnknown(fact.getId(), fact.getClaimToken(), null, null, stableError(cause));
        return existingTrackingId(fact);
      }
      startFactService.markFailed(fact.getId(), fact.getClaimToken(), stableError(cause));
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部启动失败: "
              + cause.getMessage(), cause));
    }
    ExternalStartResult result = startTry.get();
    ExternalStartFact effectiveFact = fact;
    String effectiveClaimToken = fact.getClaimToken();
    if (!startFactService.markReplied(
        fact.getId(), fact.getClaimToken(), result.externalInstanceId(), result.externalUrl())) {
      Optional<ExternalStartFactService.RecoveryClaim> adopted = startFactService.adoptLateResponse(
          fact.getId(), fact.getRequestFingerprint(), result.externalInstanceId(), result.externalUrl(),
          ExternalStartFactService.DEFAULT_LEASE_SECONDS);
      if (adopted.isEmpty()) {
        return existingTrackingId(fact);
      }
      effectiveFact = adopted.get().fact();
      effectiveClaimToken = adopted.get().claimToken();
    }

    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(effectiveFact.getTrackingId());
    binding.setFlowKey(flowKey);
    binding.setScope(scope);
    binding.setMappingId(mapping.getId());
    binding.setMappingVersion(mapping.getVersion());
    binding.setAdapterId(mapping.getAdapterId());
    binding.setConnectionId(mapping.getConnectionId());
    binding.setExternalInstanceId(result.externalInstanceId());
    binding.setExternalDefinitionId(mapping.getExternalDefinitionId());
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    binding.setExternalUrl(result.externalUrl());
    binding.setBusinessRecordType(stringVariable(instanceVariables, FlowInstanceVariables.BUSINESS_RECORD_TYPE));
    binding.setBusinessRecordId(stringVariable(instanceVariables, FlowInstanceVariables.BUSINESS_RECORD_ID));
    binding.setCallbackFlow(stringVariable(instanceVariables, FlowInstanceVariables.CALLBACK_FLOW));
    binding.setApprovalCaseId(effectiveFact.getApprovalCaseId() == null
        ? approvalCaseId : effectiveFact.getApprovalCaseId());
    binding.setSubmissionRound(effectiveFact.getSubmissionRound() == null
        ? submissionRound : effectiveFact.getSubmissionRound());

    Try<ExternalInstanceBinding> persisted = persistInstanceStart(binding, effectiveFact);
    if (persisted.isFailure()) {
      startFactService.markUnknown(
          effectiveFact.getId(), effectiveClaimToken, result.externalInstanceId(), result.externalUrl(),
          stableError(persisted.getCause()));
      return existingTrackingId(effectiveFact);
    }
    return Try.success(new FlowStartResult(persisted.get().getLocalInstanceId(), FlowStartResult.Status.STARTED,
        result.externalInstanceId(), result.externalUrl(), effectiveFact.getId(), true));
  }

  /**
   * 组装外部启动命令（发起与运行中实例迁移共用，组装逻辑仅存此处）。
   *
   * @param mapping            生效中的外部引擎映射
   * @param userId             启动人（平台规范用户 ID）
   * @param instanceVariables  自定义流程变量
   * @param formData           表单数据
   * @param cc                 可选抄送人（null 表示不抄送）
   */
  public ExternalStartCommand buildExternalStartCommand(
      WorkflowEngineMapping mapping,
      String userId,
      Map<String, Object> instanceVariables,
      Map<String, Object> formData,
      Map<String, Object> cc) {
    return buildExternalStartCommand(mapping, new FlowStartCommand(
        mapping.getFlowKey(), userId, null, instanceVariables, formData, cc, null));
  }

  private ExternalStartCommand buildExternalStartCommand(WorkflowEngineMapping mapping, FlowStartCommand command) {
    return new ExternalStartCommand(
        command.userId(),
        new ExternalProcessDefinitionRef(
            mapping.getExternalDefinitionNamespace(),
            mapping.getExternalDefinitionId(),
            mapping.getExternalDefinitionVersion(),
            mapping.getExternalDefinitionSchemaHash()),
        businessRecordOf(command.instanceVariables()),
        command.formData(),
        FlowInstanceVariables.customVariables(command.instanceVariables()),
        command.idempotencyKey(),
        toCcTargets(command.cc()),
        command.approvers(),
        command.taskAssignee());
  }

  private Try<ExternalInstanceBinding> persistInstanceStart(
      ExternalInstanceBinding binding,
      ExternalStartFact fact) {
    Optional<ExternalInstanceBinding> existing = instanceBindingService.findByExternalInstance(
        binding.getAdapterId(), binding.getConnectionId(), binding.getExternalInstanceId());
    if (existing.isPresent()) {
      if (!binding.getLocalInstanceId().equals(existing.get().getLocalInstanceId())) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                + "] 外部实例已绑定到其他本地实例: " + binding.getExternalInstanceId()));
      }
    }
    if (transactionTemplate == null) {
      Try<ExternalInstanceBinding> persisted = existing.isPresent()
          ? Try.success(existing.get()) : createAndDispatch(binding, fact);
      if (existing.isPresent() && persisted.isSuccess()) {
        bindApprovalCaseExecution(persisted.get());
      }
      if (persisted.isSuccess()
          && !startFactService.markStarted(fact.getId(), fact.getClaimToken(), persisted.get().getLocalInstanceId())) {
        return Try.failure(new BusinessException(
            "外部启动事实完成失败或 claim 已失效: factId=" + fact.getId()));
      }
      return persisted;
    }
    try {
      ExternalInstanceBinding persisted = ExternalFactTransaction.execute(transactionTemplate, ExternalStartFact.class, () -> {
        Try<ExternalInstanceBinding> created = existing.isPresent()
            ? Try.success(existing.get()) : createAndDispatch(binding, fact);
        if (created.isFailure()) {
          throw new BusinessException("持久化外部实例绑定失败", created.getCause());
        }
        if (existing.isPresent()) {
          bindApprovalCaseExecution(created.get());
        }
        if (!startFactService.markStarted(
            fact.getId(), fact.getClaimToken(), created.get().getLocalInstanceId())) {
          throw new BusinessException("外部启动事实完成失败或 claim 已失效: factId=" + fact.getId());
        }
        return created.get();
      });
      return Try.success(persisted);
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private void bindApprovalCaseExecution(ExternalInstanceBinding binding) {
    if (approvalCaseService == null || binding == null
        || binding.getApprovalCaseId() == null || binding.getSubmissionRound() == null) {
      return;
    }
    approvalCaseService.bindExecution(
        binding.getApprovalCaseId(), binding.getSubmissionRound(), binding.getLocalInstanceId());
  }

  private Try<ExternalInstanceBinding> createAndDispatch(
      ExternalInstanceBinding binding, ExternalStartFact fact) {
    Try<ExternalInstanceBinding> created = instanceBindingService.create(binding);
    if (created.isFailure()) {
      return created;
    }
    if (!Objects.equals(fact.getTrackingId(), created.get().getLocalInstanceId())) {
      return Try.failure(new BusinessException("外部实例绑定不能改变启动意图的 trackingId"));
    }
    bindApprovalCaseExecution(created.get());
    dispatchStarted(fact);
    return created;
  }

  private Try<FlowStartResult> existingTrackingId(ExternalStartFact fact) {
    return startFactService.findById(fact.getId())
        .filter(current -> current.getTrackingId() != null && !current.getTrackingId().isBlank())
        .map(this::startResult)
        .map(Try::success)
        .orElseGet(() -> Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                + "] 外部启动事实失去 claim，且本地实例 ID 尚未可用: factId=" + fact.getId())));
  }

  private FlowStartResult startResult(ExternalStartFact fact) {
    return new FlowStartResult(fact.getTrackingId(), FlowStartResult.Status.valueOf(fact.getStatus()),
        fact.getExternalInstanceId(), fact.getExternalUrl(), fact.getId(), true);
  }

  private String startRequestFingerprint(
      WorkflowEngineMapping mapping,
      FlowStartCommand command) {
    String userId = command.userId();
    Map<String, Object> instanceVariables = command.instanceVariables();
    Map<String, Object> formData = command.formData();
    Map<String, Object> cc = command.cc();
    try {
      Map<String, Object> request = new java.util.TreeMap<>();
      request.put("mappingId", mapping.getId());
      request.put("mappingVersion", mapping.getVersion());
      request.put("scope", mapping.getScope());
      request.put("flowKey", mapping.getFlowKey());
      request.put("userId", userId);
      request.put("businessRecord", businessRecordOf(instanceVariables));
      request.put("instanceVariables", instanceVariables);
      request.put("formData", formData);
      request.put("cc", cc);
      if (command.taskAssignee() != null && !command.taskAssignee().isEmpty()) {
        request.put("taskAssignee", command.taskAssignee());
      }
      if (command.approvers() != null && !command.approvers().isEmpty()) {
        request.put("approvers", command.approvers());
      }
      return ExternalRouteKeys.of("full-start-fingerprint", objectMapper.writeValueAsString(request));
    } catch (Exception ex) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
              + "] 外部启动幂等键生成失败", ex);
    }
  }

  private boolean isUncertainStartFailure(Throwable cause) {
    Throwable cursor = cause;
    while (cursor != null) {
      if (cursor instanceof ExternalOperationFailure failure) {
        return failure.certainty() == ExternalEffectCertainty.UNKNOWN;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  /** 适配器越界异常不能让启动事实永久停留在 STARTING。 */
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

  private String stableError(Throwable cause) {
    String message = cause.getMessage();
    return message == null || message.isBlank()
        ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + message;
  }

  private void dispatchStarted(ExternalStartFact fact) {
    var stored = startFactService.findById(fact.getId()).orElseThrow(() -> new BusinessException("外部启动意图不存在"));
    eventDispatcher.enqueueEvent(ExternalFlowEventFactory.started(stored));
  }

  @Override
  public Try<Void> pause(String instanceId, String userId, String comment) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isEmpty()) {
      Optional<ExternalResultBinding> resultBinding = resultBindingService.findByTrackingId(instanceId);
      if (resultBinding.isPresent()) {
        return resultOnlyUnsupported("暂停");
      }
      return internal.pause(instanceId, userId, comment);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.pause(instanceId, userId, comment);
    }
    return executeInstanceAction(binding.get(), "pause", userId, comment, null, ExternalInstanceBinding.STATUS_SUSPENDED, null);
  }

  @Override
  public Try<Void> resume(String instanceId, String userId) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isEmpty()) {
      Optional<ExternalResultBinding> resultBinding = resultBindingService.findByTrackingId(instanceId);
      if (resultBinding.isPresent()) {
        return resultOnlyUnsupported("恢复");
      }
      return internal.resume(instanceId, userId);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.resume(instanceId, userId);
    }
    return executeInstanceAction(binding.get(), "resume", userId, null, null, ExternalInstanceBinding.STATUS_RUNNING, null);
  }

  @Override
  public Try<Void> cancel(String instanceId, String userId, String comment) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isPresent()) {
      if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
        return internal.cancel(instanceId, userId, comment);
      }
      return executeInstanceAction(binding.get(), "cancel", userId, comment, "cancelled",
          ExternalInstanceBinding.STATUS_FINISHED, EventType.CANCELLED);
    }
    Optional<ExternalResultBinding> resultBinding = resultBindingService.findByTrackingId(instanceId);
    if (resultBinding.isPresent()) {
      return cancelResultOnly(resultBinding.get(), userId, comment, "cancelled");
    }
    return internal.cancel(instanceId, userId, comment);
  }

  @Override
  public Try<Void> complete(String instanceId, String userId, Boolean approved, String comment) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isEmpty()) {
      Optional<ExternalResultBinding> resultBinding = resultBindingService.findByTrackingId(instanceId);
      if (resultBinding.isPresent()) {
        return resultOnlyUnsupported("完成");
      }
      return internal.complete(instanceId, userId, approved, comment);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.complete(instanceId, userId, approved, comment);
    }
    String outcome = Boolean.TRUE.equals(approved) ? "approved" : "rejected";
    EventType eventType = Boolean.TRUE.equals(approved) ? EventType.APPROVED : EventType.REJECTED;
    return executeInstanceAction(binding.get(), "complete", userId, comment, outcome,
        ExternalInstanceBinding.STATUS_FINISHED, eventType);
  }

  @Override
  public Try<Void> withdraw(String instanceId, String userId, String comment) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isPresent()) {
      if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
        return internal.withdraw(instanceId, userId, comment);
      }
      return executeInstanceAction(binding.get(), "cancel", userId, comment, "withdrawn",
          ExternalInstanceBinding.STATUS_FINISHED, EventType.CANCELLED);
    }
    Optional<ExternalResultBinding> resultBinding = resultBindingService.findByTrackingId(instanceId);
    if (resultBinding.isPresent()) {
      return cancelResultOnly(resultBinding.get(), userId, comment, "withdrawn");
    }
    return internal.withdraw(instanceId, userId, comment);
  }

  private Try<Void> executeInstanceAction(
      ExternalInstanceBinding binding,
      String canonicalAction,
      String userId,
      String comment,
      String outcome,
      String targetStatus,
      EventType eventType) {
    // 当前生产适配器（钉钉/fake）未声明实例动作能力，分支为未来实例动作能力预留
    if (!registry.capabilitiesOf(binding.getAdapterId()).contains(Capability.EXTERNAL_INSTANCE_ACTION)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 适配器不支持实例动作: "
              + binding.getAdapterId()));
    }
    return actionExecutionService.executeInstance(binding, new ExternalActionRequest(
        canonicalAction, canonicalAction, userId, comment, null, null, binding.getMappingVersion(),
        new ExternalInstanceRef(binding.getExternalInstanceId(), binding.getConnectionId()), null),
        targetStatus, outcome, eventType);
  }

  private Try<Void> cancelResultOnly(ExternalResultBinding binding, String userId, String comment, String outcome) {
    // 当前生产适配器（钉钉/fake）未声明实例动作能力，分支为未来实例动作能力预留
    if (!registry.capabilitiesOf(binding.getAdapterId()).contains(Capability.EXTERNAL_INSTANCE_CANCEL)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 适配器不支持发起人撤销: "
              + binding.getAdapterId()));
    }
    return actionExecutionService.executeResult(binding, new ExternalActionRequest(
        "cancel", "cancel", userId, comment, null, null, binding.getMappingVersion(),
        new ExternalInstanceRef(binding.getExternalInstanceId(), binding.getConnectionId()), null),
        outcome, EventType.CANCELLED);
  }

  @Override
  public List<FlowTimeline> getTimeLine(String instanceId) {
    Optional<ExternalInstanceBinding> binding = instanceBindingService.findByLocalId(instanceId);
    if (binding.isEmpty()) {
      Optional<ExternalResultBinding> result = resultBindingService.findByTrackingId(instanceId);
      if (result.isEmpty()) { return internal.getTimeLine(instanceId); }
      ExternalResultBinding resultBinding = result.get();
      requireReadScope(resultBinding.getScope());
      requireExternalResultAuthority(resultBinding);
      return externalTimeline(instanceId, resultBinding.getAdapterId(), resultBinding.getConnectionId(),
          resultBinding.getExternalInstanceId());
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getTimeLine(instanceId);
    }
    return externalTimeline(instanceId, binding.get().getAdapterId(), binding.get().getConnectionId(),
        binding.get().getExternalInstanceId());
  }

  private List<FlowTimeline> externalTimeline(
      String instanceId, String adapterId, String connectionId, String externalInstanceId) {
    ExternalRecordReader reader = adapterResolver.resolvePort(adapterId, connectionId, ExternalRecordReader.class);
    Try<ExternalRecordPage> records = reader.queryRecords(new ExternalInstanceRef(externalInstanceId, connectionId));
    if (records.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部审批记录查询失败: "
              + instanceId,
          records.getCause());
    }
    List<FlowTimeline> timeline = new ArrayList<>();
    for (ExternalRecordPage.ExternalRecordEntry entry : records.get().records()) {
      FlowTimeline item = new FlowTimeline();
      FlowTimeline.TaskInstance task = new FlowTimeline.TaskInstance();
      task.setInstanceId(instanceId);
      task.setAssignee(resolveAssignee(entry.operator(), adapterId, connectionId));
      task.setState(mapRecordState(entry.action()));
      task.setComment(entry.comment());
      item.setTaskInstances(List.of(task));
      item.setState(mapRecordState(entry.action()));
      timeline.add(item);
    }
    return timeline;
  }

  private Try<Void> resultOnlyUnsupported(String operation) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] RESULT_ONLY 流程不支持实例"
            + operation + "操作"));
  }

  /** 外部操作人解析为平台用户；找不到映射时 fail-fast，不把外部字符串或 null 当作平台用户。 */
  private String resolveAssignee(String externalUserId, String adapterId, String connectionId) {
    if (externalUserId == null || externalUserId.isBlank()) {
      return null;
    }
    Optional<String> mapped = identityResolver.toPlatformUser(externalUserId, adapterId, connectionId);
    if (mapped == null || mapped.isEmpty()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING + "] 外部审批记录操作人缺少平台映射: "
              + adapterId + "/" + connectionId + "/" + externalUserId);
    }
    return mapped.get();
  }

  private TaskState mapRecordState(String action) {
    return ExternalStatusMapper.recordActionToTaskState(action);
  }

  @Override
  public Try<FlowProcessInfo> getProcessInfo(String instanceId) {
    return Try.of(() -> {
      Optional<ExternalInstanceBinding> instance = instanceBindingService.findByLocalId(instanceId);
      if (instance.isPresent()) {
        ExternalInstanceBinding binding = instance.get();
        if (bindingRouteResolver.resolve(binding.getMappingId(), binding.getMappingVersion()).isBuiltIn()) {
          return internal.getProcessInfo(instanceId).get();
        }
        requireReadScope(binding.getScope());
        FlowProcessInfo info = externalProcessInfo(instanceId, binding.getFlowKey(),
            binding.getStatus(), binding.getOutcome(), binding.getExternalUrl(), binding.getAdapterId());
        info.setStartTime(binding.getCreatedAt());
        info.setCancellable(!ExternalInstanceBinding.STATUS_FINISHED.equals(binding.getStatus())
            && registry.capabilitiesOf(binding.getAdapterId()).contains(Capability.EXTERNAL_INSTANCE_ACTION));
        return info;
      }
      Optional<ExternalResultBinding> result = resultBindingService.findByTrackingId(instanceId);
      if (result.isPresent()) {
        ExternalResultBinding binding = result.get();
        requireReadScope(binding.getScope());
        requireExternalResultAuthority(binding);
        FlowProcessInfo info = externalProcessInfo(instanceId, binding.getFlowKey(),
            binding.getStatus(), binding.getOutcome(), binding.getExternalUrl(), binding.getAdapterId());
        info.setStartTime(binding.getCreatedAt());
        info.setEndTime(binding.getFinalResultAt());
        info.setCancellable(binding.isRunning()
            && registry.capabilitiesOf(binding.getAdapterId()).contains(Capability.EXTERNAL_INSTANCE_CANCEL));
        return info;
      }
      Optional<ExternalStartFact> fact = resolveScopeForRead()
          .flatMap(scope -> startFactService.findByTrackingId(scope, instanceId));
      if (fact.isPresent()) {
        ExternalStartFact start = fact.get();
        FlowProcessInfo info = new FlowProcessInfo();
        info.setInstanceId(instanceId);
        info.setFlowKey(start.getFlowKey());
        info.setStatus(start.getStatus().toLowerCase(java.util.Locale.ROOT));
        info.setExternalUrl(start.getExternalUrl());
        info.setStarter(start.getActorUserId());
        info.setStartTime(start.getCreatedAt());
        return info;
      }
      return internal.getProcessInfo(instanceId).get();
    });
  }

  private FlowProcessInfo externalProcessInfo(
      String executionId, String flowKey, String status, String outcome, String externalUrl, String adapterId) {
    FlowProcessInfo info = new FlowProcessInfo();
    info.setInstanceId(executionId);
    info.setFlowKey(flowKey);
    info.setStatus(mapLocalStatus(status));
    info.setOutcome(outcome);
    info.setExternalUrl(externalUrl);
    // 外部执行没有本地 BPMN 实例；不能把平台模型当成实际执行版本画图。
    info.setTimelineAvailable(registry.capabilitiesOf(adapterId).contains(Capability.EXTERNAL_RECORD_QUERY));
    return info;
  }

  private String mapLocalStatus(String status) {
    return status == null ? null : status.toLowerCase(java.util.Locale.ROOT);
  }

  private void requireReadScope(String scope) {
    if (!Objects.equals(resolveScope(), scope)) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED
          + "] 无权读取其他作用域的审批执行");
    }
  }

  private void requireExternalResultAuthority(ExternalResultBinding binding) {
    if (bindingRouteResolver.resolve(binding.getMappingId(), binding.getMappingVersion()).isBuiltIn()) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
          + "] 结果型执行必须由外部引擎权威映射提供: " + binding.getTrackingId());
    }
  }

  @Override
  public Try<Map<String, FlowProcessInfo>> getProcessInfoByApprovalUrls(
      String flowKey, List<String> approvalUrls) {
    return internal.getProcessInfoByApprovalUrls(flowKey, approvalUrls);
  }

  @Override
  public Try<Map<String, FlowProcessInfo>> getProcessInfoByApprovalUrls(
      String flowKey, List<String> approvalUrls, String businessRecordType) {
    return getProcessInfoByApprovalUrls(flowKey, approvalUrls).flatMap(local -> Try.of(() -> {
      Map<String, FlowProcessInfo> result = new LinkedHashMap<>(local);
      if (approvalCaseService == null || businessRecordType == null || businessRecordType.isBlank()) {
        return result;
      }
      for (String url : approvalUrls) {
        // 审批地址由业务模型生成，最后两个路径段固定为记录 ID / approval。
        if (url == null || !url.endsWith("/approval")) {
          throw new BusinessException("审批地址必须以 /{业务记录ID}/approval 结束");
        }
        String recordPath = url.substring(0, url.length() - "/approval".length());
        String recordId = recordPath.substring(recordPath.lastIndexOf('/') + 1);
        var approvalCase = approvalCaseService.findByBusinessRecord(businessRecordType, recordId, flowKey);
        if (approvalCase.isEmpty()) { continue; }
        String executionId = approvalCase.get().getActiveExecutionId();
        if (executionId == null) {
          Optional<ExternalStartFact> fact = resolveScopeForRead().flatMap(scope -> startFactService
              .findByBusinessStartRouteKey(startFactService.businessStartRouteKey(
                  scope, flowKey, businessRecordType, recordId)));
          executionId = fact.map(ExternalStartFact::getTrackingId).orElse(null);
        }
        if (executionId == null) { executionId = approvalCase.get().getLastStateInstanceId(); }
        FlowProcessInfo existing = result.get(url);
        if (executionId != null && (existing == null || !executionId.equals(existing.getInstanceId()))) {
          result.put(url, getProcessInfo(executionId).get());
        }
      }
      return result;
    }));
  }

  @Override
  public Try<SchemaNode> getInitialForm(String flowKey) {
    return Try.of(() -> {
      Optional<String> scope = resolveScopeForRead();
      return scope.isEmpty() ? Optional.<WorkflowEngineMapping>empty()
          : mappingService.findActive(scope.get(), flowKey);
    }).flatMap(mapping -> {
      if (mapping.isEmpty() || !WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(
          mapping.get().getAuthority())) {
        return internal.getInitialForm(flowKey);
      }
      // 外部执行不部署本地 BPMN；定义门面读取平台模型，表单继续走已有 SPI。
      return definitions.getStructuredFlowDefinition(flowKey)
          .flatMap(new FlowInitialFormResolver(formServices)::resolve);
    });
  }

  @Override
  public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
    int normalizedOffset = offset == null ? 0 : Math.max(offset, 0);
    int normalizedLimit = limit == null ? 100 : Math.max(limit, 0);
    if (normalizedLimit == 0) {
      return List.of();
    }
    Long internalCount = internal.getStatisticCount(keyword, status);
    Map<String, FlowInstanceStatistic> byFlowKey = new LinkedHashMap<>();
    for (FlowInstanceStatistic statistic : internal.getStatistic(keyword, status, 0, safePageSize(internalCount))) {
      byFlowKey.put(statistic.getFlowKey(), statistic);
    }
    if (includesActiveExternalDefinitionStatus(status)) {
      for (WorkflowEngineMapping mapping : externalInstanceMappings()) {
        if (!matchesStatisticKeyword(mapping, keyword)) {
          continue;
        }
        FlowInstanceStatistic statistic = byFlowKey.computeIfAbsent(
            mapping.getFlowKey(), key -> externalStatistic(mapping));
        addExternalInstanceCounts(statistic, mapping);
      }
    }
    List<FlowInstanceStatistic> statistics = new ArrayList<>(byFlowKey.values());
    statistics.sort(Comparator.comparing(FlowInstanceStatistic::getFlowKey,
        Comparator.nullsLast(String::compareTo)));
    if (statistics.size() <= normalizedOffset) {
      return List.of();
    }
    int toIndex = Math.min(statistics.size(), normalizedOffset + normalizedLimit);
    return new ArrayList<>(statistics.subList(normalizedOffset, toIndex));
  }

  @Override
  public Long getStatisticCount(String keyword, String status) {
    Long internalCount = internal.getStatisticCount(keyword, status);
    if (!includesActiveExternalDefinitionStatus(status)) {
      return internalCount;
    }
    Set<String> internalFlowKeys = new HashSet<>();
    for (FlowInstanceStatistic statistic : internal.getStatistic(keyword, status, 0, safePageSize(internalCount))) {
      internalFlowKeys.add(statistic.getFlowKey());
    }
    Set<String> externalOnlyFlowKeys = new HashSet<>();
    for (WorkflowEngineMapping mapping : externalInstanceMappings()) {
      if (matchesStatisticKeyword(mapping, keyword) && !internalFlowKeys.contains(mapping.getFlowKey())) {
        externalOnlyFlowKeys.add(mapping.getFlowKey());
      }
    }
    return (internalCount == null ? 0L : internalCount) + externalOnlyFlowKeys.size();
  }

  private int safePageSize(Long count) {
    if (count == null || count <= 0) {
      return 0;
    }
    return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : count.intValue();
  }

  private boolean includesActiveExternalDefinitionStatus(String status) {
    return status == null || status.isBlank() || "deployed".equalsIgnoreCase(status);
  }

  private boolean matchesStatisticKeyword(WorkflowEngineMapping mapping, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return true;
    }
    String normalized = keyword.toLowerCase();
    return containsIgnoreCase(mapping.getFlowKey(), normalized)
        || containsIgnoreCase(mapping.getExternalDefinitionId(), normalized);
  }

  private boolean containsIgnoreCase(String value, String normalizedKeyword) {
    return value != null && value.toLowerCase().contains(normalizedKeyword);
  }

  private FlowInstanceStatistic externalStatistic(WorkflowEngineMapping mapping) {
    FlowInstanceStatistic statistic = new FlowInstanceStatistic();
    statistic.setFlowKey(mapping.getFlowKey());
    statistic.setDisplayName(mapping.getFlowKey());
    statistic.setCategory("external");
    statistic.setDescription(mapping.getExternalDefinitionId());
    statistic.setStatus("deployed");
    statistic.setTotal(0L);
    statistic.setRunning(0L);
    statistic.setSuspended(0L);
    statistic.setFinished(0L);
    return statistic;
  }

  private void addExternalInstanceCounts(FlowInstanceStatistic statistic, WorkflowEngineMapping mapping) {
    long running = countExternalInstances(mapping, ExternalInstanceBinding.STATUS_RUNNING);
    long suspended = countExternalInstances(mapping, ExternalInstanceBinding.STATUS_SUSPENDED);
    long finished = countExternalInstances(mapping, ExternalInstanceBinding.STATUS_FINISHED);
    statistic.setRunning(nullToZero(statistic.getRunning()) + running);
    statistic.setSuspended(nullToZero(statistic.getSuspended()) + suspended);
    statistic.setFinished(nullToZero(statistic.getFinished()) + finished);
    statistic.setTotal(nullToZero(statistic.getTotal()) + running + suspended + finished);
  }

  private long nullToZero(Long value) {
    return value == null ? 0L : value;
  }

  @Override
  public FlowDurationStatistic getDurationStatistic(String flowKey, Date from, Date to) {
    if (countExternalInstances(flowKey, ExternalInstanceBinding.STATUS_FINISHED) > 0) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 外部实例耗时统计暂不支持: " + flowKey);
    }
    return internal.getDurationStatistic(flowKey, from, to);
  }

  @Override
  public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
    int normalizedOffset = offset == null ? 0 : Math.max(offset, 0);
    int normalizedLimit = limit == null ? 100 : Math.max(limit, 0);
    List<FlowInstanceDetail> instances = new ArrayList<>(
        internal.getInstances(flowKey, status, 0, normalizedOffset + normalizedLimit));
    String externalStatus = mapExternalStatus(status);
    if (externalStatus != null) {
      for (ExternalInstanceBinding binding : searchExternalInstances(flowKey, externalStatus, normalizedOffset + normalizedLimit)) {
        FlowInstanceDetail detail = new FlowInstanceDetail();
        detail.setInstanceId(binding.getLocalInstanceId());
        detail.setFlowKey(binding.getFlowKey());
        detail.setStatus(mapLocalStatus(binding.getStatus()));
        detail.setCancellable(!ExternalInstanceBinding.STATUS_FINISHED.equals(binding.getStatus())
            && registry.capabilitiesOf(binding.getAdapterId()).contains(Capability.EXTERNAL_INSTANCE_ACTION));
        detail.setOutcome(binding.getOutcome());
        detail.setStartTime(binding.getCreatedAt());
        instances.add(detail);
      }
      instances.sort(Comparator.comparing(FlowInstanceDetail::getStartTime,
          Comparator.nullsLast(Comparator.reverseOrder())));
    }
    if (instances.size() <= normalizedOffset) {
      return List.of();
    }
    int toIndex = Math.min(instances.size(), normalizedOffset + normalizedLimit);
    return new ArrayList<>(instances.subList(normalizedOffset, toIndex));
  }

  @Override
  public Long getInstancesCount(String flowKey, String status) {
    Long internalCount = internal.getInstancesCount(flowKey, status);
    String externalStatus = mapExternalStatus(status);
    if (externalStatus == null) {
      return internalCount;
    }
    return internalCount + countExternalInstances(flowKey, externalStatus);
  }

  private List<ExternalInstanceBinding> searchExternalInstances(String flowKey, String externalStatus, int limit) {
    List<ExternalInstanceBinding> bindings = new ArrayList<>();
    for (WorkflowEngineMapping mapping : externalInstanceMappings(flowKey)) {
      bindings.addAll(instanceBindingService.searchByRoute(
          mapping.getScope(), mapping.getId(), mapping.getVersion(), mapping.getAdapterId(), mapping.getConnectionId(),
          externalStatus, 0, limit));
    }
    return bindings;
  }

  private long countExternalInstances(String flowKey, String externalStatus) {
    long total = 0L;
    for (WorkflowEngineMapping mapping : externalInstanceMappings(flowKey)) {
      total += countExternalInstances(mapping, externalStatus);
    }
    return total;
  }

  private long countExternalInstances(WorkflowEngineMapping mapping, String externalStatus) {
    return instanceBindingService.countByRoute(
        mapping.getScope(), mapping.getId(), mapping.getVersion(), mapping.getAdapterId(), mapping.getConnectionId(),
        externalStatus);
  }

  private List<WorkflowEngineMapping> externalInstanceMappings(String flowKey) {
    return resolveScopeForRead()
        .map(scope -> mappingService.findByScopeAndFlowKey(scope, flowKey).stream()
            .filter(this::isFullProcessExternalMapping)
            .toList())
        .orElseGet(List::of);
  }

  private List<WorkflowEngineMapping> externalInstanceMappings() {
    return resolveScopeForRead()
        .map(scope -> mappingService.findActiveByScope(scope).stream()
            .filter(this::isFullProcessExternalMapping)
            .toList())
        .orElseGet(List::of);
  }

  private boolean isFullProcessExternalMapping(WorkflowEngineMapping mapping) {
    return WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())
        && WorkflowEngineAdapterRegistry.MODE_FULL_PROCESS
            .equals(registry.integrationModeOf(mapping.getAdapterId()));
  }

  @Override
  public Try<JsonNode> getInstanceFlowDefinitionV2(String instanceId) {
    return getProcessInfo(instanceId).flatMap(info -> info.isDiagramAvailable()
        ? internal.getInstanceFlowDefinitionV2(instanceId)
        : Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
            + "] 当前执行不提供平台流程图")));
  }

  @Override
  public List<String> getActiveActivityIds(String instanceId) {
    return getProcessInfo(instanceId).get().isDiagramAvailable()
        ? internal.getActiveActivityIds(instanceId) : List.of();
  }

  @Override
  public FlowDefinition getInstanceFlowDefinition(String instanceId) {
    return internal.getInstanceFlowDefinition(instanceId);
  }

  private String mapExternalStatus(String status) {
    return ExternalStatusMapper.instanceStatusFromQuery(status);
  }

  private ExternalStartCommand.ExternalBusinessRecord businessRecordOf(Map<String, Object> instanceVariables) {
    String recordType = stringVariable(instanceVariables, "businessRecordType");
    String recordId = stringVariable(instanceVariables, "businessRecordId");
    if (recordType == null || recordId == null) {
      return null;
    }
    return new ExternalStartCommand.ExternalBusinessRecord(recordType, recordId);
  }

  private String stringVariable(Map<String, Object> variables, String key) {
    if (variables == null) {
      return null;
    }
    Object value = variables.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private Integer integerVariable(Map<String, Object> variables, String key) {
    if (variables == null) {
      return null;
    }
    Object value = variables.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<CcTarget> toCcTargets(Map<String, Object> cc) {
    if (cc != null && cc.get("targets") instanceof List<?> targets) {
      return targets.stream().map(value -> {
        if (!(value instanceof Map<?, ?> target) || target.get("targetType") == null || target.get("targetId") == null) {
          throw new BusinessException("抄送对象缺少类型或标识");
        }
        return new CcTarget(String.valueOf(target.get("targetType")), String.valueOf(target.get("targetId")),
            target.get("position") == null ? CcTarget.POSITION_START : String.valueOf(target.get("position")));
      }).toList();
    }
    if (cc == null || cc.get("userIds") == null) {
      return null;
    }
    Object rawUserIds = cc.get("userIds");
    if (!(rawUserIds instanceof List<?> userIds)) {
      return null;
    }
    String position = cc.get("position") == null ? CcTarget.POSITION_START : String.valueOf(cc.get("position"));
    return userIds.stream()
        .map(String::valueOf)
        .map(userId -> new CcTarget("user", userId, position))
        .toList();
  }
}
