package com.ouroboros.enterprise.callback;

import java.util.Set;

/** Async acceptance returns only after the owning domain durably accepts or recognizes the event. */
public interface EnterpriseCallbackSubscriber {
  String subscriberId();
  String provider();
  Set<String> eventTypes();
  void receive(VerifiedEnterpriseEvent event);
}
