package com.ouroboros.businessmodel.behavior;

import com.ouroboros.businessmodel.meta.MetaWithExtraProps;

/**
 * Stable reference to an event definition.
 */
public class EventReference extends MetaWithExtraProps {
  private String modelFullName;
  private String eventName;

  public String getModelFullName() {
    return modelFullName;
  }

  public void setModelFullName(String modelFullName) {
    this.modelFullName = modelFullName;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }
}
