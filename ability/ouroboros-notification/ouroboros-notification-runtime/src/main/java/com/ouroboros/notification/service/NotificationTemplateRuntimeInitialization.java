package com.ouroboros.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;

/** Uses the original lifecycle: schema migration completes before model-refreshed is published. */
@Component
public class NotificationTemplateRuntimeInitialization {
  private final NotificationTemplateService templates;

  public NotificationTemplateRuntimeInitialization(NotificationTemplateService templates) { this.templates = templates; }

  @EventListener
  public void onModelRefreshed(OuroborosModelRefreshedEvent event) {
    templates.initializeRuntimeManagement();
  }
}
