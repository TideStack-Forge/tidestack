package com.ouroboros.statistics.reporting.runtime.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.data.exception.StatementException;
import com.ouroboros.data.exception.RecordValidationException;
import com.ouroboros.exception.ValidationException;

class StatisticsReportingExceptionTranslatorTest {

  @Test
  void shouldPreservePlatformExceptions() {
    PlatformException error = new PlatformException("invalid chart");

    assertSame(error, StatisticsReportingExceptionTranslator.translate(error, "ignored"));
  }

  @Test
  void shouldMapInvalidArgumentsToPlatformException() {
    RuntimeException translated = StatisticsReportingExceptionTranslator.translate(
        new IllegalArgumentException("invalid chart"), "ignored");

    assertTrue(translated instanceof PlatformException);
    assertEquals("invalid chart", translated.getMessage());
  }

  @Test
  void shouldMapUnexpectedFailuresToInternalException() {
    RuntimeException translated = StatisticsReportingExceptionTranslator.translate(
        new IllegalStateException("database unavailable"), "statistics execution failed");

    assertTrue(translated instanceof InternalException);
    assertEquals("statistics execution failed", translated.getMessage());
  }

  @Test
  void shouldUseBusinessExceptionForExportDenials() {
    assertTrue(StatisticsReportingExceptionTranslator.exportDenied() instanceof BusinessException);
  }

  @Test
  void shouldReuseTheRegisteredPlatformStatementExceptionMapper() {
    StatementException error = new StatementException("invalid query");

    RuntimeException translated = StatisticsReportingExceptionTranslator.translate(error, "query failed");

    assertTrue(translated instanceof PlatformException);
    assertEquals("invalid query", translated.getMessage());
    assertSame(error, translated.getCause());
  }

  @Test
  void shouldPreserveFieldErrorsFromTheRegisteredPlatformValidationMapper() {
    RecordValidationException error = new RecordValidationException(
        java.util.Map.of("amount", java.util.List.of("amount must be positive")));

    RuntimeException translated = StatisticsReportingExceptionTranslator.translate(error, "query failed");

    assertTrue(translated instanceof ValidationException);
    assertEquals(java.util.Map.of("amount", "amount must be positive"),
        ((ValidationException) translated).getErrors());
  }
}
