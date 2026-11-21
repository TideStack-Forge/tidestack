package com.ouroboros.businessmodel.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete behavior definitions contributed for one business model.
 */
public final class BusinessModelBehaviorContribution {
  private final String modelFullName;
  private final String source;
  private final String sourceKind;
  private final String formatVersion;
  private final List<ActionDefinition> actions;
  private final List<EventDefinition> events;
  private final List<EventSubscriptionDefinition> eventSubscriptions;
  private final List<BehaviorMemberSource> memberSources;

  public BusinessModelBehaviorContribution(String modelFullName,
                                           String source,
                                           List<ActionDefinition> actions,
                                           List<EventDefinition> events,
                                           List<EventSubscriptionDefinition> eventSubscriptions) {
    this(modelFullName, source, actions, events, eventSubscriptions, null);
  }

  public BusinessModelBehaviorContribution(String modelFullName, String source, List<ActionDefinition> actions,
                                           List<EventDefinition> events,
                                           List<EventSubscriptionDefinition> eventSubscriptions, String formatVersion) {
    this(modelFullName, source, actions, events, eventSubscriptions, formatVersion, "contribution");
  }

  public BusinessModelBehaviorContribution(String modelFullName, String source, List<ActionDefinition> actions,
      List<EventDefinition> events, List<EventSubscriptionDefinition> eventSubscriptions, String formatVersion, String sourceKind) {
    this(modelFullName, source, actions, events, eventSubscriptions, formatVersion, sourceKind, List.of());
  }

  public BusinessModelBehaviorContribution(String modelFullName, String source, List<ActionDefinition> actions,
      List<EventDefinition> events, List<EventSubscriptionDefinition> eventSubscriptions, String formatVersion, String sourceKind,
      List<BehaviorMemberSource> memberSources) {
    if (!List.of("contribution", "generated").contains(sourceKind)) {
      throw new IllegalArgumentException("Unsupported behavior contribution source kind: " + sourceKind);
    }
    this.sourceKind = sourceKind;
    new BehaviorDefinitionSource(source, "contribution", formatVersion);
    this.formatVersion = formatVersion;
    this.modelFullName = Objects.requireNonNull(modelFullName, "modelFullName");
    if (modelFullName.isBlank()) {
      throw new IllegalArgumentException("modelFullName must not be blank");
    }
    this.source = Objects.requireNonNull(source, "source");
    if (source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
    this.actions = immutableCopy(actions);
    this.events = immutableCopy(events);
    this.eventSubscriptions = immutableCopy(eventSubscriptions);
    this.memberSources = List.copyOf(memberSources);
  }

  public String getModelFullName() {
    return modelFullName;
  }

  public String getFormatVersion() { return formatVersion; }

  public String getSourceKind() { return sourceKind; }

  public List<BehaviorMemberSource> getMemberSources() { return memberSources; }

  public String getSource() {
    return source;
  }

  public List<ActionDefinition> getActions() {
    return actions;
  }

  public List<EventDefinition> getEvents() {
    return events;
  }

  public List<EventSubscriptionDefinition> getEventSubscriptions() {
    return eventSubscriptions;
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
  }
}
