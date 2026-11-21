package com.ouroboros.workflow.approval.external.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.DefinitionDiscoveryRequest;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalFormSnapshot;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionDiscovery;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionPage;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalRecordPage;
import com.ouroboros.workflow.approval.external.ExternalRecordReader;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalTaskPage;
import com.ouroboros.workflow.approval.external.ExternalTaskQuery;
import com.ouroboros.workflow.approval.external.ExternalTaskReader;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.InMemoryExternalEngineCore;

import io.vavr.control.Try;

/**
 * M12 测试用 fake 全过程适配器（test scope）：内存状态机覆盖全过程链路全部端口。
 *
 * <p>状态机/幂等语义委托共享语义核心 {@link InMemoryExternalEngineCore}（B1），
 * 本类仅保留端口面与 setter 钩子控制面。状态语义：start 创建 RUNNING 实例与首个
 * 待办任务；execute 按 canonicalAction 变更实例/任务状态；未知结果码不得把实例
 * 翻到终态（与运行时"未知状态不自动结束"规则一致）。
 */
public class FakeFullProcessEngineAdapter
    implements ApprovalWorkflowEngineAdapter,
        ExternalProcessDefinitionDiscovery,
        ExternalProcessDefinitionValidator,
        ExternalProcessDefinitionFormReader,
        ExternalWorkflowStarter,
        ExternalInstanceReader,
        ExternalRecordReader,
        ExternalTaskReader,
        ExternalWorkflowActionExecutor,
        ExternalCallbackCodec {

  private final InMemoryExternalEngineCore core = new InMemoryExternalEngineCore();
  private final List<ExternalStartCommand> startedCommands = new ArrayList<>();
  private final List<ExternalActionCommand> executedCommands = new ArrayList<>();
  private final List<ExternalProcessDefinitionRef> validatedDefinitions = new ArrayList<>();

  private boolean failStart = false;
  private boolean failValidateDefinition = false;
  private boolean failCallback = false;
  private Try<ExternalActionResult> executeOutcome =
      Try.success(new ExternalActionResult(true, "APPROVED", "approved", null, null));
  private List<ExternalCallbackEnvelope> callbackEnvelopes = List.of();

  // ---- 测试辅助 ----

  public void setFailStart(boolean failStart) {
    this.failStart = failStart;
  }

  public void setFailValidateDefinition(boolean failValidateDefinition) {
    this.failValidateDefinition = failValidateDefinition;
  }

  public void setFailCallback(boolean failCallback) {
    this.failCallback = failCallback;
  }

  public void setExecuteOutcome(Try<ExternalActionResult> executeOutcome) {
    this.executeOutcome = executeOutcome;
  }

  public void setCallbackEnvelopes(List<ExternalCallbackEnvelope> envelopes) {
    this.callbackEnvelopes = envelopes;
  }

  public List<ExternalStartCommand> startedCommands() {
    return startedCommands;
  }

  public List<ExternalActionCommand> executedCommands() {
    return executedCommands;
  }

  public List<ExternalProcessDefinitionRef> validatedDefinitions() {
    return validatedDefinitions;
  }

  public String instanceStatusOf(String externalInstanceId) {
    return core.getStatus(externalInstanceId);
  }

  public List<ExternalTaskSnapshot> tasksOf(String externalInstanceId) {
    return core.getTasks().stream()
        .filter(task -> externalInstanceId.equals(
            task.raw() == null ? null : task.raw().get("externalInstanceId")))
        .toList();
  }

  public void addRecord(String nodeId, String operator, String action, String comment) {
    core.addRecord(nodeId, operator, action, comment);
  }

  // ---- 端口实现 ----

  @Override
  public Try<ExternalHealth> health() {
    return Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request) {
    return Try.success(new ExternalProcessDefinitionPage(List.of(
        new ExternalProcessDefinition("def-1", "v1", "示例流程", "hash-1"),
        new ExternalProcessDefinition("def-1", "v2", "示例流程", "hash-2")), false));
  }

  @Override
  public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
    validatedDefinitions.add(definition);
    if (failValidateDefinition) {
      return Try.failure(new IllegalStateException("外部定义不存在或未发布"));
    }
    // 契约：请求什么版本就校验什么版本，不允许隐式绑定"最新"。
    return Try.success(new ExternalProcessDefinition(
        definition.definitionId(), definition.definitionVersion(), "示例流程", definition.schemaHash()));
  }

  @Override
  public Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition) {
    return Try.success(new ExternalFormSchema(List.of(), Map.of()));
  }

  @Override
  public Try<ExternalStartResult> start(ExternalStartCommand command) {
    startedCommands.add(command);
    if (failStart) {
      return Try.failure(new IllegalStateException("fake 启动失败"));
    }
    ExternalStartResult result = core.start(
        command.idempotencyKey(),
        command.externalDefinitionRef().definitionId(),
        "fp-",
        "https://external.example/fp/",
        command.originatorUserId(),
        InMemoryExternalEngineCore.STATUS_RUNNING);
    return Try.success(result);
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    String status = core.getStatus(instance.externalInstanceId());
    return Try.success(new ExternalInstanceSnapshot(
        instance.externalInstanceId(),
        status == null ? InMemoryExternalEngineCore.STATUS_RUNNING : status,
        core.getResult(instance.externalInstanceId()), null, null, Map.of()));
  }

  @Override
  public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
    List<ExternalInstanceSnapshot> snapshots = core.getStatuses().entrySet().stream()
        .map(entry -> new ExternalInstanceSnapshot(
            entry.getKey(), entry.getValue(), core.getResult(entry.getKey()), null, null, Map.of()))
        .toList();
    return Try.success(new ExternalInstancePage(snapshots, null));
  }

  @Override
  public Try<ExternalRecordPage> queryRecords(ExternalInstanceRef instance) {
    return Try.success(new ExternalRecordPage(core.getRecords(), false));
  }

  @Override
  public Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query) {
    return Try.success(new ExternalTaskPage(core.getTasks(), false, null));
  }

  @Override
  public Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task) {
    return core.getTasks().stream()
        .filter(candidate -> candidate.externalTaskId().equals(task.externalTaskId()))
        .findFirst()
        .map(Try::success)
        .orElseGet(() -> Try.failure(new IllegalStateException("外部任务不存在: " + task.externalTaskId())));
  }

  @Override
  public Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task) {
    return Try.success(new ExternalFormSnapshot(Map.of("amount", 100)));
  }

  @Override
  public Try<ExternalActionResult> execute(ExternalActionCommand command) {
    executedCommands.add(command);
    if (!InMemoryExternalEngineCore.SUPPORTED_ACTIONS.contains(command.externalAction())) {
      return Try.failure(new IllegalStateException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_NOT_MAPPED + "] 适配器未声明动作: "
              + command.externalAction()));
    }
    Try<ExternalActionResult> outcome = executeOutcome;
    if (outcome.isFailure()) {
      return outcome;
    }
    return Try.of(() -> core.executeIdempotently(command, outcome.get()));
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    if (failCallback) {
      return Try.failure(new IllegalStateException("回调验签失败"));
    }
    return Try.success(callbackEnvelopes);
  }
}
