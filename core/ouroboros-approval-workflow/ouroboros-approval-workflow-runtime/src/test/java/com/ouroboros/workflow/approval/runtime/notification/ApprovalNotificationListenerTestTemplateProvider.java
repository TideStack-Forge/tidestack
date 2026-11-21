package com.ouroboros.workflow.approval.runtime.notification;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;

public class ApprovalNotificationListenerTestTemplateProvider implements NotificationTemplateProvider {
  static final List<String> TEMPLATE_NAMES = Arrays.asList(
      "tpl-task-created",
      "tpl-timeout-remind",
      "tpl-instance-completed",
      "tpl-instance-rejected");

  @Override
  public Optional<NotificationTemplate> getTemplate(String name) {
    if (!TEMPLATE_NAMES.contains(name)) {
      return Optional.empty();
    }
    DefaultNotificationTemplate template = new DefaultNotificationTemplate();
    template.setName(name);
    template.setTitle(name);
    template.setEnabled(true);
    return Optional.of(template);
  }

  @Override
  public List<NotificationTemplate> getAllTemplate() {
    return Arrays.asList(
        buildTemplate("tpl-task-created"),
        buildTemplate("tpl-timeout-remind"),
        buildTemplate("tpl-instance-completed"),
        buildTemplate("tpl-instance-rejected"));
  }

  private NotificationTemplate buildTemplate(String name) {
    DefaultNotificationTemplate template = new DefaultNotificationTemplate();
    template.setName(name);
    template.setTitle(name);
    template.setEnabled(true);
    return template;
  }
}
