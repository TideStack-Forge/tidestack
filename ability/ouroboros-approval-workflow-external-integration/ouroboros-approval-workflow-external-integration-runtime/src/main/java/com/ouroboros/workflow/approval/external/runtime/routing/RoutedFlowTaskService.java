package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalFormSnapshot;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalTaskPage;
import com.ouroboros.workflow.approval.external.ExternalTaskQuery;
import com.ouroboros.workflow.approval.external.ExternalTaskReader;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionRequest;
import com.ouroboros.workflow.approval.external.runtime.assignment.ExternalPendingAssignmentService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

import io.vavr.control.Try;

/**
 * 流程任务路由门面：按 localTaskId 解析外部任务绑定后路由到外部适配器；
 * 未命中绑定的任务与内置能力（历史、候选人写等）委托内置引擎。
 *
 * <p>已知限制（Phase 2 记录）：跨引擎待办合并禁止 offset 拼接——各源拉满
 * {@code limit+offset} 后统一排序截断；任一源 {@code hasMore} 时结果尾部可能缺页，
 * 待办中心改造后引入稳定游标。
 */
@Service
@Primary
public class RoutedFlowTaskService implements FlowTaskService {

  private static final Logger logger = LoggerFactory.getLogger(RoutedFlowTaskService.class);

  private final FlowTaskService internal;
  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final ExternalTaskBindingService taskBindingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final WorkflowEngineMappingService mappingService;
  private final ExternalUserIdentityResolver identityResolver;
  private final WorkflowScopeResolver scopeResolver;
  private final ExternalPendingAssignmentService pendingAssignmentService;
  private final ExternalActionExecutionService actionExecutionService;
  private final ExternalBindingRouteResolver bindingRouteResolver;

  @Autowired
  public RoutedFlowTaskService(
      ObjectProvider<BuiltInFlowTaskService> internalProvider,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalTaskBindingService taskBindingService,
      ExternalInstanceBindingService instanceBindingService,
      WorkflowEngineMappingService mappingService,
      ExternalUserIdentityResolver identityResolver,
      WorkflowScopeResolver scopeResolver,
      ExternalPendingAssignmentService pendingAssignmentService,
      ExternalActionExecutionService actionExecutionService) {
    this.internal = internalProvider.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到内置流程任务服务 Bean"));
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.taskBindingService = taskBindingService;
    this.instanceBindingService = instanceBindingService;
    this.mappingService = mappingService;
    this.identityResolver = identityResolver;
    this.scopeResolver = scopeResolver;
    this.pendingAssignmentService = pendingAssignmentService;
    this.actionExecutionService = actionExecutionService;
    this.bindingRouteResolver = new ExternalBindingRouteResolver(mappingService);
  }

  private String resolveScope() {
    return scopeResolver.resolve();
  }

  /**
   * 读路径的作用域：未装配 {@link WorkflowScopeResolver} 时返回空，表示平台未启用外部协作。
   *
   * <p>平台默认装配的是 fail-closed 的 {@link DefaultWorkflowScopeResolver}，若读路径直接透传它的异常，
   * 连内置审批流的待办/实例列表都会整体失败。此处的容错只针对"未装配"这一种情况；
   * 已装配的解析器判定无权访问时抛出的其他异常仍然向上传播。
   */
  private Optional<String> resolveScopeForRead() {
    try {
      return Optional.of(scopeResolver.resolve());
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      return Optional.empty();
    }
  }

