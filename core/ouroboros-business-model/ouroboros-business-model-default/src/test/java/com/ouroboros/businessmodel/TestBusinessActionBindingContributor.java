package com.ouroboros.businessmodel;

import java.util.List;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.BusinessActionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorBindingContributor;

public class TestBusinessActionBindingContributor implements BusinessModelBehaviorBindingContributor {
  @Override
  public List<BusinessActionBindingContribution> getBindingContributions() {
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName("annotated-handler");
    ActionBinding customBinding = new ActionBinding();
    customBinding.setKind("handler");
    customBinding.setHandlerName("custom-api-handler");
    return List.of(
        new BusinessActionBindingContribution(
            "sales.AnnotatedOrder", "submit", "test annotation binding", binding),
        new BusinessActionBindingContribution(
            "sales.CodeBoundOrder", "approve", "test custom API binding", customBinding));
  }
}
