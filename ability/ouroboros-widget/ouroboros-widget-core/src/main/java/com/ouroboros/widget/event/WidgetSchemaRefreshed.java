package com.ouroboros.widget.event;

import org.springframework.context.ApplicationEvent;

public class WidgetSchemaRefreshed extends ApplicationEvent {
  public WidgetSchemaRefreshed(Object source) {
    super(source);
  }
}