  @Override
  public <T> Try<Void> doAction(
      String taskId, String actionType, String userId, String comment,
      Map<String, Object> formData, T payload) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty()) {
      return internal.doAction(taskId, actionType, userId, comment, formData, payload);
    }
    ExternalTaskBinding task = binding.get();
    ExternalBindingRouteResolver.Decision route;
    try {
      route = bindingRouteResolver.resolve(task.getMappingId(), task.getMappingVersion());
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    if (route.isBuiltIn()) {
      return internal.doAction(taskId, actionType, userId, comment, formData, payload);
    }
    Try<Void> authorization = authorizeExternalTask(task, userId, route.mapping());
    if (authorization.isFailure()) {
      return Try.failure(authorization.getCause());
    }
    WorkflowEngineMapping mapping = route.mapping();
    String externalAction = parseActionMappings(mapping).get(actionType);
    if (externalAction == null) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_NOT_MAPPED + "] 规范动作未配置外部动作映射: " + actionType));
    }
    if (WorkflowEngineAdapterRegistry.EXECUTION_LINK.equals(
        registry.executionModeOf(mapping.getAdapterId()))) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_INTERACTION_REQUIRED + "] 需跳转外部处理，外部地址: "
              + task.getExternalUrl()));
    }
    Optional<ExternalInstanceBinding> instanceBinding = instanceBindingService.findByLocalId(task.getLocalInstanceId());
    ExternalInstanceRef instanceRef = new ExternalInstanceRef(
        instanceBinding.map(ExternalInstanceBinding::getExternalInstanceId).orElse(null), task.getConnectionId());
    Map<String, Object> payloadMap = payload instanceof Map<?, ?> map ? toStringObjectMap(map) : null;
    String targetStatus = switch (actionType) {
      case "approve" -> ExternalTaskBinding.STATUS_APPROVED;
      case "reject" -> ExternalTaskBinding.STATUS_REJECTED;
      default -> task.getStatus();
    };
    return actionExecutionService.executeTask(task, new ExternalActionRequest(
        actionType, externalAction, userId, comment, formData, payloadMap,
        task.getMappingVersion(), instanceRef,
        new ExternalTaskRef(task.getExternalTaskId(), instanceRef.externalInstanceId(), task.getConnectionId())),
        targetStatus);
  }

  @Override
  public <T> T getPayload(String taskId, Class<T> clazz) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty()) {
      return internal.getPayload(taskId, clazz);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getPayload(taskId, clazz);
    }
    Optional<Map<String, Object>> raw = readRaw(binding.get());
    return raw.map(map -> extract(clazz, map.get("payload"))).orElse(null);
  }

  @Override
  public Map<String, Object> getTaskFormData(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty()) {
      return internal.getTaskFormData(taskId);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getTaskFormData(taskId);
    }
    Optional<Map<String, Object>> raw = readRaw(binding.get());
    Object formData = raw.map(map -> map.get("formData")).orElse(null);
    return formData instanceof Map<?, ?> map ? toStringObjectMap(map) : Map.of();
  }

  @Override
  public String getComment(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty()) {
      return internal.getComment(taskId);
    }
    if (bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getComment(taskId);
    }
    Optional<Map<String, Object>> raw = readRaw(binding.get());
    Object comment = raw.map(map -> map.get("comment")).orElse(null);
    return comment == null ? null : String.valueOf(comment);
  }

  @Override
  public void setTaskAssignee(String taskId, String userId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      internal.setTaskAssignee(taskId, userId);
      return;
    }
    throw new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 外部任务不支持本地改派: " + taskId);
  }

  @Override
  public Try<SchemaNode> getTaskForm(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty()) {
      return internal.getTaskForm(taskId);
    }
    ExternalTaskBinding task = binding.get();
    ExternalBindingRouteResolver.Decision route;
    try {
      route = bindingRouteResolver.resolve(task.getMappingId(), task.getMappingVersion());
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    if (route.isBuiltIn()) {
      return internal.getTaskForm(taskId);
    }
    WorkflowEngineMapping mapping = route.mapping();
    if (WorkflowEngineAdapterRegistry.EXECUTION_LINK.equals(
        registry.executionModeOf(mapping.getAdapterId()))) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_INTERACTION_REQUIRED + "] 需跳转外部处理，外部地址: "
              + task.getExternalUrl()));
    }
    ExternalTaskReader reader;
    try {
      reader = adapterResolver.resolvePort(task.getAdapterId(), task.getConnectionId(), ExternalTaskReader.class);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    String externalInstanceId = resolveExternalInstanceId(task);
    if (externalInstanceId == null) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_BINDING_MISSING + "] 实例绑定缺失: "
              + task.getLocalInstanceId()));
    }
    Try<ExternalFormSnapshot> snapshot = reader.getTaskForm(
        new ExternalTaskRef(task.getExternalTaskId(), externalInstanceId, task.getConnectionId()));
    if (snapshot.isFailure()) {
      return Try.failure(snapshot.getCause());
    }
    return Try.success(buildFormSchema(snapshot.get()));
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId) {
    return mergeTaskLists(userId, null, 0, 100);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, Integer offset, Integer limit) {
    return mergeTaskLists(userId, null, offset == null ? 0 : offset, limit == null ? 100 : limit);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey) {
    return mergeTaskLists(userId, flowKey, 0, 100);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    return mergeTaskLists(userId, flowKey, offset == null ? 0 : offset, limit == null ? 100 : limit);
  }

  private List<FlowTaskDetail> mergeTaskLists(String userId, String flowKey, int offset, int limit) {
    List<FlowTaskDetail> merged = new ArrayList<>();
    merged.addAll(internal.getTaskList(userId, flowKey, 0, offset + limit));
    merged.addAll(aggregateExternalTasks(userId, flowKey, offset + limit));
    merged.sort(Comparator.comparing(FlowTaskDetail::getStartTime,
        Comparator.nullsLast(Comparator.reverseOrder())));
    if (merged.size() <= offset) {
      return List.of();
    }
    int toIndex = Math.min(merged.size(), offset + limit);
    return new ArrayList<>(merged.subList(offset, toIndex));
  }

  /**
   * 外部待办聚合：遍历 ACTIVE 且 FULL_PROCESS、适配器含 EXTERNAL_TASK_QUERY 的映射，
   * 按稳定游标续拉（Phase 4 M26，修复 L1008 多源缺页）：源返回 hasMore=true 且携带
   * nextCursor 时继续以该游标查询，直至源耗尽或已取得本次合并所需候选；
   * 无 nextCursor 的 hasMore（无游标能力，按 L456 截断语义）单页即止。
   * 对外契约不变（{@code FlowTaskService.getTaskList} 仍为 List，hasMore 在聚合器内部消费为续拉）。
   */
  private static final int EXTERNAL_COUNT_PAGE_SIZE = 1000;

  private List<FlowTaskDetail> aggregateExternalTasks(String userId, String flowKey, int limit) {
    return resolveScopeForRead()
        .map(scope -> aggregateExternalTasks(userId, flowKey, limit, scope))
        .orElseGet(List::of);
  }

  private List<FlowTaskDetail> aggregateExternalTasks(
      String userId, String flowKey, int limit, String scope) {
    List<FlowTaskDetail> external = new ArrayList<>();
    for (WorkflowEngineMapping mapping : externalTaskMappings(scope, flowKey)) {
      ExternalTaskReader reader = resolveTaskReader(mapping);
      String cursor = null;
      Set<String> seenCursors = new HashSet<>();
      int sourceCount = 0;
      while (sourceCount < limit) {
        Try<ExternalTaskPage> pageTry = reader.queryTasks(
            new ExternalTaskQuery(userId, mapping.getConnectionId(), cursor, limit));
        if (pageTry.isFailure()) {
          throw new BusinessException(
              "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部待办查询失败: "
                  + mapping.getAdapterId() + "/" + mapping.getConnectionId(), pageTry.getCause());
        }
        ExternalTaskPage page = pageTry.get();
        for (ExternalTaskSnapshot snapshot : page.tasks()) {
          FlowTaskDetail detail = toTaskDetail(mapping, snapshot);
          if (detail != null) {
            external.add(detail);
            sourceCount++;
          }
        }
        if (!page.hasMore() || page.nextCursor() == null) {
          break;
        }
        if (!seenCursors.add(page.nextCursor())) {
          throw new BusinessException(
              "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部待办游标重复: "
                  + mapping.getAdapterId() + "/" + mapping.getConnectionId());
        }
        cursor = page.nextCursor();
      }
    }
    return external;
  }

  /**
   * 参与待办聚合的映射：ACTIVE + EXTERNAL_ENGINE + 派生模式为 FULL_PROCESS。
   *
   * <p>不再额外过滤 {@code EXTERNAL_TASK_QUERY}：FULL_PROCESS 的定义本身就是
   * "同时具备 EXTERNAL_INSTANCE_QUERY 与 EXTERNAL_TASK_QUERY 两个端口能力"
   * （见 {@link WorkflowEngineAdapterRegistry#integrationModeOf}），重复过滤只会掩盖
   * 描述声明与端口实现之间的不一致。
   */
  private List<WorkflowEngineMapping> externalTaskMappings(String scope, String flowKey) {
    return mappingService.findActiveByScope(scope).stream()
        .filter(mapping -> WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority()))
        .filter(mapping -> WorkflowEngineAdapterRegistry.MODE_FULL_PROCESS
            .equals(registry.integrationModeOf(mapping.getAdapterId())))
        .filter(mapping -> flowKey == null || flowKey.equals(mapping.getFlowKey()))
        .toList();
  }

  private ExternalTaskReader resolveTaskReader(WorkflowEngineMapping mapping) {
    return adapterResolver.resolvePort(mapping, ExternalTaskReader.class);
  }

  private FlowTaskDetail toTaskDetail(WorkflowEngineMapping mapping, ExternalTaskSnapshot snapshot) {
    if (snapshot == null || snapshot.externalTaskId() == null) {
      return null;
    }
    String discoveredStatus = discoveredTaskStatus(snapshot.status());
    if (discoveredStatus == null) {
      logger.warn("外部待办状态不受支持，跳过本地任务绑定: adapterId={}, connectionId={}, externalTaskId={}, status={}",
          mapping.getAdapterId(), mapping.getConnectionId(), snapshot.externalTaskId(), snapshot.status());
      return null;
    }
    if (isTerminalTaskStatus(discoveredStatus)) {
      logger.debug("外部待办已进入终态，跳过本地任务绑定: adapterId={}, connectionId={}, externalTaskId={}, status={}",
          mapping.getAdapterId(), mapping.getConnectionId(), snapshot.externalTaskId(), snapshot.status());
      return null;
    }
    Optional<String> assignee = resolveAssignee(snapshot, mapping, true);
    if (assignee.isEmpty()) {
      return null;
    }
    Optional<ExternalTaskBinding> existing = taskBindingService.findByExternalTask(
        mapping.getAdapterId(), mapping.getConnectionId(), snapshot.externalTaskId());
    ExternalTaskBinding binding = existing.orElseGet(() -> createDiscoveredTaskBinding(
        mapping, snapshot, assignee.get(), discoveredStatus).orElse(null));
    if (binding == null) {
      return null;
    }
    if (existing.isPresent() && !assignee.get().equals(binding.getAssignee())) {
      Try<Boolean> synced = taskBindingService.syncAssignee(binding.getLocalTaskId(), assignee.get());
      if (synced.isFailure()) {
        throw new BusinessException(
            "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
                + "] 同步外部任务处理人失败: localTaskId=" + binding.getLocalTaskId(),
            synced.getCause());
      }
    }
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId(binding.getLocalTaskId());
    detail.setFlowKey(mapping.getFlowKey());
    detail.setFlowName(mapping.getFlowKey());
    detail.setInstanceId(binding.getLocalInstanceId());
    detail.setAssignee(assignee.get());
    return detail;
  }

  private Optional<ExternalTaskBinding> createDiscoveredTaskBinding(
      WorkflowEngineMapping mapping,
      ExternalTaskSnapshot snapshot,
      String assignee,
      String discoveredStatus) {
    String externalInstanceId = rawValue(snapshot, "externalInstanceId");
    if (externalInstanceId == null) {
      logger.warn("外部待办缺少实例标识，跳过本地任务绑定: adapterId={}, connectionId={}, externalTaskId={}",
          mapping.getAdapterId(), mapping.getConnectionId(), snapshot.externalTaskId());
      return Optional.empty();
    }
    Optional<ExternalInstanceBinding> instanceBinding = instanceBindingService.findByExternalInstance(
        mapping.getAdapterId(), mapping.getConnectionId(), externalInstanceId);
    if (instanceBinding.isEmpty()) {
      logger.warn("外部待办实例绑定缺失，跳过本地任务绑定: adapterId={}, connectionId={}, externalInstanceId={}, externalTaskId={}",
          mapping.getAdapterId(), mapping.getConnectionId(), externalInstanceId, snapshot.externalTaskId());
      return Optional.empty();
    }
    ExternalInstanceBinding instance = instanceBinding.get();
    if (!mapping.getId().equals(instance.getMappingId())
        || !mapping.getVersion().equals(instance.getMappingVersion())
        || !mapping.getScope().equals(instance.getScope())) {
      logger.warn("外部待办实例绑定路由不一致，跳过本地任务绑定: adapterId={}, connectionId={}, externalInstanceId={}, taskMapping={}@{}, instanceMapping={}@{}",
          mapping.getAdapterId(), mapping.getConnectionId(), externalInstanceId,
          mapping.getId(), mapping.getVersion(), instance.getMappingId(), instance.getMappingVersion());
      return Optional.empty();
    }
    ExternalTaskBinding requested = new ExternalTaskBinding();
    requested.setLocalInstanceId(instance.getLocalInstanceId());
    requested.setScope(instance.getScope());
    requested.setMappingId(instance.getMappingId());
    requested.setMappingVersion(instance.getMappingVersion());
    requested.setAdapterId(mapping.getAdapterId());
    requested.setConnectionId(mapping.getConnectionId());
    requested.setExternalTaskId(snapshot.externalTaskId());
    requested.setNodeId(snapshot.nodeId());
    requested.setAssignee(assignee);
    requested.setStatus(discoveredStatus);
    requested.setExternalUrl(snapshot.externalUrl());
    Try<ExternalTaskBinding> created = taskBindingService.create(requested);
    if (created.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
              + "] 创建外部任务绑定失败: externalTaskId=" + snapshot.externalTaskId(),
          created.getCause());
    }
    return Optional.of(created.get());
  }

  private String discoveredTaskStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    return switch (status.trim().toUpperCase(Locale.ROOT)) {
      case "CREATED" -> ExternalTaskBinding.STATUS_CREATED;
      case "RUNNING", "PROCESSING" -> ExternalTaskBinding.STATUS_PROCESSING;
      case "APPROVED" -> ExternalTaskBinding.STATUS_APPROVED;
      case "REJECTED" -> ExternalTaskBinding.STATUS_REJECTED;
      case "CANCELED", "CANCELLED" -> ExternalTaskBinding.STATUS_CANCELLED;
      default -> null;
    };
  }

  private boolean isTerminalTaskStatus(String status) {
    return ExternalTaskBinding.STATUS_APPROVED.equals(status)
        || ExternalTaskBinding.STATUS_REJECTED.equals(status)
        || ExternalTaskBinding.STATUS_CANCELLED.equals(status);
  }

  private String rawValue(ExternalTaskSnapshot snapshot, String field) {
    if (snapshot.raw() == null || snapshot.raw().get(field) == null) {
      return null;
    }
    String value = String.valueOf(snapshot.raw().get(field));
    return value.isBlank() ? null : value;
  }

  /** 外部任务处理人解析为平台用户；无法映射时进入待处理 assignment 队列。 */
  private Optional<String> resolveAssignee(
      ExternalTaskSnapshot snapshot, WorkflowEngineMapping mapping, boolean recordPending) {
    if (snapshot.assignee() == null || snapshot.assignee().isBlank()) {
      if (recordPending) {
        pendingAssignmentService.recordMissingMapping(
            mapping, snapshot, EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING);
      }
      return Optional.empty();
    }
    Optional<String> mapped = identityResolver.toPlatformUser(
        snapshot.assignee(), mapping.getAdapterId(), mapping.getConnectionId());
    if (mapped == null || mapped.isEmpty()) {
      if (recordPending) {
        pendingAssignmentService.recordMissingMapping(
            mapping, snapshot, EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING);
      }
      return Optional.empty();
    }
    return mapped;
  }

  @Override
  public Long getTaskCount(String userId) {
    return internal.getTaskCount(userId) + countExternalTasks(userId, null);
  }

  @Override
  public Long getTaskCount(String userId, String flowKey) {
    return internal.getTaskCount(userId, flowKey) + countExternalTasks(userId, flowKey);
  }

  private long countExternalTasks(String userId, String flowKey) {
    long total = 0L;
    Optional<String> scope = resolveScopeForRead();
    if (scope.isEmpty()) {
      return total;
    }
    for (WorkflowEngineMapping mapping : externalTaskMappings(scope.get(), flowKey)) {
      ExternalTaskReader reader = resolveTaskReader(mapping);
      String cursor = null;
      Set<String> seenCursors = new HashSet<>();
      while (true) {
        Try<ExternalTaskPage> pageTry = reader.queryTasks(
            new ExternalTaskQuery(userId, mapping.getConnectionId(), cursor, EXTERNAL_COUNT_PAGE_SIZE));
        if (pageTry.isFailure()) {
          throw new BusinessException(
              "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部待办计数查询失败: "
                  + mapping.getAdapterId() + "/" + mapping.getConnectionId(), pageTry.getCause());
        }
        ExternalTaskPage page = pageTry.get();
        total += page.tasks().stream()
            .filter(snapshot -> snapshot != null && snapshot.externalTaskId() != null)
            .filter(snapshot -> resolveAssignee(snapshot, mapping, false).isPresent())
            .count();
        if (!page.hasMore()) {
          break;
        }
        if (page.nextCursor() == null || page.nextCursor().isBlank()) {
          throw new BusinessException(
              "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
                  + "] 外部待办计数需要稳定 cursor: "
                  + mapping.getAdapterId() + "/" + mapping.getConnectionId());
        }
        if (!seenCursors.add(page.nextCursor())) {
          throw new BusinessException(
              "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部待办计数游标重复: "
                  + mapping.getAdapterId() + "/" + mapping.getConnectionId());
        }
        cursor = page.nextCursor();
      }
    }
    return total;
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, Integer offset, Integer limit) {
    return internal.getHistoryTaskList(userId, offset, limit);
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    return internal.getHistoryTaskList(userId, flowKey, offset, limit);
  }

  @Override
  public Long getHistoryTaskCount(String userId) {
    return internal.getHistoryTaskCount(userId);
  }

  @Override
  public Long getHistoryTaskCount(String userId, String flowKey) {
    return internal.getHistoryTaskCount(userId, flowKey);
  }

  @Override
  public List<String> getCandidateUsers(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getCandidateUsers(taskId);
    }
    return List.of();
  }

  @Override
  public void setCandidateUsers(String taskId, List<String> userIds) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      internal.setCandidateUsers(taskId, userIds);
      return;
    }
    throw new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 外部任务不支持本地候选人设置: " + taskId);
  }

  @Override
  public String getTaskAssignee(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getTaskAssignee(taskId);
    }
    return binding.get().getAssignee();
  }

  @Override
  public List<FlowTaskDetail> getAllActiveTaskList() {
    List<FlowTaskDetail> merged = new ArrayList<>(internal.getAllActiveTaskList());
    Optional<String> scope = resolveScopeForRead();
    if (scope.isEmpty()) {
      return merged;
    }
    for (ExternalTaskBinding binding : taskBindingService.findActive(scope.get())) {
    if (bindingRouteResolver.resolve(binding.getMappingId(), binding.getMappingVersion()).isBuiltIn()) {
        continue;
      }
      FlowTaskDetail detail = new FlowTaskDetail();
      detail.setId(binding.getLocalTaskId());
      detail.setInstanceId(binding.getLocalInstanceId());
      detail.setAssignee(binding.getAssignee());
      detail.setFlowKey(binding.getMappingId() == null ? null
          : mappingService.findByIdForSystem(binding.getMappingId())
              .map(WorkflowEngineMapping::getFlowKey).orElse(null));
      merged.add(detail);
    }
    return merged;
  }

  @Override
  public FlowTaskDetail getTaskDetail(String taskId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.getTaskDetail(taskId);
    }
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId(binding.get().getLocalTaskId());
    detail.setInstanceId(binding.get().getLocalInstanceId());
    detail.setAssignee(binding.get().getAssignee());
    return detail;
  }

  @Override
  public Try<Void> remind(String taskId, String userId) {
    Optional<ExternalTaskBinding> binding = taskBindingService.findByLocalId(taskId);
    if (binding.isEmpty() || bindingRouteResolver.resolve(binding.get().getMappingId(), binding.get().getMappingVersion()).isBuiltIn()) {
      return internal.remind(taskId, userId);
    }
    ExternalTaskBinding task = binding.get();
    Try<Void> authorization = authorizeExternalTask(task, userId,
        bindingRouteResolver.resolve(task.getMappingId(), task.getMappingVersion()).mapping());
    if (authorization.isFailure()) {
      return Try.failure(authorization.getCause());
    }
    String externalInstanceId = resolveExternalInstanceId(task);
    if (externalInstanceId == null) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_BINDING_MISSING + "] 实例绑定缺失: "
              + task.getLocalInstanceId()));
    }
    return actionExecutionService.executeTask(task, new ExternalActionRequest(
        "remind", "remind", userId, null, null, null, task.getMappingVersion(),
        new ExternalInstanceRef(externalInstanceId, task.getConnectionId()),
        new ExternalTaskRef(task.getExternalTaskId(), externalInstanceId, task.getConnectionId())),
        task.getStatus());
  }

  private Try<Void> authorizeExternalTask(
      ExternalTaskBinding task, String userId, WorkflowEngineMapping mapping) {
    if (userId == null || userId.isBlank()) {
      return Try.failure(permissionDenied("当前用户为空"));
    }
    String scope;
    try {
      scope = resolveScope();
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
    if (task.getScope() == null || !task.getScope().equals(scope)) {
      return Try.failure(permissionDenied("任务不属于当前作用域"));
    }
    if (!ExternalTaskBinding.STATUS_CREATED.equals(task.getStatus())
        && !ExternalTaskBinding.STATUS_PROCESSING.equals(task.getStatus())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 外部任务不处于可操作状态: "
              + task.getLocalTaskId() + "/" + task.getStatus()));
    }
    boolean externalAuthorization = registry.capabilitiesOf(task.getAdapterId())
        .contains(Capability.EXTERNAL_TASK_AUTHORIZATION);
    if (!externalAuthorization
        && (task.getAssignee() == null || task.getAssignee().isBlank()
            || !task.getAssignee().equals(userId))) {
      return Try.failure(permissionDenied("当前用户不是任务处理人"));
    }
    if (!externalAuthorization) {
      return Try.success(null);
    }
    String externalInstanceId = resolveExternalInstanceId(task);
    if (externalInstanceId == null) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_BINDING_MISSING + "] 实例绑定缺失: "
              + task.getLocalInstanceId()));
    }
    ExternalTaskReader reader;
    try {
      reader = adapterResolver.resolvePort(
          task.getAdapterId(), task.getConnectionId(), ExternalTaskReader.class);
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
    Try<ExternalTaskSnapshot> snapshotTry;
    try {
      snapshotTry = reader.getTask(new ExternalTaskRef(
          task.getExternalTaskId(), externalInstanceId, task.getConnectionId()));
    } catch (RuntimeException ex) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 外部任务权威状态查询失败: " + task.getLocalTaskId(), ex));
    }
    if (snapshotTry == null || snapshotTry.isFailure() || snapshotTry.get() == null) {
      Throwable cause = snapshotTry == null
          ? new IllegalStateException("外部任务读取端口返回 null Try")
          : snapshotTry.isFailure() ? snapshotTry.getCause()
              : new IllegalStateException("外部任务权威快照为空");
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 外部任务权威状态查询失败: " + task.getLocalTaskId(), cause));
    }
    ExternalTaskSnapshot snapshot = snapshotTry.get();
    String currentStatus = discoveredTaskStatus(snapshot.status());
    if (currentStatus == null) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] 外部任务状态不受支持: " + task.getLocalTaskId() + "/" + snapshot.status()));
    }
    if (isTerminalTaskStatus(currentStatus)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] 外部任务已进入终态: " + task.getLocalTaskId() + "/" + currentStatus));
    }
    Optional<String> currentAssignee = resolveAssignee(snapshot, mapping, false);
    if (currentAssignee.isEmpty() || !userId.equals(currentAssignee.get())) {
      return Try.failure(permissionDenied("当前用户不是外部任务权威快照中的处理人"));
    }
    return Try.success(null);
  }

  private BusinessException permissionDenied(String reason) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] " + reason);
  }

  /**
   * 按任务绑定的本地实例 ID 解析外部实例 ID（路由门面按绑定解析外部 ID，禁止泄漏本地 UUID）。
   */
  private String resolveExternalInstanceId(ExternalTaskBinding task) {
    return instanceBindingService.findByLocalId(task.getLocalInstanceId())
        .map(ExternalInstanceBinding::getExternalInstanceId)
        .orElse(null);
  }

  private Optional<Map<String, Object>> readRaw(ExternalTaskBinding binding) {
    ExternalTaskReader reader = adapterResolver.resolvePort(
        binding.getAdapterId(), binding.getConnectionId(), ExternalTaskReader.class);
    Try<ExternalTaskSnapshot> snapshot = reader.getTask(
        new ExternalTaskRef(binding.getExternalTaskId(), resolveExternalInstanceId(binding), binding.getConnectionId()));
    if (snapshot.isFailure()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 外部任务详情查询失败: "
              + binding.getExternalTaskId(),
          snapshot.getCause());
    }
    if (snapshot.get() == null) {
      return Optional.empty();
    }
    Map<String, Object> raw = snapshot.get().raw();
    return raw == null ? Optional.empty() : Optional.of(raw);
  }

  private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    map.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private <T> T extract(Class<T> clazz, Object value) {
    if (value == null) {
      return null;
    }
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    if (clazz == String.class) {
      return clazz.cast(String.valueOf(value));
    }
    return null;
  }

  private SchemaNode buildFormSchema(ExternalFormSnapshot snapshot) {
    FormNode schema = new FormNode();
    schema.setType("form");
    if (snapshot.fields() == null) {
      return schema;
    }
    for (Map.Entry<String, Object> entry : snapshot.fields().entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      FormItemNode property = new FormItemNode();
      property.setName(entry.getKey());
      property.setType(inferType(entry.getValue()));
      schema.addBody(property);
    }
    return schema;
  }

  private String inferType(Object value) {
    if (value instanceof Number) {
      return "number";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    return "string";
  }

  private Map<String, String> parseActionMappings(WorkflowEngineMapping mapping) {
    Map<String, String> result = new java.util.LinkedHashMap<>();
    if (mapping.getActionMappingsJson() == null || mapping.getActionMappingsJson().isBlank()) {
      return result;
    }
    try {
      var array = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mapping.getActionMappingsJson());
      if (array.isArray()) {
        for (var item : array) {
          String systemAction = item.path("systemAction").asText(null);
          String externalAction = item.path("externalAction").asText(null);
          if (systemAction != null && externalAction != null) {
            result.put(systemAction, externalAction);
          }
        }
      }
    } catch (Exception ex) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_NOT_MAPPED + "] 动作映射解析失败: " + ex.getMessage(), ex);
    }
    return result;
  }
}
