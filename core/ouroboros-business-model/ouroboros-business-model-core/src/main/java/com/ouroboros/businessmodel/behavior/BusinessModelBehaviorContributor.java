package com.ouroboros.businessmodel.behavior;

import java.util.List;

/**
 * Supplies code-defined behavior contracts through the business-model SPI.
 */
public interface BusinessModelBehaviorContributor {
  List<BusinessModelBehaviorContribution> getContributions();

  /** Model-derived contributions use the same Resolver without scanning or rebuilding the model registry. */
  default List<BusinessModelBehaviorContribution> getContributions(com.ouroboros.businessmodel.meta.BusinessModelMeta modelMeta) {
    return getContributions();
  }
}
