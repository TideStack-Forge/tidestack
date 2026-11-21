package com.ouroboros.businessmodel.behavior;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.businessmodel.meta.MetaWithExtraProps;
import com.ouroboros.businessmodel.event.BusinessEventExecutionMode;

/** Consumer-owned reaction to one event. Execution mode is explicit; identity is resolved by the platform. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventSubscriptionDefinition extends MetaWithExtraProps {
  private String name;
  private String title;
  private String description;
  private Boolean isEnabled;
  private EventReference sourceEvent = new EventReference();
  private Object filter;
  private BusinessEventExecutionMode executionMode;
  private EventSubscriptionTarget target = new EventSubscriptionTarget();

  public String getName() {
    return name;
  }
  public void setName(String value) {
    name = value;
  }

  public String getTitle() {
    return title;
  }
  public void setTitle(String value) {
    title = value;
  }

  public String getDescription() {
    return description;
  }
  public void setDescription(String value) {
    description = value;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }
  public void setIsEnabled(Boolean value) {
    isEnabled = value;
  }

  public EventReference getSourceEvent() {
    return sourceEvent;
  }
  public void setSourceEvent(EventReference value) {
    sourceEvent = value;
  }

  public Object getFilter() {
    return filter;
  }
  public void setFilter(Object value) {
    filter = value;
  }

  public BusinessEventExecutionMode getExecutionMode() {
    return executionMode;
  }
  public void setExecutionMode(BusinessEventExecutionMode value) {
    executionMode = value;
  }

  public EventSubscriptionTarget getTarget() {
    return target;
  }
  public void setTarget(EventSubscriptionTarget value) {
    target = value;
  }
}
