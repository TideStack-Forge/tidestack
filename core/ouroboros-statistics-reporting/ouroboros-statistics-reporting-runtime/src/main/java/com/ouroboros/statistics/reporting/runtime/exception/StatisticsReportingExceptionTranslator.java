package com.ouroboros.statistics.reporting.runtime.exception;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

public final class StatisticsReportingExceptionTranslator {
  private StatisticsReportingExceptionTranslator() {
  }

  public static RuntimeException translate(Throwable error, String operation) {
    return OuroborosExceptionMapper.toOuroborosException(error, () -> {
      if (error instanceof IllegalArgumentException) {
        return new PlatformException(error.getMessage(), error);
      }
      return new InternalException(operation, error);
    });
  }

  public static BusinessException exportDenied() {
    return new BusinessException("export permission denied");
  }
}
