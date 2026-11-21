package com.ouroboros.notification.template.provider;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.notification.template.NotificationTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseNotificationTemplateProviderTest {

  @AfterEach
  void clearProxyProvider() throws Exception {
    Field providerField = DatabaseNotificationTemplateProvider.Proxy.class.getDeclaredField("provider");
    providerField.setAccessible(true);
    providerField.set(null, null);
  }

  @Test
  void getTemplateAndProxyShouldReturnTemplateFromService() {
    NotificationTemplateModel template = template("welcome", "Welcome");
    NotificationTemplateService service = mock(NotificationTemplateService.class);
    when(service.getByName("welcome")).thenReturn(template);

    DatabaseNotificationTemplateProvider provider = new DatabaseNotificationTemplateProvider(service);

    Optional<NotificationTemplate> direct = provider.getTemplate("welcome");
    Optional<NotificationTemplate> proxied = new DatabaseNotificationTemplateProvider.Proxy().getTemplate("welcome");

    assertTrue(direct.isPresent());
    assertSame(template, direct.get());
    assertTrue(proxied.isPresent());
    assertSame(template, proxied.get());
  }

  @Test
  void getAllTemplateAndProxyShouldReturnTemplatesFromService() {
    NotificationTemplateModel template1 = template("welcome", "Welcome");
    NotificationTemplateModel template2 = template("reminder", "Reminder");
    NotificationTemplateService service = mock(NotificationTemplateService.class);
    doReturn(Arrays.asList((NotificationTemplate) template1, template2)).when(service).searchAll();

    DatabaseNotificationTemplateProvider provider = new DatabaseNotificationTemplateProvider(service);

    List<NotificationTemplate> direct = provider.getAllTemplate();
    List<NotificationTemplate> proxied = new DatabaseNotificationTemplateProvider.Proxy().getAllTemplate();

    assertEquals(2, direct.size());
    assertSame(template1, direct.get(0));
    assertSame(template2, direct.get(1));
    assertEquals(2, proxied.size());
    assertSame(template1, proxied.get(0));
    assertSame(template2, proxied.get(1));
  }

  @Test
  void databaseFailureMustNotBeMistakenForAnAbsentOverride() {
    NotificationTemplateService service = mock(NotificationTemplateService.class);
    when(service.getByName("broken")).thenThrow(new RuntimeException("boom"));
    when(service.searchAll()).thenThrow(new RuntimeException("boom"));

    DatabaseNotificationTemplateProvider provider = new DatabaseNotificationTemplateProvider(service);

    assertThrows(RuntimeException.class, () -> provider.getTemplate("broken"));
    assertThrows(RuntimeException.class, provider::getAllTemplate);
    var proxy = new DatabaseNotificationTemplateProvider.Proxy();
    assertThrows(RuntimeException.class, () -> proxy.getTemplate("broken"));
    assertThrows(RuntimeException.class, proxy::getAllTemplate);
  }

  @Test
  void missingRowStillAllowsTheOriginalProviderChainToContinue() {
    var provider = new DatabaseNotificationTemplateProvider(mock(NotificationTemplateService.class));
    assertTrue(provider.getTemplate("missing").isEmpty());
    assertTrue(new DatabaseNotificationTemplateProvider.Proxy().isRuntimeOverride());
  }

  @Test
  void proxyShouldFailOpenWhenProviderIsUnset() {
    DatabaseNotificationTemplateProvider.Proxy proxy = new DatabaseNotificationTemplateProvider.Proxy();

    assertFalse(proxy.getTemplate("missing").isPresent());
    assertEquals(Collections.emptyList(), proxy.getAllTemplate());
  }

  private NotificationTemplateModel template(String name, String title) {
    NotificationTemplateModel template = new NotificationTemplateModel();
    template.setName(name);
    template.setTitle(title);
    template.setEnabled(true);
    template.setChannel(Collections.emptyMap());
    return template;
  }
}
