package com.ouroboros.businessmodel.workflow;

import java.util.Objects;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventProducer;

/** A verified source fact grants one emission scope; knowing this producer's name grants nothing. */
final class WorkflowBusinessEventProducer implements BusinessEventProducer {
  static final String NAME = "businessWorkflowProjection";
  private final ThreadLocal<Fact> current = new ThreadLocal<>();

  @Override
  public String getName() { return NAME; }

  @Override
  public String getKind() { return "workflow"; }

  @Override
  public boolean isEmissionAllowed(BusinessEventIdentity identity, BusinessEventEmissionContext context) {
    Fact fact = current.get();
    return fact != null && fact.identity().equals(identity) && Objects.equals(fact.sourceFactId(), context.causationId());
  }

  <T> T withFact(BusinessEventIdentity identity, String sourceFactId, Supplier<T> operation) {
    Fact previous = current.get();
    current.set(new Fact(identity, sourceFactId));
    try {
      return operation.get();
    } finally {
      if (previous == null) { current.remove(); }
      else { current.set(previous); }
    }
  }

  private record Fact(BusinessEventIdentity identity, String sourceFactId) { }
}
