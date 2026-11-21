package com.ouroboros.businessmodel.behavior;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Discriminated target: action uses modelFullName/actionName; listener uses listenerName/config. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventSubscriptionTarget {
  private String kind;
  private String modelFullName;
  private String actionName;
  private String listenerName;
  private Map<String, Object> config;
  private Map<String, Object> inputMapping;

  public String getKind() {
    return kind;
  }
  public void setKind(String value) {
    kind = value;
  }

  public String getModelFullName() {
    return modelFullName;
  }
  public void setModelFullName(String value) {
    modelFullName = value;
  }

  public String getActionName() {
    return actionName;
  }
  public void setActionName(String value) {
    actionName = value;
  }

  public String getListenerName() {
    return listenerName;
  }
  public void setListenerName(String value) {
    listenerName = value;
  }

  public Map<String, Object> getConfig() {
    return config;
  }
  public void setConfig(Map<String, Object> value) {
    config = value;
  }

  public Map<String, Object> getInputMapping() {
    return inputMapping;
  }
  public void setInputMapping(Map<String, Object> value) {
    inputMapping = value;
  }
}
