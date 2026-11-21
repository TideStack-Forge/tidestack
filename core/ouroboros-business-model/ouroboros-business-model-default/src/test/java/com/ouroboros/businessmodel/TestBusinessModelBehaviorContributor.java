package com.ouroboros.businessmodel;

import java.util.List;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;

public class TestBusinessModelBehaviorContributor implements BusinessModelBehaviorContributor {
  @Override
  public List<BusinessModelBehaviorContribution> getContributions() {
    ActionDefinition action = new ActionDefinition();
    action.setName("reserveStock");
    action.setTitle("Reserve stock");

    action.setIsEnabled(true);
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName("handler.reserveStock");
    action.setBinding(binding);
    return List.of(new BusinessModelBehaviorContribution(
        "sales.Order", "test contributor", List.of(action), List.of(), List.of()),
        mappingContribution("sourceCases.Legacy", "2.0"), mappingContribution("sourceCases.Current", null));
  }
  private static BusinessModelBehaviorContribution mappingContribution(String model, String version) {
    var action = new ActionDefinition();
    action.setName("fromCode");
    action.setIsEnabled(true);
    var binding = new ActionBinding();
    binding.setKind("logicflow");
    binding.setFlowName("sourceCases.flow");
    binding.setInputMapping(java.util.Map.of("data", "${ 1 + 2 }"));
    action.setBinding(binding);
    return new BusinessModelBehaviorContribution(model, "test-source.java", List.of(action), List.of(), List.of(), version);
  }

}
