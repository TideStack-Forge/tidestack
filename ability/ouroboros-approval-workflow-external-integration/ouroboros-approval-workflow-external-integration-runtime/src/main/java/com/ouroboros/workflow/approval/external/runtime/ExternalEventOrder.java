package com.ouroboros.workflow.approval.external.runtime;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * External event ordering rules shared by processors and persistence compare-and-set updates.
 */
public final class ExternalEventOrder {

  private ExternalEventOrder() {
  }

  /**
   * @return negative for stale, zero for duplicate/idempotent, positive for newer.
   */
  public static int compare(
      String incomingRevision,
      LocalDateTime incomingEventTime,
      String currentRevision,
      LocalDateTime currentEventTime) {
    if (hasText(incomingRevision)) {
      if (hasText(currentRevision)) {
        return compareRevisionValues(incomingRevision, currentRevision);
      }
      return 1;
    }
    if (hasText(currentRevision) && currentEventTime == null) {
      return -1;
    }
    if (currentEventTime == null) {
      return 1;
    }
    if (incomingEventTime == null) {
      return -1;
    }
    return incomingEventTime.compareTo(currentEventTime);
  }

  /**
   * 比较事件事实。revision/time 只表达顺序位置；位置相同时，只有相同 eventId 才是同一事实重投。
   */
  public static int compareFact(
      String incomingRevision,
      LocalDateTime incomingEventTime,
      String incomingEventId,
      String currentRevision,
      LocalDateTime currentEventTime,
      String currentEventId) {
    int order = compare(incomingRevision, incomingEventTime, currentRevision, currentEventTime);
    if (order != 0) {
      return order;
    }
    if (hasText(incomingEventId) && incomingEventId.equals(currentEventId)) {
      return 0;
    }
    return hasText(incomingEventId) ? 1 : 0;
  }

  private static int compareRevisionValues(String current, String last) {
    BigInteger currentNumber = parseIntegerOrNull(current);
    BigInteger lastNumber = parseIntegerOrNull(last);
    if (currentNumber != null && lastNumber != null) {
      return currentNumber.compareTo(lastNumber);
    }
    return current.compareTo(last);
  }

  private static BigInteger parseIntegerOrNull(String value) {
    try {
      return new BigInteger(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
