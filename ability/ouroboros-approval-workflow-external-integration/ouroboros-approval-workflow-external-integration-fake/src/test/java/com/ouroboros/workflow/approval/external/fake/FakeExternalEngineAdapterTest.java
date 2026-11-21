package com.ouroboros.workflow.approval.external.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionConfigurationValidator;

import io.vavr.control.Try;

/**
 * M16 fake 适配器单元测试：provider 字段开关、simulate-callback 直投 inbox。
 */
class FakeExternalEngineAdapterTest {

  private FakeExternalEngineAdapter adapter(Map<String, Object> fields) {
    return new FakeExternalEngineAdapter(new ResolvedEnterpriseConnection(
        "conn-1", fields, null));
  }

  private ExternalStartCommand startCommand(String idempotencyKey) {
    return new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("ns-1", "DEMO_PROC", "v1", "hash"),
        null, null, null, idempotencyKey, null, null, null);
  }

  @Test
  void startWithSameIdempotencyKeyReturnsSameInstance() {
    FakeExternalEngineAdapter adapter = adapter(Map.of());

    ExternalStartResult first = adapter.start(startCommand("ik-1")).get();
    ExternalStartResult second = adapter.start(startCommand("ik-1")).get();

    assertThat(second.externalInstanceId()).isEqualTo(first.externalInstanceId());
    assertThat(adapter.startedCommands()).hasSize(2);
  }

  @Test
  void startFailOptionFailsStart() {
    FakeExternalEngineAdapter adapter = adapter(Map.of(FakeEngineAdapterProvider.START_FAIL_FIELD, true));

    assertThat(adapter.start(startCommand("ik-1")).isFailure()).isTrue();
  }

  @Test
  void providerSchemaExposesOnlyTypedFakeFields() {
    FakeEngineAdapterProvider provider = new FakeEngineAdapterProvider();

    assertThat(new FakeConnectionProvider().connectionConfigFields()).containsExactlyInAnyOrder(
        FakeEngineAdapterProvider.START_FAIL_FIELD,
        FakeEngineAdapterProvider.AUTO_APPROVE_AFTER_SECONDS_FIELD,
        FakeEngineAdapterProvider.INITIAL_STATUS_FIELD);
    Map<String, Object> schema = new FakeConnectionProvider().connectionSchema();
    assertThat(schema).containsKey("fields");
    assertThat(schema).doesNotContainKey("options");
    assertThat(schema.get("fields").toString()).contains(
        FakeEngineAdapterProvider.START_FAIL_FIELD,
        FakeEngineAdapterProvider.AUTO_APPROVE_AFTER_SECONDS_FIELD,
        FakeEngineAdapterProvider.INITIAL_STATUS_FIELD);
  }

  @Test
  void unifiedConfigurationAcceptsTypedFieldsAndRejectsRemovedOptions() {
    EnterpriseConnectionConfigurationValidator validator =
        new EnterpriseConnectionConfigurationValidator(List.of(new FakeConnectionProvider()), List.of());

    validator.validate(List.of(Map.of(
        "connectionId", "conn-1",
        "provider", FakeEngineAdapterProvider.ADAPTER_ID,
        FakeEngineAdapterProvider.START_FAIL_FIELD, false,
        FakeEngineAdapterProvider.AUTO_APPROVE_AFTER_SECONDS_FIELD, 5,
        FakeEngineAdapterProvider.INITIAL_STATUS_FIELD, "RUNNING")));

    assertThatThrownBy(() -> validator.validate(List.of(Map.of(
        "connectionId", "conn-1",
        "provider", FakeEngineAdapterProvider.ADAPTER_ID,
        "options", Map.of(FakeEngineAdapterProvider.START_FAIL_FIELD, true)))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("连接字段已删除")
        .hasMessageContaining("options");
  }

  @Test
  void unknownResultCodeNeverAdvancesToFinished() {
    FakeExternalEngineAdapter adapter = adapter(Map.of());
    ExternalStartResult started = adapter.start(startCommand(null)).get();

    // 未知结果码不推进终态：仅 execute 返回成功不代表引擎实例已终态
    assertThat(adapter.getInstance(new ExternalInstanceRef(started.externalInstanceId(), "conn-1"))
        .get().lifecycleStatus()).isEqualTo("RUNNING");
  }

  @Test
  void autoApproveOptionAdvancesStatusAfterElapsedTime() {
    FakeExternalEngineAdapter adapter = adapter(Map.of(
        FakeEngineAdapterProvider.AUTO_APPROVE_AFTER_SECONDS_FIELD, 10L));
    ExternalStartResult started = adapter.start(startCommand(null)).get();
    adapter.backdateStartedAt(started.externalInstanceId(), 11L);

    assertThat(adapter.getInstance(new ExternalInstanceRef(started.externalInstanceId(), "conn-1"))
        .get().lifecycleStatus()).isEqualTo("FINISHED");
  }

  @Test
  void simulateCallbackIngestsEnvelopeWithAdapterAndConnection() {
    ExternalEventIngestService ingestService = mock(ExternalEventIngestService.class);
    when(ingestService.ingest(any())).thenReturn(Try.success(null));
    FakeSimulateCallbackController controller = new FakeSimulateCallbackController(ingestService);

    var result = controller.simulate("conn-1", Map.of(
        "externalInstanceId", "fake-1", "resultCode", "APPROVED"));

    assertThat(result.get("accepted")).isEqualTo(Boolean.TRUE);
    org.mockito.ArgumentCaptor<ExternalCallbackEnvelope> captor =
        org.mockito.ArgumentCaptor.forClass(ExternalCallbackEnvelope.class);
    verify(ingestService).ingest(captor.capture());
    ExternalCallbackEnvelope envelope = captor.getValue();
    assertThat(envelope.adapterId()).isEqualTo(FakeEngineAdapterProvider.ADAPTER_ID);
    assertThat(envelope.connectionId()).isEqualTo("conn-1");
    assertThat(envelope.externalInstanceId()).isEqualTo("fake-1");
    assertThat(envelope.resultCode()).isEqualTo("APPROVED");
  }

  @Test
  void simulateCallbackPropagatesIngestFailure() {
    ExternalEventIngestService ingestService = mock(ExternalEventIngestService.class);
    when(ingestService.ingest(any())).thenReturn(Try.failure(new IllegalStateException("入箱失败")));
    FakeSimulateCallbackController controller = new FakeSimulateCallbackController(ingestService);

    assertThatThrownBy(() -> controller.simulate("conn-1", Map.of("externalInstanceId", "fake-1")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("fake 回调入箱失败");
  }
}
