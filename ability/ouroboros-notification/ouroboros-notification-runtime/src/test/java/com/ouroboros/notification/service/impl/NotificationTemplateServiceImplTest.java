package com.ouroboros.notification.service.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplateServiceImplTest {
  @Test
  void lookupUsesOnlyTheCaseInsensitiveTemplateIdentity() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var service = new NotificationTemplateServiceImpl();
      var first = NotificationTemplatePersistenceIntegrationTest.template("first", "Welcome");
      first.setTitle("other");
      var second = NotificationTemplatePersistenceIntegrationTest.template("second", "other");
      second.setTitle("welcome");
      assertTrue(service.insertOverride(first)); assertTrue(service.insertOverride(second));
      assertEquals("Welcome", service.getByName("WELCOME").getName());
      assertEquals("first", service.getOverride("welcome").getId());
      assertEquals("other", service.getByName("welcome").getTitle());
      assertNull(service.getByName("missing"));
      return null;
    });
  }

  @Test
  void unconditionalCrudCannotBypassRuntimeManagement() {
    var service = new NotificationTemplateServiceImpl();
    var row = NotificationTemplatePersistenceIntegrationTest.template("first", "welcome");
    List<org.junit.jupiter.api.function.Executable> writes = List.of(
        () -> service.add(row), () -> service.add(List.of(row)), () -> service.update("first", row),
        () -> service.partialUpdate("first", Map.of("title", "bypass")), () -> service.delete("first"), () -> service.delete(List.of("first")));
    writes.forEach(write -> assertEquals(400, assertThrows(BusinessException.class, write).getErrorCode()));
  }
}
