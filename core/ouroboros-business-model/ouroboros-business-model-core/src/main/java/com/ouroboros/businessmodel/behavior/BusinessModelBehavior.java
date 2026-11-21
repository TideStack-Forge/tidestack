package com.ouroboros.businessmodel.behavior;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Published behavior snapshot for one business model.
 */
public class BusinessModelBehavior {
  private final String modelFullName;
  private final List<BehaviorMemberSource> memberSources;
  private final List<ActionDefinition> actionDefinitions;
  private final List<EventDefinition> eventDefinitions;
  private final List<EventSubscriptionDefinition> eventSubscriptionDefinitions;
  private final Map<String, ActionDefinition> actions;
  private final Map<String, EventDefinition> events;
  private final Map<String, EventSubscriptionDefinition> eventSubscriptions;
  private final List<String> definitionConflicts;
  private final String modelRevision;
  private final String declarationRevision;
  private final Map<String, String> executionAuthorities;
  private final Map<String, BehaviorDefinitionSource> actionSources;
  private final Map<String, BehaviorDefinitionSource> subscriptionSources;
  private final Map<String, BusinessBehaviorMappingPlan> outputPlans;
  private final Map<String, BusinessBehaviorMappingPlan> subscriptionInputPlans;
  private final Map<String, com.ouroboros.businessmodel.action.BusinessActionInputPlan> inputPlans;
  private final Map<String, com.ouroboros.businessmodel.action.BusinessActionPreconditionPlan> preconditions;
  private final Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans;
  private final Map<String, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan> featureQueryPlans;

  public BusinessModelBehavior(String modelFullName,
                               List<ActionDefinition> actions,
                               List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions) {
    this(modelFullName, actions, events, eventSubscriptions, List.of());
  }

