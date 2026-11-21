package com.ouroboros.businessmodel.event;

/** Transaction boundary of a subscription, fixed when its delivery responsibility is created. */
public enum BusinessEventExecutionMode {
  JOIN_SOURCE_TRANSACTION,
  AFTER_COMMIT_TRANSACTIONAL,
  AFTER_COMMIT_INDEPENDENT
}
