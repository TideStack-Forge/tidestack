package com.ouroboros.businessmodel.behavior;

import java.util.List;

/** Supplies implementation bindings for Action contracts declared by a business model. */
public interface BusinessModelBehaviorBindingContributor {
  List<BusinessActionBindingContribution> getBindingContributions();
}
