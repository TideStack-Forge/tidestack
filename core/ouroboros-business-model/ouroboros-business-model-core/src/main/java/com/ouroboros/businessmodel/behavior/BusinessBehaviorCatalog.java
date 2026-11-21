package com.ouroboros.businessmodel.behavior;

import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan;
import com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan;

/** A read-only view of the same resolved contract, without compiled expression internals or runtime data. */
public record BusinessBehaviorCatalog(String modelFullName, String revision,
    List<ActionDefinition> actions, List<EventDefinition> events, List<EventSubscriptionDefinition> subscriptions,
    List<BehaviorMemberSource> sources, List<String> conflicts, Map<String, String> executionAuthorities,
    Map<String, BusinessActionMutationEventPlan> mutationEventPlans,
    Map<String, BusinessActionFeatureQueryPlan> featureQueryPlans) {
  public static BusinessBehaviorCatalog from(BusinessModelBehavior behavior) {
    var mutationPlans = new java.util.TreeMap<String, BusinessActionMutationEventPlan>(String.CASE_INSENSITIVE_ORDER);
    behavior.getActions().keySet().forEach(name -> {
      var plan = behavior.mutationEventPlan(name);
      if (plan != null) { mutationPlans.put(name, plan); }
    });
    return new BusinessBehaviorCatalog(behavior.getModelFullName(), behavior.getModelRevision(),
        behavior.getActionDefinitions(), behavior.getEventDefinitions(), behavior.getEventSubscriptionDefinitions(),
        behavior.getMemberSources(), behavior.getDefinitionConflicts(), behavior.getExecutionAuthorities(),
        java.util.Collections.unmodifiableMap(mutationPlans), behavior.getFeatureQueryPlans());
  }
}
