package com.ouroboros.notification.template.provider;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.notification.template.NotificationTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarNotificationTemplateProviderTest {

  @Test
  void getTemplateShouldLoadFixtureFromClasspathResources() {
    JarNotificationTemplateProvider provider = new JarNotificationTemplateProvider();

    Optional<NotificationTemplate> template = provider.getTemplate("JAR-TEMPLATE-ALPHA");

    assertTrue(template.isPresent());
    assertEquals("Jar Alpha", template.get().getTitle());
  }

  @Test
  void getAllTemplateShouldIncludeJarFixtures() {
    JarNotificationTemplateProvider provider = new JarNotificationTemplateProvider();

    List<String> names = provider.getAllTemplate().stream()
        .map(NotificationTemplate::getName)
        .collect(Collectors.toList());

    assertTrue(names.contains("jar-template-alpha"));
    assertTrue(names.contains("jar-template-beta"));
  }
}
