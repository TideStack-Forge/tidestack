package com.ouroboros.businessmodel;

import java.util.List;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.behavior.BusinessEventSubscriptionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;
import com.ouroboros.businessmodel.behavior.BusinessModelEventSubscriptionBindingContributor;

@Priority(0)
public class BusinessEventAnnotationContributor implements
    BusinessModelBehaviorContributor, BusinessModelEventSubscriptionBindingContributor {
  @Override
  public List<BusinessModelBehaviorContribution> getContributions() {
    return BusinessEventAnnotationCatalog.getDefinitionContributions();
  }

  @Override
  public List<BusinessEventSubscriptionBindingContribution> getEventSubscriptionBindingContributions() {
    return BusinessEventAnnotationCatalog.getBindingContributions();
  }
}
