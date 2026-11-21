package com.ouroboros.mq.dev.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.mq.binding.MQProducerProperties;

public class MQBindingModel {
  private String bindingName;
  private String bindingType;
  private String destination;
  private String binder;
  private String group;
  private String contentType;
  private Boolean broadcast;
  private String routingKey;
  private String description;
  private String flowName;
  private Map<String, Object> extraProps;
  @JsonIgnore
  private MQBindingProperties<?> bindingProperties;

  public MQBindingModel(MQBindingProperties<?> bindingProperties) {
    this.bindingProperties = bindingProperties;
    this.bindingName = bindingProperties.getBindingName();
    this.description = bindingProperties.getDescription();
    this.destination = bindingProperties.getDestination();
    this.binder = bindingProperties.getBinder();
    this.group = bindingProperties.getGroup();
    this.contentType = bindingProperties.getContentType();
    this.broadcast = bindingProperties.getBroadcast();
    this.routingKey = bindingProperties.getRoutingKey();
    this.extraProps = new LinkedHashMap<>(bindingProperties.getExtraProps());
    if (bindingProperties instanceof MQConsumerProperties consumer) {
      this.flowName = consumer.getFlowName();
      this.bindingType = "consumer";
    } else {
      this.bindingType = "producer";
    }
  }

  public MQBindingModel() {
    extraProps = new HashMap<>();
  }

  public String getBindingName() {
    return bindingName;
  }

  public MQBindingModel setBindingName(String bindingName) {
    this.bindingName = bindingName;
    return this;
  }

  public String getBindingType() {
    return bindingType;
  }

  public MQBindingModel setBindingType(String bindingType) {
    this.bindingType = bindingType;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public MQBindingModel setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getDestination() {
    return destination;
  }

  public MQBindingModel setDestination(String destination) {
    this.destination = destination;
    return this;
  }

  public String getFlowName() {
    return flowName;
  }

  public MQBindingModel setFlowName(String flowName) {
    this.flowName = flowName;
    return this;
  }

  public String getBinder() {
    return binder;
  }

  public MQBindingModel setBinder(String binder) {
    this.binder = binder;
    return this;
  }

  public String getGroup() {
    return group;
  }

  public MQBindingModel setGroup(String group) {
    this.group = group;
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public MQBindingModel setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Boolean getBroadcast() {
    return broadcast;
  }

  public MQBindingModel setBroadcast(Boolean broadcast) {
    this.broadcast = broadcast;
    return this;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public MQBindingModel setRoutingKey(String routingKey) {
    this.routingKey = routingKey;
    return this;
  }

  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public MQBindingModel setExtraProps(Map<String, Object> extraProps) {
    this.extraProps.putAll(extraProps);
    return this;
  }

  public MQBindingProperties<?> toMQBindingProperties() {
    MQBindingProperties<?> properties;
    if ("consumer".equalsIgnoreCase(bindingType)) {
      properties = new MQConsumerProperties().setFlowName(flowName);
    } else {
      properties = new MQProducerProperties();
    }
    properties.setBindingName(bindingName)
        .setDescription(description)
        .setDestination(destination)
        .setBinder(binder)
        .setGroup(group)
        .setContentType(contentType)
        .setBroadcast(broadcast)
        .setRoutingKey(routingKey)
        .setExtraProps(extraProps);
    return properties;
  }
}
