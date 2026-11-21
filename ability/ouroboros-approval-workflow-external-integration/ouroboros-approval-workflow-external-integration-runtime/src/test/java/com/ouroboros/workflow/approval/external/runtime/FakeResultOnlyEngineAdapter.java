package com.ouroboros.workflow.approval.external.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;

import io.vavr.control.Try;

/**
 * M5 测试用 fake 结果型适配器（test scope）：内存实现启动/查询/回调解析，可模拟启动失败。
 */
public class FakeResultOnlyEngineAdapter
    implements ApprovalWorkflowEngineAdapter,
        ExternalWorkflowStarter,
        ExternalInstanceReader,
        ExternalCallbackCodec {

  private final AtomicInteger counter = new AtomicInteger();
  private boolean failStart = false;
  private boolean failCallback = false;
  private Throwable startFailure;
  private RuntimeException startException;
  private final List<ExternalStartCommand> startedCommands = new ArrayList<>();
  private final Map<String, ExternalStartResult> idempotentResults = new java.util.LinkedHashMap<>();

  public void setFailStart(boolean failStart) {
    this.failStart = failStart;
  }

  public void setStartFailure(Throwable startFailure) {
    this.startFailure = startFailure;
  }

  public void setStartException(RuntimeException startException) {
    this.startException = startException;
  }

  public void setFailCallback(boolean failCallback) {
    this.failCallback = failCallback;
  }

  public List<ExternalStartCommand> startedCommands() {
    return startedCommands;
  }

  @Override
  public Try<ExternalHealth> health() {
    return Try.success(new ExternalHealth(true, "ok"));
  }

  @Override
  public synchronized Try<ExternalStartResult> start(ExternalStartCommand command) {
    startedCommands.add(command);
    if (startException != null) {
      throw startException;
    }
    if (startFailure != null) {
      return Try.failure(startFailure);
    }
    if (failStart) {
      return Try.failure(new IllegalStateException("fake 启动失败"));
    }
    if (command.idempotencyKey() != null && idempotentResults.containsKey(command.idempotencyKey())) {
      return Try.success(idempotentResults.get(command.idempotencyKey()));
    }
    // 注意：命令中的用户标识是平台规范 ID（契约见 ExternalStartCommand javadoc）；
    // 真实适配器必须经 ExternalUserIdentityMapper 翻译为外部标识后再调用外部 API。
    ExternalStartResult result = new ExternalStartResult(
        "ext-" + counter.incrementAndGet(), "https://external.example/inst/" + counter.get(), null);
    if (command.idempotencyKey() != null) {
      idempotentResults.put(command.idempotencyKey(), result);
    }
    return Try.success(result);
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
    return Try.success(new ExternalInstanceSnapshot(
        instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
  }

  @Override
  public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
    return Try.success(new ExternalInstancePage(List.of(), null));
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    if (failCallback) {
      return Try.failure(new IllegalStateException("回调验签失败"));
    }
    return Try.success(List.of());
  }
}
