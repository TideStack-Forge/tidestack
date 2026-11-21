package com.ouroboros.widget;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.widget.event.WidgetSchemaRefreshed;

@ComponentScan
public class WidgetRuntimeAutoConfiguration {
  @Autowired
  ApplicationEventPublisher applicationEventPublisher;

  @EventListener(OuroborosContextRefreshedEvent.class)
  public void onOuroborosContextRefreshedEvent(OuroborosContextRefreshedEvent event) {
    applicationEventPublisher.publishEvent(new WidgetSchemaRefreshed(this));
  }
  @EventListener(WidgetSchemaRefreshed.class)
  public void onWidgetSchemaRefreshed() {
    GeneratorWidgetDefinitionProvider.refresh();
  }
}
