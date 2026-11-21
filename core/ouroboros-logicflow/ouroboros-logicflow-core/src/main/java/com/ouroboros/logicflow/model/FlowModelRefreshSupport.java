package com.ouroboros.logicflow.model;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Optional capability on the original runtime provider; candidates never execute business flows. */
public interface FlowModelRefreshSupport {
  Candidate prepare(List<FlowModelSource> overrides, Set<String> changedNames);

  enum ApplyResult { PUBLISHED, INVALID, STALE, UNAVAILABLE, TIMED_OUT }

  interface Candidate {
    String basePublicationId();
    FlowModelCatalogue catalogue();
    FlowModelBuildReport report();
    Set<String> checkScope();
    ApplyResult publish(Duration timeout);
  }
}
