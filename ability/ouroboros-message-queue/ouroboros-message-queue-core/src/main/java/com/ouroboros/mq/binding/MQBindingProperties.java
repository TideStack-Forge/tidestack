package com.ouroboros.mq.binding;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// 根据 bindingType 反序列化
//   consumer: MQConsumerProperties
//   producer: MQProducerProperties
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "bindingType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MQConsumerProperties.class, name = "consumer"),
    @JsonSubTypes.Type(value = MQProducerProperties.class, name = "producer")
})
public abstract class MQBindingProperties<T extends MQBindingProperties<T>> {
  private String bindingName;
  private String destination;
  private String binder;
  private String group;
  private String contentType;
  private Boolean broadcast;
  private String routingKey;
  private String description;
  private Map<String, Object> extraProps;

  public MQBindingProperties() {
    extraProps = new HashMap<>();
  }

  public String getBindingName() {
    return bindingName;
  }

  public T setBindingName(String bindingName) {
    this.bindingName = bindingName;
    return (T) this;
  }

  public String getDescription() {
    return description;
  }

  public MQBindingProperties<T> setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getDestination() {
    return destination;
  }

  public T setDestination(String destination) {
    this.destination = destination;
    return (T) this;
  }

  public String getBinder() {
    return binder;
  }

  public T setBinder(String binder) {
    this.binder = binder;
    return (T) this;
  }

  public String getGroup() {
    return group;
  }

  public T setGroup(String group) {
    this.group = group;
    return (T) this;
  }

  public String getContentType() {
    return contentType;
  }

  public T setContentType(String contentType) {
    this.contentType = contentType;
    return (T) this;
  }

  public Boolean getBroadcast() {
    return broadcast;
  }

  public T setBroadcast(Boolean broadcast) {
    this.broadcast = broadcast;
    return (T) this;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public T setRoutingKey(String routingKey) {
    this.routingKey = routingKey;
    return (T) this;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public T setExtraProps(Map<String, Object> extraProps) {
    this.extraProps.putAll(extraProps);
    return (T) this;
  }

  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  public T fillNullsFrom(MQBindingProperties<?> source) {
    if (source == null) {
      return (T) this;
    }
    if (bindingName == null) {
      bindingName = source.getBindingName();
    }
    if (destination == null) {
      destination = source.getDestination();
    }
    if (binder == null) {
      binder = source.getBinder();
    }
    if (group == null) {
      group = source.getGroup();
    }
    if (contentType == null) {
      contentType = source.getContentType();
    }
    if (broadcast == null) {
      broadcast = source.getBroadcast();
    }
    if (routingKey == null) {
      routingKey = source.getRoutingKey();
    }
    if (description == null) {
      description = source.getDescription();
    }
    source.getExtraProps().forEach(extraProps::putIfAbsent);
    fillTypedNullsFrom(source);
    return (T) this;
  }

  public Map<String, Object> toHydratedMap() {
    Map<String, Object> hydrated = new HashMap<>(extraProps);
    putHydratedProp(hydrated, "bindingName", bindingName);
    putHydratedProp(hydrated, "destination", destination);
    putHydratedProp(hydrated, "binder", binder);
    putHydratedProp(hydrated, "group", group);
    putHydratedProp(hydrated, "contentType", contentType);
    putHydratedProp(hydrated, "broadcast", broadcast);
    putHydratedProp(hydrated, "routingKey", routingKey);
    putHydratedProp(hydrated, "description", description);
    addTypedHydratedProps(hydrated);
    return hydrated;
  }

  protected void addTypedHydratedProps(Map<String, Object> hydrated) {
  }

  protected void fillTypedNullsFrom(MQBindingProperties<?> source) {
  }

  protected void putHydratedProp(Map<String, Object> hydrated, String key, Object value) {
    if (value != null) {
      hydrated.put(key, value);
    }
  }

  @JsonAnySetter
  public T setExtraProp(String key, Object value) {
    if (!"bindingType".equals(key)) {
      extraProps.put(key, value);
    }
    return (T) this;
  }

  public T removeExtraProp(String key) {
    extraProps.remove(key);
    return (T) this;
  }
}
