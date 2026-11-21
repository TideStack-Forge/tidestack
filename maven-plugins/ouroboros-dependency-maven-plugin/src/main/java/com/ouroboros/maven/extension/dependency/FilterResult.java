package com.ouroboros.maven.extension.dependency;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class FilterResult {
  private final List<DependencyDecision> decisions;

  FilterResult(List<DependencyDecision> decisions) {
    this.decisions = Collections.unmodifiableList(decisions);
  }

  public List<DependencyDecision> getDecisions() {
    return decisions;
  }

  public List<DependencyDecision> getCopied() {
    return filter("copy");
  }

  public List<DependencyDecision> getExcluded() {
    return filter("exclude");
  }

  private List<DependencyDecision> filter(String action) {
    return decisions.stream()
        .filter(decision -> action.equals(decision.getAction()))
        .collect(Collectors.toList());
  }
}
