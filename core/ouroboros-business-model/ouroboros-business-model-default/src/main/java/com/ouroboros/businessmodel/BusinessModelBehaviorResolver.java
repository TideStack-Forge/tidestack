package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.BehaviorMemberSource;
import com.ouroboros.businessmodel.behavior.BusinessActionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessEventSubscriptionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorBindingContributor;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;
import com.ouroboros.businessmodel.behavior.BusinessModelEventSubscriptionBindingContributor;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionCopies;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.SPI;

final class BusinessModelBehaviorResolver {
  private BusinessModelBehaviorResolver() {
  }

  static BusinessModelBehavior resolve(String modelFullName,
                                       BusinessModelMeta modelMeta,
                                       List<ActionDefinition> declaredActions,
                                       LegacyBusinessActionNormalizer.NormalizedBehavior normalized,
                                       List<EventDefinition> declaredEvents,
                                       List<EventSubscriptionDefinition> declaredSubscriptions) {
    return resolve(modelFullName, modelMeta, declaredActions, normalized, declaredEvents, declaredSubscriptions, List.of());
  }

  static BusinessModelBehavior resolve(String modelFullName, BusinessModelMeta modelMeta,
      List<ActionDefinition> declaredActions, LegacyBusinessActionNormalizer.NormalizedBehavior normalized,
      List<EventDefinition> declaredEvents, List<EventSubscriptionDefinition> declaredSubscriptions,
      List<com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan> featurePlans) {
    Map<String, BehaviorDefinitionSource> actionOrigins = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, BehaviorDefinitionSource> subscriptionOrigins = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    String metadataVersion = BusinessModelMetaParser.isCurrent(modelMeta) ? "2.0" : null;
    declaredActions.stream().filter(action -> action != null && action.getName() != null).forEach(action -> actionOrigins.put(action.getName(),
        new BehaviorDefinitionSource(modelFullName, "actions." + action.getName() + ".binding", metadataVersion)));
    declaredSubscriptions.stream().filter(subscription -> subscription != null && subscription.getName() != null).forEach(subscription -> subscriptionOrigins.put(subscription.getName(),
        new BehaviorDefinitionSource(modelFullName, "eventSubscriptions." + subscription.getName(), metadataVersion)));
    normalized.actions().forEach(action -> actionOrigins.putIfAbsent(action.getName(),
        new BehaviorDefinitionSource(modelFullName, "projection.actions." + action.getName(), "2.0")));
    var memberSources = new ArrayList<BehaviorMemberSource>(normalized.sources());
    addSources(memberSources, declaredActions, "action", "actions", "metadata", modelFullName, metadataVersion, ActionDefinition::getName);
    addSources(memberSources, declaredEvents, "event", "events", "metadata", modelFullName, metadataVersion, EventDefinition::getName);
    addSources(memberSources, declaredSubscriptions, "subscription", "eventSubscriptions", "metadata", modelFullName,
        metadataVersion, EventSubscriptionDefinition::getName);
    List<ActionDefinition> legacyActions = normalized.actions();
    List<ActionDefinition> actions = declaredActions.stream().map(BehaviorDefinitionCopies::copyAction)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    legacyActions.stream().map(BehaviorDefinitionCopies::copyAction).forEach(actions::add);
    List<EventDefinition> events = declaredEvents.stream().map((EventDefinition event) -> BehaviorDefinitionCopies.copy(event))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    List<EventSubscriptionDefinition> subscriptions = declaredSubscriptions.stream()
        .map((EventSubscriptionDefinition subscription) -> BehaviorDefinitionCopies.copy(subscription))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Map<String, String> actionSources = sourcesFor(declaredActions, "metadata", ActionDefinition::getName);
    Map<String, String> eventSources = sourcesFor(declaredEvents, "metadata", EventDefinition::getName);
    Map<String, String> subscriptionSources = sourcesFor(
        declaredSubscriptions, "metadata", EventSubscriptionDefinition::getName);
    List<String> conflicts = new ArrayList<>();
    normalized.events().stream().map(BehaviorDefinitionCopies::copy).forEach(events::add);
    collectConflicts("events", normalized.events(), "default mutation projection", eventSources, EventDefinition::getName, conflicts);
    collectConflicts("actions", legacyActions, "legacy API configuration",
        actionSources, ActionDefinition::getName, conflicts);

    for (BusinessModelBehaviorContributor contributor : SPI
        .getCachedSortedServiceStream(BusinessModelBehaviorContributor.class)
        .toList()) {
      List<BusinessModelBehaviorContribution> contributions = contributor.getContributions(modelMeta);
      if (contributions == null) {
        throw new IllegalStateException("BusinessModelBehaviorContributor returned null contributions: "
            + contributor.getClass().getName());
      }
      for (BusinessModelBehaviorContribution contribution : contributions) {
        if (contribution == null) {
          throw new IllegalStateException("BusinessModelBehaviorContributor returned a null contribution: "
              + contributor.getClass().getName());
        }
        if (modelFullName == null || !modelFullName.equalsIgnoreCase(contribution.getModelFullName())) {
          continue;
        }
        addSources(memberSources, contribution.getActions(), "action", "actions", contribution.getSourceKind(),
            contribution.getSource(), contribution.getFormatVersion(), ActionDefinition::getName);
        addSources(memberSources, contribution.getEvents(), "event", "events", contribution.getSourceKind(),
            contribution.getSource(), contribution.getFormatVersion(), EventDefinition::getName);
        addSources(memberSources, contribution.getEventSubscriptions(), "subscription", "eventSubscriptions", contribution.getSourceKind(),
            contribution.getSource(), contribution.getFormatVersion(), EventSubscriptionDefinition::getName);
        memberSources.addAll(contribution.getMemberSources());
        contribution.getActions().stream().filter(action -> action != null && action.getName() != null).forEach(action -> actionOrigins.putIfAbsent(action.getName(),
            new BehaviorDefinitionSource(contribution.getSource(), "actions." + action.getName() + ".binding", contribution.getFormatVersion())));
        contribution.getEventSubscriptions().stream().filter(subscription -> subscription != null && subscription.getName() != null).forEach(subscription -> subscriptionOrigins.putIfAbsent(subscription.getName(),
            new BehaviorDefinitionSource(contribution.getSource(), "eventSubscriptions." + subscription.getName(), contribution.getFormatVersion())));
        contribution.getActions().stream().map(BehaviorDefinitionCopies::copyAction).forEach(actions::add);
        contribution.getEvents().stream()
            .map((EventDefinition event) -> BehaviorDefinitionCopies.copy(event)).forEach(events::add);
        contribution.getEventSubscriptions().stream()
            .map((EventSubscriptionDefinition subscription) -> BehaviorDefinitionCopies.copy(subscription))
            .forEach(subscriptions::add);
        collectConflicts("actions", contribution.getActions(), contribution.getSource(),
            actionSources, ActionDefinition::getName, conflicts);
        collectConflicts("events", contribution.getEvents(), contribution.getSource(),
            eventSources, EventDefinition::getName, conflicts);
        collectConflicts("eventSubscriptions", contribution.getEventSubscriptions(), contribution.getSource(),
            subscriptionSources, EventSubscriptionDefinition::getName, conflicts);
      }
    }

    Map<String, ActionDefinition> actionsByName = new HashMap<>();
    actions.stream().filter(action -> action != null && action.getName() != null)
        .forEach(action -> actionsByName.putIfAbsent(normalize(action.getName()), action));
    Map<String, String> bindingSources = new HashMap<>();
    for (BusinessModelBehaviorBindingContributor contributor : SPI
        .getCachedSortedServiceStream(BusinessModelBehaviorBindingContributor.class)
        .toList()) {
      List<BusinessActionBindingContribution> contributions = contributor.getBindingContributions();
      if (contributions == null) {
        throw new IllegalStateException("BusinessModelBehaviorBindingContributor returned null contributions: "
            + contributor.getClass().getName());
      }
      for (BusinessActionBindingContribution contribution : contributions) {
        if (contribution == null) {
          throw new IllegalStateException("BusinessModelBehaviorBindingContributor returned a null contribution: "
              + contributor.getClass().getName());
        }
        if (!modelFullName.equalsIgnoreCase(contribution.getModelFullName())) {
          continue;
        }
        memberSources.add(new BehaviorMemberSource("action", contribution.getActionName(), "binding", "contribution",
            new BehaviorDefinitionSource(contribution.getSource(), "actions." + contribution.getActionName() + ".binding", contribution.getFormatVersion())));
        ActionDefinition action = actionsByName.get(normalize(contribution.getActionName()));
        if (action == null) {
          conflicts.add("actions." + contribution.getActionName() + ".binding.action-not-found: "
              + contribution.getSource());
        } else if (action.getBinding() != null) {
          String previousSource = bindingSources.getOrDefault(normalize(contribution.getActionName()),
              actionSources.getOrDefault(normalize(contribution.getActionName()), "metadata"));
          conflicts.add("actions." + contribution.getActionName() + ".binding.duplicate: "
              + previousSource + " <> " + contribution.getSource());
        } else {
          action.setBinding(contribution.getBinding());
          actionOrigins.put(action.getName(), new BehaviorDefinitionSource(contribution.getSource(),
              "actions." + action.getName() + ".binding", contribution.getFormatVersion()));
          bindingSources.put(normalize(contribution.getActionName()), contribution.getSource());
        }
      }
    }

    Map<String, EventSubscriptionDefinition> subscriptionsByName = new HashMap<>();
    subscriptions.stream().filter(subscription -> subscription != null && subscription.getName() != null)
        .forEach(subscription -> subscriptionsByName.putIfAbsent(normalize(subscription.getName()), subscription));
    Map<String, String> listenerBindingSources = new HashMap<>();
    for (BusinessModelEventSubscriptionBindingContributor contributor : SPI
        .getCachedSortedServiceStream(BusinessModelEventSubscriptionBindingContributor.class)
        .toList()) {
      List<BusinessEventSubscriptionBindingContribution> contributions =
          contributor.getEventSubscriptionBindingContributions();
      if (contributions == null) {
        throw new IllegalStateException("BusinessModelEventSubscriptionBindingContributor returned null contributions: "
            + contributor.getClass().getName());
      }
      for (BusinessEventSubscriptionBindingContribution contribution : contributions) {
        if (contribution == null) {
          throw new IllegalStateException("BusinessModelEventSubscriptionBindingContributor returned a null contribution: "
              + contributor.getClass().getName());
        }
        if (!modelFullName.equalsIgnoreCase(contribution.consumerModelFullName())) {
          continue;
        }
        memberSources.add(new BehaviorMemberSource("subscription", contribution.subscriptionName(), "listenerBinding", "contribution",
            new BehaviorDefinitionSource(contribution.source(), "eventSubscriptions." + contribution.subscriptionName() + ".listener", contribution.formatVersion())));
        String key = normalize(contribution.subscriptionName());
        EventSubscriptionDefinition subscription = subscriptionsByName.get(key);
        if (subscription == null) {
          conflicts.add("eventSubscriptions." + contribution.subscriptionName()
              + ".listener.subscription-not-found: " + contribution.source());
          continue;
        }
        String previousBindingSource = listenerBindingSources.putIfAbsent(key, contribution.source());
        String configuredListenerName = subscription.getTarget().getListenerName();
        if (previousBindingSource != null) {
          conflicts.add("eventSubscriptions." + contribution.subscriptionName()
              + ".listener.duplicate: " + previousBindingSource + " <> " + contribution.source());
        } else if (configuredListenerName == null || configuredListenerName.isBlank()) {
          subscription.getTarget().setKind("listener");
          subscription.getTarget().setListenerName(contribution.listenerName());
        } else if (!configuredListenerName.equalsIgnoreCase(contribution.listenerName())) {
          conflicts.add("eventSubscriptions." + contribution.subscriptionName()
              + ".listener.binding-conflict: metadata <> " + contribution.source());
        }
      }
    }

    Map<String, String> executionAuthorities = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, String> authorityOverrides = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    authorityOverrides.putAll(normalized.executionAuthorities());
    actions.stream().filter(action -> action != null && action.getName() != null).forEach(action ->
        executionAuthorities.put(action.getName(), authorityOverrides.getOrDefault(
            action.getName(), modelFullName + "." + action.getName())));
    if (!executionAuthorities.keySet().containsAll(authorityOverrides.keySet())) {
      throw new IllegalArgumentException("Execution authority references an unavailable Action");
    }
    featurePlans.stream().filter(plan -> plan.getWriteEffect() != null).forEach(plan ->
        actions.stream().filter(action -> !Boolean.TRUE.equals(action.getReadOnly())).forEach(action ->
            memberSources.add(new BehaviorMemberSource("action", action.getName(), "effect", "generated",
                new BehaviorDefinitionSource(modelFullName, plan.getConfigurationPath(), "2.0"), plan.getWriteEffect()))));
    String modelRevision = BusinessModelRevision.compute(modelMeta, actions, events, subscriptions, normalized.plans(), actionOrigins, subscriptionOrigins, executionAuthorities, memberSources);
    return new BusinessModelBehavior(modelFullName, actions, events, subscriptions, conflicts, modelRevision, normalized.plans(), actionOrigins, subscriptionOrigins, executionAuthorities, memberSources);
  }

