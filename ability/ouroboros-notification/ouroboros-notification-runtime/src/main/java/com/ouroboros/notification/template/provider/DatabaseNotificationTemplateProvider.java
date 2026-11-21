package com.ouroboros.notification.template.provider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.springframework.stereotype.Component;

import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;

/**
 * @author liansz
 */
@Component
public class DatabaseNotificationTemplateProvider {

  private final NotificationTemplateService service;

  public DatabaseNotificationTemplateProvider(NotificationTemplateService service) {
    this.service = service;
    Proxy.provider = this;
  }

  public Optional<NotificationTemplate> getTemplate(String name) {
    return Optional.ofNullable(service.getByName(name));
  }

  public List<NotificationTemplate> getAllTemplate() {
    return service.searchAll()
        .stream()
        .map(template -> (NotificationTemplate) template)
        .collect(Collectors.toList());
  }

  @Priority(-20)
  public static class Proxy implements NotificationTemplateProvider {
    private static DatabaseNotificationTemplateProvider provider;

    @Override
    public boolean isRuntimeOverride() {
      return true;
    }

    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      return Optional.ofNullable(provider).flatMap(p -> p.getTemplate(name));
    }

    @Override
    public List<NotificationTemplate> getAllTemplate() {
      return Optional.ofNullable(provider)
          .map(DatabaseNotificationTemplateProvider::getAllTemplate)
          .orElseGet(Collections::emptyList);
    }
  }
}
