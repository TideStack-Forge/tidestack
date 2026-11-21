package com.ouroboros.workflow.approval.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * B1 共享语义核心单元测试：start 幂等、结果码推进/不推进终态、公开读写入口行为。
 */
class InMemoryExternalEngineCoreTest {

  private final InMemoryExternalEngineCore core = new InMemoryExternalEngineCore();

  private ExternalStartResult start(String idempotencyKey, String initialStatus) {
    return core.start(idempotencyKey, "DEMO_PROC", "inst-", "https://demo.example/fake/", "user-1", initialStatus);
  }

  @Test
  void startWithSameIdempotencyKeyReturnsOriginalInstanceWithoutDuplicatingTask() {
    ExternalStartResult first = start("ik-1", InMemoryExternalEngineCore.STATUS_RUNNING);
    ExternalStartResult second = start("ik-1", InMemoryExternalEngineCore.STATUS_RUNNING);

    assertThat(second.externalInstanceId()).isEqualTo(first.externalInstanceId());
    assertThat(second.externalUrl()).isEqualTo(first.externalUrl());
    assertThat(core.getTasks()).hasSize(1);
    assertThat(core.getStatus(first.externalInstanceId())).isEqualTo(InMemoryExternalEngineCore.STATUS_RUNNING);
  }

  @Test
  void startWithoutIdempotencyKeyCreatesNewInstanceEachTime() {
    ExternalStartResult first = start(null, InMemoryExternalEngineCore.STATUS_RUNNING);
    ExternalStartResult second = start(null, InMemoryExternalEngineCore.STATUS_RUNNING);

    assertThat(second.externalInstanceId()).isNotEqualTo(first.externalInstanceId());
    assertThat(core.getTasks()).hasSize(2);
  }

  @Test
  void startRegistersInitialTaskWithOriginatorAndStatus() {
    ExternalStartResult started = start("ik-1", "PENDING");

    ExternalTaskSnapshot task = core.getTasks().get(0);
    assertThat(task.assignee()).isEqualTo("user-1");
    assertThat(task.status()).isEqualTo("PENDING");
    assertThat(task.raw()).containsEntry("externalInstanceId", started.externalInstanceId());
    assertThat(core.getStatus(started.externalInstanceId())).isEqualTo("PENDING");
  }

  @Test
  void terminalResultCodeAdvancesInstanceToFinished() {
    String approved = start("ik-1", InMemoryExternalEngineCore.STATUS_RUNNING).externalInstanceId();
    core.advanceOnResult(approved, "APPROVED");
    assertThat(core.getStatus(approved)).isEqualTo(InMemoryExternalEngineCore.STATUS_FINISHED);

    String rejected = start("ik-2", InMemoryExternalEngineCore.STATUS_RUNNING).externalInstanceId();
    core.advanceOnResult(rejected, "REJECTED");
    assertThat(core.getStatus(rejected)).isEqualTo(InMemoryExternalEngineCore.STATUS_FINISHED);

    String canceled = start("ik-3", InMemoryExternalEngineCore.STATUS_RUNNING).externalInstanceId();
    core.advanceOnResult(canceled, "CANCELED");
    assertThat(core.getStatus(canceled)).isEqualTo(InMemoryExternalEngineCore.STATUS_FINISHED);
  }

  @Test
  void unknownResultCodeDoesNotAdvanceInstance() {
    String instanceId = start("ik-1", InMemoryExternalEngineCore.STATUS_RUNNING).externalInstanceId();

    core.advanceOnResult(instanceId, "WEIRD");
    assertThat(core.getStatus(instanceId)).isEqualTo(InMemoryExternalEngineCore.STATUS_RUNNING);

    core.advanceOnResult(instanceId, null);
    assertThat(core.getStatus(instanceId)).isEqualTo(InMemoryExternalEngineCore.STATUS_RUNNING);
  }

  @Test
  void statusReadWriteEntriesRoundTrip() {
    assertThat(core.getStatus("unknown-instance")).isNull();

    core.setStatus("inst-9", "HOLD");
    assertThat(core.getStatus("inst-9")).isEqualTo("HOLD");
    assertThat(core.getStatuses()).containsEntry("inst-9", "HOLD");
  }

  @Test
  void taskAndRecordEntriesAppendAndExpose() {
    ExternalTaskSnapshot task = new ExternalTaskSnapshot("t-1", "node-1", "user-1", "RUNNING", null, Map.of());
    core.addTask(task);
    assertThat(core.getTasks()).containsExactly(task);

    core.addRecord("node-1", "user-1", "approve", "同意");
    assertThat(core.getRecords()).hasSize(1);
    assertThat(core.getRecords().get(0).action()).isEqualTo("approve");
    assertThat(core.getRecords().get(0).comment()).isEqualTo("同意");
  }

  @Test
  void idempotencyResultEntriesRoundTrip() {
    ExternalStartResult result = new ExternalStartResult("inst-7", "https://demo.example/fake/inst-7", null);
    core.putIdempotencyResult("ik-x", result);

    assertThat(core.getIdempotencyResult("ik-x")).isEqualTo(result);
    assertThat(core.getIdempotencyResult("ik-absent")).isNull();
  }

  @Test
  void supportedActionsVocabularyIsFixed() {
    assertThat(InMemoryExternalEngineCore.SUPPORTED_ACTIONS)
        .containsExactly("approve", "reject", "transfer", "remind", "cancel");
  }
}
