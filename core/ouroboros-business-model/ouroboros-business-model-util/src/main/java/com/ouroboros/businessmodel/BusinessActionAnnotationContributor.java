package com.ouroboros.businessmodel;

import java.util.List;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.behavior.BusinessActionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorBindingContributor;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;

@Priority(0)
public class BusinessActionAnnotationContributor implements
    BusinessModelBehaviorContributor, BusinessModelBehaviorBindingContributor {
  @Override
  public List<BusinessModelBehaviorContribution> getContributions() {
    return BusinessActionAnnotationCatalog.getDefinitionContributions();
  }

  @Override
  public List<BusinessActionBindingContribution> getBindingContributions() {
    return BusinessActionAnnotationCatalog.getBindingContributions();
  }
}
