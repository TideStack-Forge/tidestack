package com.ouroboros.businessmodel.action;

import java.util.Map;

/** Published projection plan for default mutation facts; independent of the implementation binding. */
public record BusinessActionMutationEventPlan(Map<String, String> eventNames) {
  public BusinessActionMutationEventPlan {
    eventNames = Map.copyOf(eventNames);
  }
}
