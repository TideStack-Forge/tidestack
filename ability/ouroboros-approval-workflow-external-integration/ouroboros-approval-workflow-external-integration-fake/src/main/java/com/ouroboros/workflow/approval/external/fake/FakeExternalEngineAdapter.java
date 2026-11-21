package com.ouroboros.workflow.approval.external.fake;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.DefinitionDiscoveryRequest;
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
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.InMemoryExternalEngineCore;

import io.vavr.control.Try;

/**
 * 产品级 fake 外部引擎适配器（灰度/演示）：行为由连接 provider schema 的顶层字段控制——
 * {@code startFail}（bool，缺省 false）、{@code autoApproveAfterSeconds}（long，
 * 缺省 0=不自动流转，>0 时实例在启动该时长后经查询表现为 FINISHED）、
 * {@code initialStatus}（缺省 RUNNING）。
 *
 * <p>状态机/幂等语义委托共享语义核心 {@link InMemoryExternalEngineCore}（B1），
 * 本类仅保留端口面与控制面（开关、autoApprove 起始时间簿记）。start 幂等
 * 键去重、未知结果码不推进终态语义与测试 fake 一致；真实外部网络零依赖
 * （演示/契约锚点，见 Phase 3 清单 M16）。
 */
public class FakeExternalEngineAdapter
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

  /** 假引擎返回的外部实例地址前缀（仅用于本地联调）。 */
  private static final String EXTERNAL_INSTANCE_URL_BASE = "https://demo.example/fake/";

  private final ResolvedEnterpriseConnection connection;
  private final InMemoryExternalEngineCore core = new InMemoryExternalEngineCore();
  private final List<ExternalStartCommand> startedCommands = new ArrayList<>();
  private final Map<String, Instant> instanceStartedAt = new LinkedHashMap<>();
  private final boolean startFail;
  private final long autoApproveAfterSeconds;
  private final String initialStatus;

  public FakeExternalEngineAdapter(ResolvedEnterpriseConnection connection) {
    this.connection = connection;
    Map<String, Object> config = connection == null || connection.config() == null
        ? Map.of() : connection.config();
    this.startFail = boolField(config, FakeEngineAdapterProvider.START_FAIL_FIELD, false);
    this.autoApproveAfterSeconds = longField(
        config, FakeEngineAdapterProvider.AUTO_APPROVE_AFTER_SECONDS_FIELD, 0L);
    this.initialStatus = stringField(
        config, FakeEngineAdapterProvider.INITIAL_STATUS_FIELD, InMemoryExternalEngineCore.STATUS_RUNNING);
  }

  // ---- 端口实现 ----

  @Override
  public Try<ExternalHealth> health() {
    return Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request) {
    return Try.success(new ExternalProcessDefinitionPage(List.of(
        new ExternalProcessDefinition("DEMO_PROC", "v1", "演示流程", "hash-demo")), false));
  }

  @Override
  public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
    return Try.success(new ExternalProcessDefinition(
        definition.definitionId(), definition.definitionVersion(), "演示流程", definition.schemaHash()));
  }

  @Override
  public Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition) {
    return Try.success(new ExternalFormSchema(List.of(), Map.of()));
  }

  @Override
  public Try<ExternalStartResult> start(ExternalStartCommand command) {
    startedCommands.add(command);
    if (startFail) {
      return Try.failure(new IllegalStateException("fake 启动失败（startFail=true）"));
    }
    ExternalStartResult result = core.start(
        command.idempotencyKey(),
        command.externalDefinitionRef().definitionId(),
        "fake-",
        EXTERNAL_INSTANCE_URL_BASE,
        command.originatorUserId(),
        initialStatus);
    if (!instanceStartedAt.containsKey(result.externalInstanceId())) {
      instanceStartedAt.put(result.externalInstanceId(), Instant.now());
    }
    return Try.success(result);
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    String resultCode = core.getResult(instance.externalInstanceId());
    return Try.success(new ExternalInstanceSnapshot(
        instance.externalInstanceId(), currentStatus(instance.externalInstanceId()), resultCode,
        outcomeOf(resultCode), null, Map.of()));
  }

  @Override
  public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
    List<ExternalInstanceSnapshot> snapshots = core.getStatuses().keySet().stream()
        .map(id -> {
          String resultCode = core.getResult(id);
          return new ExternalInstanceSnapshot(
              id, currentStatus(id), resultCode, outcomeOf(resultCode), null, Map.of());
        })
        .toList();
    return Try.success(new ExternalInstancePage(snapshots, null));
  }

  private String outcomeOf(String resultCode) {
    return switch (resultCode == null ? "" : resultCode) {
      case "APPROVED" -> "approved";
      case "REJECTED" -> "rejected";
      case "CANCELED" -> "cancelled";
      default -> null;
    };
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
    if (!InMemoryExternalEngineCore.SUPPORTED_ACTIONS.contains(command.externalAction())) {
      return Try.failure(new IllegalStateException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_NOT_MAPPED + "] 适配器未声明动作: "
              + command.externalAction()));
    }
    return Try.of(() -> core.executeIdempotently(
        command, new ExternalActionResult(true, "APPROVED", "approved", null, null)));
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    // fake 不接收平台 HTTP 回调（事件源=simulate 端点，直投 inbox），正常请求返回空事件列表；
    // X-Sign=bad 模拟验签失败（契约测试用），保持 codec 契约语义完整。
    if ("bad".equals(request.headers().get("X-Sign"))) {
      return Try.failure(new IllegalStateException("fake 验签失败"));
    }
    return Try.success(List.of());
  }

  /** startFail 开关（供单测直接断言行为，不经配置也可构造）。 */
  public boolean isStartFail() {
    return startFail;
  }

  public List<ExternalStartCommand> startedCommands() {
    return startedCommands;
  }

  /** 供 simulate-callback 模拟记录追加。 */
  public void addRecord(String nodeId, String operator, String action, String comment) {
    core.addRecord(nodeId, operator, action, comment);
  }

  /** 测试辅助：把实例启动时间回拨 seconds 秒，用于验证 autoApproveAfterSeconds。 */
  void backdateStartedAt(String externalInstanceId, long seconds) {
    instanceStartedAt.computeIfPresent(externalInstanceId,
        (id, startedAt) -> startedAt.minusSeconds(seconds));
  }

  private String currentStatus(String externalInstanceId) {
    String status = core.getStatus(externalInstanceId);
    if (status == null) {
      status = InMemoryExternalEngineCore.STATUS_RUNNING;
    }
    if (!InMemoryExternalEngineCore.STATUS_FINISHED.equals(status) && autoApproveAfterSeconds > 0) {
      Instant startedAt = instanceStartedAt.get(externalInstanceId);
      if (startedAt != null && Duration.between(startedAt, Instant.now()).getSeconds() >= autoApproveAfterSeconds) {
        return InMemoryExternalEngineCore.STATUS_FINISHED;
      }
    }
    return status;
  }

  private static boolean boolField(Map<String, Object> config, String key, boolean defaultValue) {
    Object value = config.get(key);
    return value instanceof Boolean bool ? bool
        : value != null ? Boolean.parseBoolean(String.valueOf(value)) : defaultValue;
  }

  private static long longField(Map<String, Object> config, String key, long defaultValue) {
    Object value = config.get(key);
    return value instanceof Number number ? number.longValue()
        : value != null ? Long.parseLong(String.valueOf(value)) : defaultValue;
  }

  private static String stringField(Map<String, Object> config, String key, String defaultValue) {
    Object value = config.get(key);
    return value == null ? defaultValue : String.valueOf(value);
  }
}