  private static <T> void addSources(List<BehaviorMemberSource> sources, List<T> definitions, String type,
      String path, String kind, String resource, String version, java.util.function.Function<T, String> name) {
    definitions.stream().filter(definition -> definition != null && name.apply(definition) != null)
        .forEach(definition -> sources.add(new BehaviorMemberSource(type, name.apply(definition), "definition", kind,
            new BehaviorDefinitionSource(resource, path + "." + name.apply(definition), version))));
  }

  private static <T> Map<String, String> sourcesFor(List<T> definitions,
                                                     String source,
                                                     java.util.function.Function<T, String> name) {
    Map<String, String> sources = new HashMap<>();
    definitions.forEach(definition -> {
      if (definition != null && name.apply(definition) != null) {
        sources.putIfAbsent(normalize(name.apply(definition)), source);
      }
    });
    return sources;
  }

  private static <T> void collectConflicts(String path,
                                           List<T> definitions,
                                           String source,
                                           Map<String, String> sources,
                                           java.util.function.Function<T, String> name,
                                           List<String> conflicts) {
    definitions.forEach(definition -> {
      if (definition == null || name.apply(definition) == null) {
        return;
      }
      String memberName = name.apply(definition);
      String previousSource = sources.putIfAbsent(normalize(memberName), source);
      if (previousSource != null && !previousSource.equals(source)) {
        conflicts.add(path + "." + memberName + ".duplicate: " + previousSource + " <> " + source);
      }
    });
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
