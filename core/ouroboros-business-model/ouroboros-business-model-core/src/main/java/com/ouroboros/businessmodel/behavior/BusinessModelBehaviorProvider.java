package com.ouroboros.businessmodel.behavior;

import java.util.Map;
import java.util.Optional;

/**
 * Provider for the published behavior snapshots of business models.
 */
public interface BusinessModelBehaviorProvider {
  Optional<BusinessModelBehavior> getBusinessModelBehavior(String modelFullName);

  Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap();
}
