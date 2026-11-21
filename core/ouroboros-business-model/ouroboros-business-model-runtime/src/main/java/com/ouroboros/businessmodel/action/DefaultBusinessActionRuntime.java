package com.ouroboros.businessmodel.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.TreeMap;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.data.record.Record;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;

/** Executes a published Action through its selected implementation binding. */
public class DefaultBusinessActionRuntime implements BusinessActionRuntime {
  private final BusinessModelBehaviorProvider behaviorProvider;
  private final LogicFlowExecutionFacade logicFlowExecutionFacade;
  private final Supplier<List<BusinessActionHandler>> handlerSupplier;
  private final PlatformTransactionManager transactionManager;
  private final BusinessActionExecutionAuditRecorder auditRecorder;
  private BusinessActionRequestProvider requestProvider = () -> null;
  private List<BusinessActionInvocationValidator> invocationValidators = List.of();
  private BusinessActionMutationEventEmitter mutationEvents = new BusinessActionMutationEventEmitter(() -> {
    throw new BusinessException("默认业务事件运行时不可用");
  }, new BusinessMutationEventProperties());

  public DefaultBusinessActionRuntime(BusinessModelBehaviorProvider behaviorProvider,
                                      LogicFlowExecutionFacade logicFlowExecutionFacade,
                                      List<BusinessActionHandler> handlers,
                                      PlatformTransactionManager transactionManager,
                                      BusinessActionExecutionAuditRecorder auditRecorder) {
    this(behaviorProvider, logicFlowExecutionFacade, () -> handlers, transactionManager, auditRecorder);
  }

  public DefaultBusinessActionRuntime(BusinessModelBehaviorProvider behaviorProvider,
                                      LogicFlowExecutionFacade logicFlowExecutionFacade,
                                      Supplier<List<BusinessActionHandler>> handlerSupplier,
                                      PlatformTransactionManager transactionManager,
                                      BusinessActionExecutionAuditRecorder auditRecorder) {
    this.behaviorProvider = Objects.requireNonNull(behaviorProvider, "behaviorProvider must not be null");
    this.logicFlowExecutionFacade = Objects.requireNonNull(logicFlowExecutionFacade, "logicFlowExecutionFacade must not be null");
    this.handlerSupplier = Objects.requireNonNull(handlerSupplier, "handlerSupplier must not be null");
    this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
  }

  public void setRequestProvider(BusinessActionRequestProvider requestProvider) {
    this.requestProvider = Objects.requireNonNull(requestProvider);
  }

  public void setMutationEvents(BusinessActionMutationEventEmitter mutationEvents) {
    this.mutationEvents = Objects.requireNonNull(mutationEvents);
  }

  public void setInvocationValidators(List<BusinessActionInvocationValidator> validators) {
    this.invocationValidators = List.copyOf(validators);
  }

  @Override
  public Optional<BusinessActionImplementation> inspectImplementation(String modelFullName, String actionName) {
    return BusinessModelPublication.withPublishedContext(() -> behaviorProvider.getBusinessModelBehavior(modelFullName)
        .flatMap(model -> Optional.ofNullable(model.getActions().get(actionName)).flatMap(action ->
            resolveImplementationIfAvailable(action).map(implementation -> new BusinessActionImplementation(model.getModelRevision(),
                implementation.implementationRef(), implementation.flowModel() == null
                    ? implementation.handler().getImplementationVersion() : implementation.flowModel().getRevision())))));
  }

  @Override
  public Optional<String> inspectContractFingerprint(String modelFullName, String actionName) {
    return BusinessModelPublication.withPublishedContext(() -> behaviorProvider.getBusinessModelBehavior(modelFullName)
        .map(behavior -> behavior.getActions().get(actionName))
        .map(com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts::actionFingerprint));
  }

  @Override
  public Object invoke(BusinessActionInvocation invocation) {
    return BusinessModelPublication.withPublishedContext(() -> invokeInPublishedContext(invocation));
  }

