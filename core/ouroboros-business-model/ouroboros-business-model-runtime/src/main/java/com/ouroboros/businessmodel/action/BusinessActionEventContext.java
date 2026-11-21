package com.ouroboros.businessmodel.action;

import java.util.Set;

import com.ouroboros.businessmodel.event.BusinessEventIdentity;

/** Trusted action facts exposed to the Event runtime while a synchronous Action binding is executing. */
public record BusinessActionEventContext(String modelFullName,
                                         String actionName,
                                         String executionId,
                                         String modelRevision,
                                         String actorRef,
                                         boolean readOnly,
                                         Set<BusinessEventIdentity> emittedEvents) {
  public BusinessActionEventContext {
    emittedEvents = Set.copyOf(emittedEvents);
  }

  public boolean allows(BusinessEventIdentity identity) {
    return emittedEvents.stream().anyMatch(event ->
        event.modelFullName().equalsIgnoreCase(identity.modelFullName())
            && event.eventName().equalsIgnoreCase(identity.eventName()));
  }
}
