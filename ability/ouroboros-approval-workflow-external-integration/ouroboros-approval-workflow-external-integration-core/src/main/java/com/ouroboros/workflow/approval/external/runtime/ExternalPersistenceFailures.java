package com.ouroboros.workflow.approval.external.runtime;

import java.sql.SQLException;

import org.springframework.dao.DuplicateKeyException;

import com.ouroboros.data.exception.UniqueConstraintViolationException;

/** Persistence failure classification shared by external-integration repositories. */
public final class ExternalPersistenceFailures {

  private ExternalPersistenceFailures() {
  }

  public static boolean isUniqueConstraintViolation(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor instanceof UniqueConstraintViolationException || cursor instanceof DuplicateKeyException) {
        return true;
      }
      if (cursor instanceof SQLException sqlException && isDatabaseDuplicateKey(sqlException)) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  private static boolean isDatabaseDuplicateKey(SQLException exception) {
    return exception.getErrorCode() == 1062 || "23505".equals(exception.getSQLState());
  }
}
