package com.ouroboros.notification.template;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.template.provider.DatabaseNotificationTemplateProvider;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class NotificationTemplateCenterTest {

  @BeforeEach
  @AfterEach
  void clearSpiCache() throws Exception {
    Field databaseProvider = DatabaseNotificationTemplateProvider.Proxy.class.getDeclaredField("provider");
    databaseProvider.setAccessible(true);
    databaseProvider.set(null, null);
    SPI.clearCache();
  }

  @Test
  void getTemplateShouldResolveTemplateFromRegisteredProviders() {
    Optional<NotificationTemplate> template = NotificationTemplateCenter.getTemplate("alpha-template");

    assertTrue(template.isPresent());
    assertEquals("alpha-template", template.get().getName());
    assertEquals("Alpha", template.get().getTitle());
  }

  @Test
  void disabledEmptyDatabaseSnapshotWinsUntilTheOverrideIsRemoved() throws Exception {
    var stored = new NotificationTemplateModel();
    stored.setName("alpha-template");
    stored.setTitle("");
    stored.setEnabled(false);
    stored.setChannel(Map.of());
    var service = mock(NotificationTemplateService.class);
    when(service.getByName("alpha-template")).thenReturn(stored);
    doReturn(java.util.List.of(stored)).when(service).searchAll();
    new DatabaseNotificationTemplateProvider(service);
    try {
      var effective = NotificationTemplateCenter.resolveTemplate("alpha-template").orElseThrow();
      assertTrue(effective.runtimeOverride());
      assertFalse(effective.template().isEnabled());
      assertTrue(effective.template().getChannel().isEmpty());
      assertEquals("", effective.template().getTitle());
      assertEquals(stored, NotificationTemplateCenter.getAllTemplate().get("alpha-template"));
      var baseline = NotificationTemplateCenter.resolveDefaultTemplate("alpha-template").orElseThrow();
      assertFalse(baseline.runtimeOverride());
      assertEquals("Alpha", baseline.template().getTitle());
      assertEquals(baseline.template().getTitle(),
          NotificationTemplateCenter.getAllDefaultTemplate().get("alpha-template").getTitle());
      when(service.getByName("alpha-template")).thenReturn(null);
      assertEquals("Alpha", NotificationTemplateCenter.getTemplate("alpha-template").orElseThrow().getTitle());
    } finally {
      Field provider = DatabaseNotificationTemplateProvider.Proxy.class.getDeclaredField("provider");
      provider.setAccessible(true);
      provider.set(null, null);
    }
  }

  @Test
  void getAllTemplateShouldAggregateTemplatesAcrossProviders() {
    Map<String, NotificationTemplate> templates = NotificationTemplateCenter.getAllTemplate();

    assertTrue(templates.containsKey("alpha-template"));
    assertTrue(templates.containsKey("beta-template"));
    assertTrue(templates.containsKey("controlled-template"));
    assertTrue(templates.containsKey("null-title-template"));
    assertTrue(templates.containsKey("gamma-template"));
    assertTrue(templates.containsKey("jar-template-alpha"));
    assertTrue(templates.containsKey("jar-template-beta"));
    assertTrue(templates.containsKey("test-invalid-payload-template"));
    assertTrue(templates.containsKey("test-success-template"));
    assertEquals(Arrays.asList(
            "alpha-template",
            "beta-template",
            "controlled-template",
            "gamma-template",
            "jar-template-alpha",
            "jar-template-beta",
            "null-title-template",
            "test-invalid-payload-template",
            "test-success-template"),
        Arrays.asList(templates.keySet().toArray(new String[0])));
  }

  @Test
  void getAllTemplateShouldAggregateControlledTemplateFixtureFromRegisteredProviders() {
    Map<String, NotificationTemplate> templates = NotificationTemplateCenter.getAllTemplate();

    assertTrue(templates.containsKey("controlled-template"));
    assertTrue(templates.get("controlled-template") instanceof NotificationTemplateModel);
    assertEquals("Controlled Template", templates.get("controlled-template").getTitle());
  }

  @Test
  void getAllTemplateShouldAggregateNullTitleFixtureFromRegisteredProviders() {
    Map<String, NotificationTemplate> templates = NotificationTemplateCenter.getAllTemplate();

    assertTrue(templates.containsKey("null-title-template"));
    assertEquals(null, templates.get("null-title-template").getTitle());
  }

  @Priority(-5)
  public static class AlphaTemplateProvider implements NotificationTemplateProvider {
    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      if (!"alpha-template".equals(name)) {
        return Optional.empty();
      }
      return Optional.of(template("alpha-template", "Alpha"));
    }

    @Override
    public java.util.List<NotificationTemplate> getAllTemplate() {
      return Collections.singletonList(template("alpha-template", "Alpha"));
    }
  }

  @Priority(5)
  public static class SecondaryTemplateProvider implements NotificationTemplateProvider {
    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      if ("beta-template".equals(name)) {
        return Optional.of(template("beta-template", "Beta"));
      }
      if ("gamma-template".equals(name)) {
        return Optional.of(template("gamma-template", "Gamma"));
      }
      return Optional.empty();
    }

    @Override
    public java.util.List<NotificationTemplate> getAllTemplate() {
      return Arrays.asList(
          template("beta-template", "Beta"),
          template("gamma-template", "Gamma"));
    }
  }

  @Priority(0)
  public static class ControlledTemplateProvider implements NotificationTemplateProvider {
    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      if (!"controlled-template".equals(name)) {
        return Optional.empty();
      }
      return Optional.of(controlledTemplate());
    }

    @Override
    public java.util.List<NotificationTemplate> getAllTemplate() {
      return Collections.singletonList(controlledTemplate());
    }
  }

  @Priority(1)
  public static class NullTitleTemplateProvider implements NotificationTemplateProvider {
    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      if (!"null-title-template".equals(name)) {
        return Optional.empty();
      }
      return Optional.of(template("null-title-template", null));
    }

    @Override
    public java.util.List<NotificationTemplate> getAllTemplate() {
      return Collections.singletonList(template("null-title-template", null));
    }
  }

  private static NotificationTemplate template(String name, String title) {
    DefaultNotificationTemplate template = new DefaultNotificationTemplate();
    template.setName(name);
    template.setTitle(title);
    template.setEnabled(true);
    template.setChannel(new LinkedHashMap<>());
    return template;
  }

  private static NotificationTemplateModel controlledTemplate() {
    NotificationTemplateModel template = new NotificationTemplateModel();
    template.setId("controlled-template-id");
    template.setName("controlled-template");
    template.setTitle("Controlled Template");
    template.setEnabled(true);
    template.setChannel(new LinkedHashMap<>());
    return template;
  }
}