  private Object invokeInPublishedContext(BusinessActionInvocation invocation) {
    ResolvedAction resolved = resolve(invocation);
    requireEnabled(resolved);
    requireAuthorized(resolved);
    if (!matchesGeneratedContract(resolved.definition, invocation)) {
      throw new BusinessException("业务 Action SDK 契约已变化，请重新生成类型支持");
    }
    BusinessActionPreconditionPlan precondition = resolved.modelBehavior.preconditionPlan(resolved.definition.getName());
    precondition.requireValid();
    if (resolved.definition.getAuthorization() != null) {
      throw new BusinessException("业务 Action authorization 尚无执行语义，请使用正式 Authority");
    }
    PreparedBusinessActionInput input = prepareInput(resolved, invocation.input());
    BusinessActionExecutionScope.Execution parent = BusinessActionExecutionScope.current();
    if (parent != null && parent.readOnly() && !Boolean.TRUE.equals(resolved.definition.getReadOnly())) {
      throw new BusinessException("只读业务 Action 不能嵌套调用可写业务 Action");
    }
    ResolvedImplementation implementation = requireImplementation(resolved.definition);
    Map<String, Object> request = currentRequest(invocation.input(), parent);
    if (implementation.binding().isRequiresHttpRequest() && request == null) {
      throw new BusinessException("业务 Action 需要真实 HTTP 请求上下文");
    }

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    // Platform audit completion joins this transaction; business read-only semantics are enforced by the write guard.
    String executionId = UUID.randomUUID().toString();
    String implementationRef = implementation.implementationRef();
    String modelRevision = implementation.flowModel() == null
        ? resolved.modelBehavior.getModelRevision()
        : resolved.modelBehavior.getModelRevision() + ":" + implementation.flowModel().getRevision();
    String actorRef = CurrentSecurityContextHolder.findAuthentication()
        .map(OuroborosAuthentication::getPrincipal)
        .map(String::valueOf)
        .orElseGet(() -> CurrentSecurityContextHolder.findIdentity()
            .map(SecurityIdentity::getPrincipal)
            .map(String::valueOf)
            .orElse(null));
    BusinessActionExecutionScope.Execution execution = new BusinessActionExecutionScope.Execution(
        executionId, parent == null ? null : parent.executionId(), resolved.modelBehavior.getModelFullName(),
        modelRevision, resolved.definition.getName(), implementationRef,
        actorRef, request, Boolean.TRUE.equals(resolved.definition.getReadOnly()),
        resolved.definition.getEmits().stream()
            .filter(event -> event != null && event.getModelFullName() != null && event.getEventName() != null)
            .map(event -> new BusinessEventIdentity(event.getModelFullName(), event.getEventName()))
            .collect(java.util.stream.Collectors.toSet()),
        Instant.now(), resolved.authorityName);
    AtomicBoolean auditCompletionScheduled = new AtomicBoolean();
    DataModelExecutionContext.withPlatformWrite(() -> {
      auditRecorder.begin(execution.auditRecord(null, "STARTED"));
      return null;
    });
    try {
      ActionResult actionResult = BusinessActionExecutionScope.with(execution, () -> transaction.execute(status -> {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int transactionStatus) {
              if (transactionStatus != TransactionSynchronization.STATUS_COMMITTED) {
                DataModelExecutionContext.withPlatformWrite(() -> {
                  recordAudit(execution.auditRecord(Instant.now(), transactionResult(transactionStatus)));
                  return null;
                });
              }
            }
          });
          auditCompletionScheduled.set(true);
        }
        try {
          var resources = BusinessTransactionResources.capture(transactionManager, status);
          Object result = DataModelExecutionContext.withWriteGuard(
              resources, () -> DataModelExecutionContext.withExecution(
              Boolean.TRUE.equals(resolved.definition.getReadOnly()), execution,
              () -> {
                var objectStates = BusinessActionObjectStates.resolve(resolved.definition.getInputSchema(), input,
                    resolved.modelBehavior.getModelFullName(), aliasedAuthority(resolved) ? resolved.authorityName : null);
                objectStates.requireReadableFields(precondition);
                if (!Boolean.TRUE.equals(resolved.definition.getReadOnly()) && precondition.requiresObjectStates()) {
                  objectStates.lockAndRevalidate(resources);
                }
                precondition.requireSatisfied(input, objectStates.values());
                Object bindingResult = invokeBinding(resolved, implementation, invocation, input, objectStates.values(), request);
                validateOutput(resolved.definition, bindingResult);
                DataModelExecutionContext.assertMutationTrackingHealthy();
                mutationEvents.emit(resolved.modelBehavior.getModelFullName(),
                    resolved.modelBehavior.mutationEventPlan(resolved.definition.getName()), execution.ownMutations());
                return bindingResult;
              }));
          if (status.isRollbackOnly()) {
            throw new BusinessException("业务 Action 所属事务已标记回滚");
          }
          DataModelExecutionContext.withPlatformWrite(() -> {
            recordAudit(execution.auditRecord(Instant.now(), "COMMITTED"));
            return null;
          });
          return new ActionResult(result);
        } catch (RuntimeException error) {
          status.setRollbackOnly();
          execution.recordFailure(error);
          if (error instanceof BusinessException) {
            throw error;
          }
          throw new BusinessException("业务 Action 执行失败", error);
        }
      }));
      execution.mergeInto(parent);
      return actionResult == null ? null : actionResult.result;
    } catch (BusinessException error) {
      if (!auditCompletionScheduled.get()) {
        recordAudit(execution.auditRecord(Instant.now(), execution.failureType() == null ? "UNKNOWN" : "ROLLED_BACK"));
      }
      throw error;
    } catch (RuntimeException error) {
      if (!auditCompletionScheduled.get()) {
        recordAudit(execution.auditRecord(Instant.now(), execution.failureType() == null ? "UNKNOWN" : "ROLLED_BACK"));
      }
      throw new BusinessException("业务 Action 执行失败", error);
    }
  }

  @Override
  public BusinessActionAvailability checkAvailability(BusinessActionInvocation invocation) {
    try {
      return BusinessModelPublication.withPublishedContext(() -> checkAvailabilityInPublishedContext(invocation));
    } catch (BusinessException unavailable) {
      return new BusinessActionAvailability(BusinessActionAvailability.Status.BLOCKED,
          List.of("业务模型发布快照不可用"), BusinessActionAvailability.Blockage.CONTRACT);
    }
  }

  @Override
  public List<BusinessActionDescription> describe(String modelFullName) {
    return BusinessModelPublication.withPublishedContext(() -> behaviorProvider.getBusinessModelBehavior(modelFullName)
        .map(behavior -> behavior.getActions().values().stream()
            .filter(action -> isAuthorized(new ResolvedAction(behavior, action,
                behavior.executionAuthorityName(action.getName()))))
            .map(action -> new BusinessActionDescription(behavior.getModelFullName(), behavior.getModelRevision(),
                action.getName(), action.getTitle(), action.getDescription(),
                Boolean.TRUE.equals(action.getIsEnabled()), Boolean.TRUE.equals(action.getReadOnly())))
            .toList())
        .orElseGet(List::of));
  }

  @EventListener
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void validatePublishedBindings(OuroborosContextRefreshedEvent event) {
    List<String> errors = new ArrayList<>();
    behaviorProvider.getBusinessModelBehaviorMap().values().forEach(model ->
        model.getActions().values().stream().filter(action -> Boolean.TRUE.equals(action.getIsEnabled()))
            .forEach(action -> {
              try {
                if (resolveImplementationIfAvailable(action).isEmpty()) {
                  errors.add(model.getModelFullName() + ".actions." + action.getName() + ".binding-unavailable");
                }
              } catch (RuntimeException failure) {
                errors.add(model.getModelFullName() + ".actions." + action.getName() + ": " + failure.getMessage());
              }
            }));
    if (!errors.isEmpty()) {
      throw new BusinessException("业务 Action 绑定验证失败：" + String.join("; ", errors));
    }
  }

  private BusinessActionAvailability checkAvailabilityInPublishedContext(BusinessActionInvocation invocation) {
    ResolvedAction resolved;
    try {
      resolved = resolve(invocation);
    } catch (BusinessException error) {
      return contractBlocked("Action 不可用");
    }
    if (!isAuthorized(resolved)) {
      return blocked("Action 不可用");
    }
    if (!Boolean.TRUE.equals(resolved.definition.getIsEnabled())) {
      return contractBlocked("Action 已禁用");
    }
    if (!matchesGeneratedContract(resolved.definition, invocation)) {
      return contractBlocked("Action SDK 契约已变化，请重新生成类型支持");
    }
    var precondition = resolved.modelBehavior.preconditionPlan(resolved.definition.getName());
    if (!precondition.isValid() || resolved.definition.getAuthorization() != null) {
      return contractBlocked("Action 前置条件或授权配置无效");
    }
    if (resolveImplementationIfAvailable(resolved.definition).isEmpty()) {
      return contractBlocked("Action 实现不可用");
    }
    PreparedBusinessActionInput input;
    try {
      input = resolved.modelBehavior.inputPlan(resolved.definition.getName()).prepare(invocation.input());
    } catch (RuntimeException invalidInput) {
      return needsInput("输入不符合 Action 契约");
    }
    try {
      ResolvedImplementation implementation = requireImplementation(resolved.definition);
      if (implementation.binding().isRequiresHttpRequest()
          && currentRequest(invocation.input(), BusinessActionExecutionScope.current()) == null) {
        return needsInput("需要真实 HTTP 请求上下文");
      }
      var states = BusinessActionObjectStates.resolve(resolved.definition.getInputSchema(), input,
          resolved.modelBehavior.getModelFullName(), aliasedAuthority(resolved) ? resolved.authorityName : null);
      states.requireReadableFields(precondition);
      precondition.requireSatisfied(input, states.values());
    } catch (BusinessActionPreconditionException blocked) {
      return new BusinessActionAvailability(BusinessActionAvailability.Status.BLOCKED,
          List.of(blocked.getMessage()), BusinessActionAvailability.Blockage.PRECONDITION);
    } catch (BusinessException error) {
      return blocked("业务对象不可用");
    }
    return new BusinessActionAvailability(BusinessActionAvailability.Status.EXECUTABLE, List.of());
  }

  private ResolvedAction resolve(BusinessActionInvocation invocation) {
    Objects.requireNonNull(invocation, "invocation must not be null");
    BusinessModelBehavior behavior = behaviorProvider.getBusinessModelBehavior(invocation.modelFullName())
        .orElseThrow(() -> new BusinessException("业务模型或 Action 不存在"));
    ActionDefinition action = behavior.getActions().get(invocation.actionName());
    if (action == null) {
      throw new BusinessException("业务模型或 Action 不存在");
    }
    return new ResolvedAction(behavior, action, behavior.executionAuthorityName(action.getName()));
  }

  private static boolean matchesGeneratedContract(ActionDefinition action, BusinessActionInvocation invocation) {
    return invocation.expectedContractFingerprint() == null || invocation.expectedContractFingerprint().equals(
        com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.actionFingerprint(action));
  }

  private static BusinessActionAvailability contractBlocked(String reason) {
    return new BusinessActionAvailability(BusinessActionAvailability.Status.BLOCKED, List.of(reason),
        BusinessActionAvailability.Blockage.CONTRACT);
  }

  private void requireEnabled(ResolvedAction resolved) {
    if (!Boolean.TRUE.equals(resolved.definition.getIsEnabled())) {
      throw new BusinessException("业务 Action 已禁用");
    }
  }

  private void requireAuthorized(ResolvedAction resolved) {
    if (!isAuthorized(resolved)) {
      throw new BusinessException("业务 Action 未授权");
    }
  }

  private boolean isAuthorized(ResolvedAction resolved) {
    return BusinessBehaviorSecurity.hasAuthority(resolved.authorityName);
  }

  private PreparedBusinessActionInput prepareInput(ResolvedAction resolved, BusinessActionInput<?, ?> input) {
    try {
      return resolved.modelBehavior.inputPlan(resolved.definition.getName()).prepare(input);
    } catch (BusinessException invalid) {
      throw new BusinessException(resolved.modelBehavior.getModelFullName() + "." + resolved.definition.getName()
          + "：" + invalid.getMessage(), invalid);
    }
  }

  private void validateOutput(ActionDefinition action, Object result) {
    if (result instanceof com.ouroboros.util.FileResult file) {
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        var failure = new BusinessException("Action 文件结果要求事务同步以确保失败时释放资源");
        try { file.close(); } catch (java.io.IOException cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
      }
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != TransactionSynchronization.STATUS_COMMITTED) {
            try { file.close(); } catch (java.io.IOException error) { throw new BusinessException("回滚后释放 Action 文件失败", error); }
          }
        }
      });
    }
    BusinessActionOutputs.validate(action.getOutputSchema(), result);
  }

  private ResolvedImplementation requireImplementation(ActionDefinition action) {
    return resolveImplementationIfAvailable(action)
        .orElseThrow(() -> new BusinessException("业务 Action 实现不可用"));
  }

  private Optional<ResolvedImplementation> resolveImplementationIfAvailable(ActionDefinition action) {
    ActionBinding binding = action.getBinding();
    if (binding == null) {
      return Optional.empty();
    }
    if ("logicflow".equalsIgnoreCase(binding.getKind())) {
      if (binding.getFlowName() == null || binding.getFlowName().isBlank()) {
        return Optional.empty();
      }
      try {
        return FlowModelCenter.getFlowModel(binding.getFlowName())
            .filter(flowModel -> Boolean.TRUE.equals(flowModel.isEnabled()) && Boolean.TRUE.equals(flowModel.isValid()))
            .flatMap(flowModel -> {
              boolean hasAsyncNode = flowModel.getNodes().stream()
                  .anyMatch(node -> Boolean.TRUE.equals(node.isAsync()));
              if (hasAsyncNode) {
                return Optional.empty();
              }
              return Optional.of(new ResolvedImplementation(binding, flowModel, null,
                  "logicflow:" + binding.getFlowName() + "@" + flowModel.getRevision()));
            });
      } catch (RuntimeException unavailableCatalog) {
        return Optional.empty();
      }
    }
    if ("handler".equalsIgnoreCase(binding.getKind())) {
      return findHandler(binding.getHandlerName())
          .filter(com.ouroboros.businessmodel.BusinessBindingCapabilities::isSynchronous)
          .map(handler -> {
            handler.validateContract(action);
            return handler;
          })
          .map(handler -> new ResolvedImplementation(binding, null, handler,
              "handler:" + binding.getHandlerName() + ":" + handler.getImplementationRef()));
    }
    return Optional.empty();
  }

  private Object invokeBinding(ResolvedAction resolved,
                               ResolvedImplementation implementation,
                               BusinessActionInvocation invocation,
                               PreparedBusinessActionInput input,
                               Map<String, Record> objectStates,
                               Map<String, Object> request) {
    ActionBinding binding = implementation.binding();
    SecurityIdentity actor = CurrentSecurityContextHolder.findIdentity().orElse(null);
    BusinessActionExecutionScope.Execution execution = BusinessActionExecutionScope.current();
    BusinessActionContext context = new BusinessActionContext(
        invocation.modelFullName(), invocation.actionName(), resolved.authorityName,
        execution == null ? null : execution.executionId(),
        execution == null ? null : execution.parentExecutionId(),
        objectStates, request, actor, Boolean.TRUE.equals(resolved.definition.getReadOnly()));
    return DataModelExecutionContext.withReadOnly(context.readOnly(), () ->
        BusinessBehaviorSecurity.withAuthority(resolved.authorityName,
            aliasedAuthority(resolved) && !objectStates.isEmpty() ? resolved.modelBehavior.getModelFullName() : null, () -> {
      invocationValidators.forEach(validator -> validator.validate(context, input));
      if ("logicflow".equalsIgnoreCase(binding.getKind())) {
        Map<String, Object> contextValues = contextValues(context);
        Map<String, Object> mappedInput = resolved.modelBehavior.inputPlan(resolved.definition.getName())
            .flowInput(input, contextValues);
        Try<Object> result = logicFlowExecutionFacade.execute(implementation.flowModel(), List.of(mappedInput));
        if (result.isFailure()) {
          throw new BusinessException("业务 Action 执行失败", result.getCause());
        }
        Object output = result.get();
        var outputPlan = resolved.modelBehavior.outputPlan(resolved.definition.getName());
        if (outputPlan == null) { return output; }
        Map<String, Object> outputContext = new LinkedHashMap<>();
        outputContext.put("result", output);
        return outputPlan.evaluate(outputContext);
      }
      BusinessActionHandler handler = implementation.handler();
      if (handler == null) {
        throw new BusinessException("业务 Action 实现不可用");
      }
      return handler.handle(context, input);
        }));
  }

  private boolean aliasedAuthority(ResolvedAction resolved) {
    return !resolved.authorityName.equalsIgnoreCase(resolved.modelBehavior.getModelFullName() + "." + resolved.definition.getName());
  }

  private Map<String, Object> currentRequest(BusinessActionInput<?, ?> input,
                                               BusinessActionExecutionScope.Execution parent) {
    if (!BusinessActionExecutionScope.isRequestInheritanceAllowed()) {
      return null;
    }
    if (input instanceof BusinessActionRequestProvider provider) {
      return provider.currentRequest();
    }
    return parent == null ? requestProvider.currentRequest() : parent.request();
  }

  private static Map<String, Object> contextValues(BusinessActionContext context) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("modelFullName", context.modelFullName());
    values.put("actionName", context.actionName());
    values.put("authorityName", context.authorityName());
    values.put("executionId", context.executionId());
    values.put("parentExecutionId", context.parentExecutionId());
    values.put("actor", context.actor() == null ? null : context.actor().getPrincipal());
    values.put("readOnly", context.readOnly());
    values.put("objectStates", context.objectStates());
    if (context.request() != null) {
      values.put("request", context.request());
    }
    return Collections.unmodifiableMap(values);
  }

  private Optional<BusinessActionHandler> findHandler(String name) {
    Map<String, BusinessActionHandler> handlers = indexHandlers(handlerSupplier.get());
    return Optional.ofNullable(handlers.get(normalize(name)));
  }

  private static Map<String, BusinessActionHandler> indexHandlers(List<BusinessActionHandler> handlers) {
    Map<String, BusinessActionHandler> result = new TreeMap<>();
    if (handlers == null) {
      return Collections.unmodifiableMap(result);
    }
    handlers.stream().filter(Objects::nonNull).forEach(handler -> {
      String name = normalize(handler.getName());
      if (name.isEmpty()) {
        throw new IllegalArgumentException("BusinessActionHandler name must not be blank");
      }
      if (result.putIfAbsent(name, handler) != null) {
        throw new IllegalArgumentException("Duplicate BusinessActionHandler name: " + handler.getName());
      }
    });
    return Collections.unmodifiableMap(result);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static BusinessActionAvailability blocked(String reason) {
    return new BusinessActionAvailability(BusinessActionAvailability.Status.BLOCKED, List.of(reason));
  }

  private static BusinessActionAvailability needsInput(String reason) {
    return new BusinessActionAvailability(BusinessActionAvailability.Status.NEEDS_INPUT, List.of(reason));
  }

  private void recordAudit(BusinessActionExecutionAuditRecord audit) {
    auditRecorder.record(audit);
  }

  private static String transactionResult(int status) {
    if (status == TransactionSynchronization.STATUS_COMMITTED) {
      return "COMMITTED";
    }
    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
      return "ROLLED_BACK";
    }
    return "UNKNOWN";
  }

  private record ActionResult(Object result) {
  }

  private record ResolvedAction(BusinessModelBehavior modelBehavior,
                                ActionDefinition definition,
                                String authorityName) {
  }

  private record ResolvedImplementation(ActionBinding binding,
                                        FlowModel flowModel,
                                        BusinessActionHandler handler,
                                        String implementationRef) {
  }


}
