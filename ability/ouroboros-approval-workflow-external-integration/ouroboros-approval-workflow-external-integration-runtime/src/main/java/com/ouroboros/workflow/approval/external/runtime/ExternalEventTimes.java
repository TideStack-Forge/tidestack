package com.ouroboros.workflow.approval.external.runtime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 外部事件时间解析工具。
 */
public final class ExternalEventTimes {

  private ExternalEventTimes() {
  }

  public static LocalDateTime parseOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return parseOffsetDateTime(trimmed)
        .or(() -> parseLocalDateTime(trimmed))
        .or(() -> parseInstant(trimmed))
        .orElse(null);
  }

  private static Optional<LocalDateTime> parseOffsetDateTime(String value) {
    try {
      return Optional.of(LocalDateTime.ofInstant(OffsetDateTime.parse(value).toInstant(), ZoneOffset.UTC));
    } catch (DateTimeParseException ex) {
      return Optional.empty();
    }
  }

  private static Optional<LocalDateTime> parseLocalDateTime(String value) {
    try {
      return Optional.of(LocalDateTime.parse(value));
    } catch (DateTimeParseException ex) {
      return Optional.empty();
    }
  }

  private static Optional<LocalDateTime> parseInstant(String value) {
    try {
      return Optional.of(LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC));
    } catch (DateTimeParseException ex) {
      return Optional.empty();
    }
  }
}