  public BusinessModelBehavior(String modelFullName,
                               List<ActionDefinition> actions,
                               List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions,
                               List<String> definitionConflicts) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, null);
  }

  public BusinessModelBehavior(String modelFullName,
                               List<ActionDefinition> actions,
                               List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions,
                               List<String> definitionConflicts,
                               String modelRevision) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, modelRevision, Map.of());
  }

  public BusinessModelBehavior(String modelFullName,
                               List<ActionDefinition> actions,
                               List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions,
                               List<String> definitionConflicts,
                               String modelRevision,
                               Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, modelRevision,
        mutationEventPlans, Map.of(), Map.of());
  }

  public BusinessModelBehavior(String modelFullName, List<ActionDefinition> actions, List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions, List<String> definitionConflicts,
                               String modelRevision,
                               Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans,
                               Map<String, BehaviorDefinitionSource> actionSources,
                               Map<String, BehaviorDefinitionSource> subscriptionSources) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, modelRevision,
        mutationEventPlans, actionSources, subscriptionSources, Map.of());
  }

  public BusinessModelBehavior(String modelFullName, List<ActionDefinition> actions, List<EventDefinition> events,
                               List<EventSubscriptionDefinition> eventSubscriptions, List<String> definitionConflicts,
                               String modelRevision,
                               Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans,
                               Map<String, BehaviorDefinitionSource> actionSources,
                               Map<String, BehaviorDefinitionSource> subscriptionSources,
                               Map<String, String> executionAuthorities) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, modelRevision,
        mutationEventPlans, actionSources, subscriptionSources, executionAuthorities, List.of());
  }

  public BusinessModelBehavior(String modelFullName, List<ActionDefinition> actions, List<EventDefinition> events,
      List<EventSubscriptionDefinition> eventSubscriptions, List<String> definitionConflicts, String modelRevision,
      Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans,
      Map<String, BehaviorDefinitionSource> actionSources, Map<String, BehaviorDefinitionSource> subscriptionSources,
      Map<String, String> executionAuthorities, List<BehaviorMemberSource> memberSources) {
    this(modelFullName, actions, events, eventSubscriptions, definitionConflicts, modelRevision,
        mutationEventPlans, actionSources, subscriptionSources, executionAuthorities, Map.of(), memberSources);
  }

  private BusinessModelBehavior(String modelFullName, List<ActionDefinition> actions, List<EventDefinition> events,
      List<EventSubscriptionDefinition> eventSubscriptions, List<String> definitionConflicts, String modelRevision,
      Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationEventPlans,
      Map<String, BehaviorDefinitionSource> actionSources, Map<String, BehaviorDefinitionSource> subscriptionSources,
      Map<String, String> executionAuthorities,
      Map<String, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan> featureQueries,
      List<BehaviorMemberSource> memberSources) {
    this.modelFullName = modelFullName;
    this.memberSources = List.copyOf(memberSources);
    this.declarationRevision = modelRevision;
    this.actionDefinitions = immutableCopy(actions, BehaviorDefinitionCopies::copyAction);
    this.eventDefinitions = immutableCopy(events, BehaviorDefinitionCopies::copy);
    this.eventSubscriptionDefinitions = immutableCopy(eventSubscriptions, BehaviorDefinitionCopies::copy);
    this.definitionConflicts = definitionConflicts == null ? List.of() : List.copyOf(definitionConflicts);
    this.actions = indexActions(this.actionDefinitions);
    Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    overrides.putAll(executionAuthorities);
    Map<String, String> authorities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.actions.keySet().forEach(name -> {
      String authority = overrides.getOrDefault(name, modelFullName + "." + name);
      if (authority == null || !authority.startsWith(modelFullName + ".") || authority.substring(modelFullName.length() + 1).isBlank()) {
        throw new IllegalArgumentException("Action execution authority must belong to its business model: " + name);
      }
      authorities.put(name, authority);
    });
    if (!this.actions.keySet().containsAll(executionAuthorities.keySet())) {
      throw new IllegalArgumentException("Execution authority references an unavailable Action");
    }
    this.executionAuthorities = Collections.unmodifiableMap(authorities);
    this.actionSources = definitionSources(this.actions.keySet(), modelFullName, "actions", actionSources);
    this.subscriptionSources = definitionSources(indexSubscriptions(this.eventSubscriptionDefinitions).keySet(),
        modelFullName, "eventSubscriptions", subscriptionSources);
    Map<String, com.ouroboros.businessmodel.action.BusinessActionInputPlan> plans = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.actions.forEach((name, action) -> plans.put(name,
        new com.ouroboros.businessmodel.action.BusinessActionInputPlan(action.getInputSchema(),
            action.getBinding() == null ? Map.of() : action.getBinding().getInputMapping(), this.actionSources.get(name))));
    this.inputPlans = Collections.unmodifiableMap(plans);
    Map<String, BusinessBehaviorMappingPlan> outputs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.actions.forEach((name, action) -> {
      if (action.getBinding() != null && action.getBinding().getOutputMapping() != null) {
        outputs.put(name, BusinessBehaviorMappingPlan.flowOutput(action.getBinding().getOutputMapping(), this.actionSources.get(name)));
      }
    });
    this.outputPlans = Collections.unmodifiableMap(outputs);
    var conditions = new TreeMap<String, com.ouroboros.businessmodel.action.BusinessActionPreconditionPlan>(String.CASE_INSENSITIVE_ORDER);
    this.actions.forEach((name, action) -> conditions.put(name,
        new com.ouroboros.businessmodel.action.BusinessActionPreconditionPlan(action.getPrecondition())));
    this.preconditions = Collections.unmodifiableMap(conditions);
    this.events = indexEvents(this.eventDefinitions);
    this.eventSubscriptions = indexSubscriptions(this.eventSubscriptionDefinitions);
    Map<String, BusinessBehaviorMappingPlan> subscriptionInputs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.eventSubscriptions.forEach((name, subscription) -> subscriptionInputs.put(name,
        BusinessBehaviorMappingPlan.subscriptionInput(subscription.getTarget() == null || subscription.getTarget().getInputMapping() == null ? Map.of() : subscription.getTarget().getInputMapping(), this.subscriptionSources.get(name))));
    this.subscriptionInputPlans = Collections.unmodifiableMap(subscriptionInputs);
    var mutationPlans = new TreeMap<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan>(String.CASE_INSENSITIVE_ORDER);
    mutationPlans.putAll(mutationEventPlans);
    this.mutationEventPlans = Collections.unmodifiableMap(mutationPlans);
    var queries = new TreeMap<String, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan>(String.CASE_INSENSITIVE_ORDER);
    featureQueries.forEach((name, plan) -> {
      var action = this.actions.get(name);
      if (!modelFullName.equals(plan.sourceModel()) || !name.equals(plan.kind().actionName()) || action == null
          || !Boolean.TRUE.equals(action.getReadOnly()) || action.getBinding() == null
          || !"handler".equals(action.getBinding().getKind())
          || !com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.HANDLER_NAME.equals(action.getBinding().getHandlerName())) {
        throw new IllegalArgumentException("Feature query projection does not match its declared Action: " + name);
      }
      queries.put(name, plan);
    });
    this.featureQueryPlans = Collections.unmodifiableMap(queries);
    this.modelRevision = queries.isEmpty() ? modelRevision : BusinessBehaviorContracts.schemaFingerprint(
        Map.of("revision", modelRevision == null ? "" : modelRevision,
            "featureQueries", com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(queries))));
  }

  public BusinessModelBehavior withFeatureQueryPlans(
      Map<String, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan> queries) {
    return featureQueryPlans.equals(queries) ? this : new BusinessModelBehavior(modelFullName, actionDefinitions, eventDefinitions,
        eventSubscriptionDefinitions, definitionConflicts, declarationRevision, mutationEventPlans, actionSources,
        subscriptionSources, executionAuthorities, queries, memberSources);
  }

  public Map<String, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan> getFeatureQueryPlans() { return featureQueryPlans; }

  public List<BehaviorMemberSource> getMemberSources() { return memberSources; }

  public String executionAuthorityName(String actionName) {
    String authority = executionAuthorities.get(actionName);
    if (authority == null) { throw new IllegalArgumentException("Action execution authority unavailable: " + actionName); }
    return authority;
  }

  public Map<String, String> getExecutionAuthorities() { return executionAuthorities; }

  public Map<String, BehaviorDefinitionSource> getActionSources() { return actionSources; }

  public Map<String, BehaviorDefinitionSource> getSubscriptionSources() { return subscriptionSources; }

  public BusinessBehaviorMappingPlan outputPlan(String actionName) { return outputPlans.get(actionName); }

  public BusinessBehaviorMappingPlan subscriptionInputPlan(String name) { return subscriptionInputPlans.get(name); }


  private static Map<String, BehaviorDefinitionSource> definitionSources(java.util.Set<String> names, String model,
                                      String field, Map<String, BehaviorDefinitionSource> sources) {
    Map<String, BehaviorDefinitionSource> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    names.forEach(name -> result.put(name, sources.getOrDefault(name,
        new BehaviorDefinitionSource(model, field + "." + name, null))));
    return Collections.unmodifiableMap(result);
  }

  public com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan mutationEventPlan(String actionName) {
    return mutationEventPlans.get(actionName);
  }

  public com.ouroboros.businessmodel.action.BusinessActionPreconditionPlan preconditionPlan(String actionName) {
    return preconditions.get(actionName);
  }

  public String getModelFullName() {
    return modelFullName;
  }

  public com.ouroboros.businessmodel.action.BusinessActionInputPlan inputPlan(String actionName) {
    return inputPlans.get(actionName);
  }

  public Map<String, ActionDefinition> getActions() {
    return immutableCopy(actions, BehaviorDefinitionCopies::copyAction);
  }

  public List<ActionDefinition> getActionDefinitions() {
    return immutableCopy(actionDefinitions, BehaviorDefinitionCopies::copyAction);
  }

  public Map<String, EventDefinition> getEvents() {
    return immutableCopy(events, BehaviorDefinitionCopies::copy);
  }

  public List<EventDefinition> getEventDefinitions() {
    return immutableCopy(eventDefinitions, BehaviorDefinitionCopies::copy);
  }

  public Map<String, EventSubscriptionDefinition> getEventSubscriptions() {
    return immutableCopy(eventSubscriptions, BehaviorDefinitionCopies::copy);
  }

  public List<EventSubscriptionDefinition> getEventSubscriptionDefinitions() {
    return immutableCopy(eventSubscriptionDefinitions, BehaviorDefinitionCopies::copy);
  }

  public List<String> getDefinitionConflicts() {
    return definitionConflicts;
  }

  public String getModelRevision() {
    return modelRevision;
  }

  private static Map<String, ActionDefinition> indexActions(List<ActionDefinition> definitions) {
    Map<String, ActionDefinition> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (definitions != null) {
      definitions.stream().filter(definition -> definition != null && definition.getName() != null)
          .forEach(definition -> result.put(definition.getName(), definition));
    }
    return result;
  }

  private static Map<String, EventDefinition> indexEvents(List<EventDefinition> definitions) {
    Map<String, EventDefinition> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (definitions != null) {
      definitions.stream().filter(definition -> definition != null && definition.getName() != null)
          .forEach(definition -> result.put(definition.getName(), definition));
    }
    return result;
  }

  private static Map<String, EventSubscriptionDefinition> indexSubscriptions(
      List<EventSubscriptionDefinition> definitions) {
    Map<String, EventSubscriptionDefinition> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (definitions != null) {
      definitions.stream().filter(definition -> definition != null && definition.getName() != null)
          .forEach(definition -> result.put(definition.getName(), definition));
    }
    return result;
  }

  private static <T> List<T> immutableCopy(List<T> values, Function<T, T> copy) {
    if (values == null) {
      return List.of();
    }
    List<T> result = new ArrayList<>(values.size());
    values.forEach(value -> result.add(copy.apply(value)));
    return Collections.unmodifiableList(result);
  }

  private static <T> Map<String, T> immutableCopy(Map<String, T> values, Function<T, T> copy) {
    Map<String, T> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    values.forEach((name, value) -> result.put(name, copy.apply(value)));
    return Collections.unmodifiableMap(result);
  }
}
