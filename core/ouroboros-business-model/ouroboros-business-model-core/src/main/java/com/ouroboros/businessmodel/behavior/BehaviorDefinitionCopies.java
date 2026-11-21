package com.ouroboros.businessmodel.behavior;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BehaviorDefinitionCopies {
  private BehaviorDefinitionCopies() {
  }

  public static ActionDefinition copyAction(ActionDefinition source) {
    if (source == null) {
      return null;
    }
    ActionDefinition copy = new ActionDefinition();
    copy.setName(source.getName());
    copy.setTitle(source.getTitle());
    copy.setDescription(source.getDescription());
    copy.setIsEnabled(source.getIsEnabled());
    copy.setInputSchema(copyMap(source.getInputSchema()));
    copy.setOutputSchema(copyMap(source.getOutputSchema()));
    copy.setBinding(copyBinding(source.getBinding()));
    copy.setReadOnly(source.getReadOnly());
    copy.setAuthorization(copyValue(source.getAuthorization()));
    copy.setPrecondition(copyValue(source.getPrecondition()));
    copy.setEmits(source.getEmits().stream().map(BehaviorDefinitionCopies::copy).toList());
    return copy;
  }

  static ActionBinding copyBinding(ActionBinding source) {
    if (source == null) {
      return null;
    }
    ActionBinding copy = new ActionBinding();
    copy.setKind(source.getKind());
    copy.setFlowName(source.getFlowName());
    copy.setHandlerName(source.getHandlerName());
    copy.setInputMapping(copyMap(source.getInputMapping()));
    copy.setRequiresHttpRequest(source.isRequiresHttpRequest());
    copy.setOutputMapping(copyValue(source.getOutputMapping()));
    return copy;
  }

  public static EventDefinition copy(EventDefinition source) {
    if (source == null) {
      return null;
    }
    EventDefinition copy = new EventDefinition();
    copy.setName(source.getName());
    copy.setTitle(source.getTitle());
    copy.setDescription(source.getDescription());
    copy.setOccurrenceCondition(source.getOccurrenceCondition());
    copy.setSubject(source.getSubject());
    copy.setRequireUnfilteredModelRead(source.isRequireUnfilteredModelRead());
    copy.setPayloadSchema(copyMap(source.getPayloadSchema()));
    copy.setAllowedProducerKinds(new ArrayList<>(source.getAllowedProducerKinds()));
    return copy;
  }

  public static EventSubscriptionDefinition copy(EventSubscriptionDefinition source) {
    if (source == null) {
      return null;
    }
    EventSubscriptionDefinition copy = new EventSubscriptionDefinition();
    copy.setName(source.getName());
    copy.setTitle(source.getTitle());
    copy.setDescription(source.getDescription());
    copy.setIsEnabled(source.getIsEnabled());
    copy.setSourceEvent(copy(source.getSourceEvent()));
    copy.setFilter(copyValue(source.getFilter()));
    copy.setExecutionMode(source.getExecutionMode());
    if (source.getTarget() == null) {
      copy.setTarget(null);
    } else {
      var target = new EventSubscriptionTarget();
      target.setKind(source.getTarget().getKind());
      target.setModelFullName(source.getTarget().getModelFullName());
      target.setActionName(source.getTarget().getActionName());
      target.setListenerName(source.getTarget().getListenerName());
      target.setConfig(source.getTarget().getConfig() == null ? null : copyMap(source.getTarget().getConfig()));
      target.setInputMapping(source.getTarget().getInputMapping() == null ? null : copyMap(source.getTarget().getInputMapping()));
      copy.setTarget(target);
    }
    return copy;
  }

  private static ActionBinding copy(ActionBinding source) {
    return copyBinding(source);
  }

  private static EventReference copy(EventReference source) {
    if (source == null) {
      return null;
    }
    EventReference copy = new EventReference();
    copy.setModelFullName(source.getModelFullName());
    copy.setEventName(source.getEventName());
    return copy;
  }

  private static Map<String, Object> copyMap(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(key, copyValue(value)));
    return copy;
  }

  private static Object copyValue(Object source) {
    if (source instanceof Map<?, ?> sourceMap) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      sourceMap.forEach((key, value) -> copy.put(key, copyValue(value)));
      return copy;
    }
    if (source instanceof List<?> sourceList) {
      List<Object> copy = new ArrayList<>(sourceList.size());
      sourceList.forEach(value -> copy.add(copyValue(value)));
      return copy;
    }
    return source;
  }
}
