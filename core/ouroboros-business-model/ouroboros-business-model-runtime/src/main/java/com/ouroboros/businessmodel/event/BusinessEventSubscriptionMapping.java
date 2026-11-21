package com.ouroboros.businessmodel.event;

import java.util.Map;

import com.ouroboros.exception.BusinessException;

/** Evaluates published subscription mappings against the immutable event envelope. */
public final class BusinessEventSubscriptionMapping {
  private BusinessEventSubscriptionMapping() {
  }

  public static boolean matches(Object filter, BusinessEventOccurrence<?> occurrence) {
    if (filter == null) {
      return true;
    }
    if (filter instanceof Boolean accepted) {
      return accepted;
    }
    if (!(filter instanceof String expression)) {
      throw new BusinessException("Business Event subscription filter must be a predicate");
    }
    return com.ouroboros.expression.EL.evalPredicate(expression, Map.of("event", occurrence.toMap()))
        .getOrElseThrow(error -> new BusinessException("Business Event subscription filter is invalid", error));
  }

}
