package com.ouroboros.businessmodel.behavior;

import java.util.List;

/** Supplies code listener bindings for published Event subscriptions. */
public interface BusinessModelEventSubscriptionBindingContributor {
  List<BusinessEventSubscriptionBindingContribution> getEventSubscriptionBindingContributions();
}
