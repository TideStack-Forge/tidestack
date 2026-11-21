package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ExternalEventOrderTest {

  @Test
  void numericRevisionsCompareNumerically() {
    assertThat(ExternalEventOrder.compare("10", null, "9", null)).isPositive();
    assertThat(ExternalEventOrder.compare("9", null, "10", null)).isNegative();
  }

  @Test
  void missingIncomingRevisionCannotOvertakeStoredRevisionWithoutStoredEventTime() {
    assertThat(ExternalEventOrder.compare(null, LocalDateTime.parse("2026-08-26T10:00:01"), "10", null))
        .isNegative();
  }

  @Test
  void missingRevisionFallsBackToEventTimeWhenStoredEventTimeExists() {
    assertThat(ExternalEventOrder.compare(
        null, LocalDateTime.parse("2026-08-26T10:00:01"), "10", LocalDateTime.parse("2026-08-26T10:00:00")))
        .isPositive();
    assertThat(ExternalEventOrder.compare(
        null, LocalDateTime.parse("2026-08-26T10:00:00"), "10", LocalDateTime.parse("2026-08-26T10:00:00")))
        .isZero();
  }

  @Test
  void missingRevisionAndMissingTimeIsStaleAgainstStoredEventTime() {
    assertThat(ExternalEventOrder.compare(null, null, null, LocalDateTime.parse("2026-08-26T10:00:00")))
        .isNegative();
  }
}
