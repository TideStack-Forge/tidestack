package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;

class ExternalSnapshotEnvelopeFactoryTest {

  @Test
  void suspendedSnapshotIsAValidNonTerminalLifecycleFact() {
    var envelope = ExternalSnapshotEnvelopeFactory.create(
        "adapter-a", "connection-a",
        new ExternalInstanceSnapshot("external-1", "SUSPENDED", null, null, null, Map.of()));

    assertThat(envelope.externalInstanceId()).isEqualTo("external-1");
    assertThat(envelope.eventType()).isEqualTo("SUSPENDED");
    assertThat(envelope.resultCode()).isNull();
  }

  @Test
  void terminalSnapshotStillRequiresAResultCode() {
    assertThatThrownBy(() -> ExternalSnapshotEnvelopeFactory.create(
        "adapter-a", "connection-a",
        new ExternalInstanceSnapshot("external-1", "FINISHED", null, null, null, Map.of())))
        .hasMessageContaining("缺少规范结果状态");
  }
}
