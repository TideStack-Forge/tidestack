package com.ouroboros.notification.service.impl;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.Command;
import com.ouroboros.notification.template.NotificationTemplateDefinitionValidator;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.notification.template.provider.DatabaseNotificationTemplateProvider;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;
import com.ouroboros.notification.service.model.NotificationTemplateModel;

class NotificationTemplateRuntimeManagementServiceTest {
  private org.mockito.MockedStatic<SPI> spiBinding;

  @AfterEach
  void clearProviderBinding() throws Exception {
    Field provider = DatabaseNotificationTemplateProvider.Proxy.class.getDeclaredField("provider");
    provider.setAccessible(true); provider.set(null, null);
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    SPI.clearCache();
  }

  @Test
  void savingAndRestoringUseTheOriginalProviderChainWithoutMutatingTheDefault() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl();
      new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence);
      var opened = management.getRuntimeDefinition("alpha-template");
      var command = new Command(opened.defaultRevision(), opened.overrideRevision(), Map.of("title", "", "isEnabled", false, "channel", Map.of()));
      var check = management.validateRuntimeOverride("alpha-template", command);
      assertTrue(check.valid()); assertEquals(0L, store.model.count(Map.of()).get());
      assertEquals("Alpha", NotificationTemplateCenter.resolveDefaultTemplate("alpha-template").orElseThrow().template().getTitle());
      var saved = management.saveRuntimeOverride("alpha-template", command, "operator");
      assertTrue(saved.committed()); assertTrue(saved.confirmed()); assertTrue(saved.changed());
      assertFalse(NotificationTemplateCenter.getTemplate("alpha-template").orElseThrow().isEnabled());
      assertEquals("", NotificationTemplateCenter.getTemplate("alpha-template").orElseThrow().getTitle());
      var restarted = management(store, new NotificationTemplateServiceImpl());
      var current = restarted.getRuntimeDefinition("alpha-template");
      assertTrue(current.hasOverride()); assertEquals("operator", current.modifiedBy());
      assertEquals(current.defaultTemplate().get("payloadSchema"), current.effectiveTemplate().get("payloadSchema"));
      assertNull(NotificationTemplateCenter.resolveDefaultTemplate("alpha-template").orElseThrow().template().getPayloadSchema());
      assertEquals(false, current.baseChanged());
      var restored = restarted.restoreDefault("alpha-template", new Command(current.defaultRevision(), current.overrideRevision(), null), "operator");
      assertTrue(restored.confirmed()); assertFalse(restored.definition().hasOverride());
      assertEquals("Alpha", NotificationTemplateCenter.getTemplate("alpha-template").orElseThrow().getTitle());
      assertEquals(0L, store.model.count(Map.of()).get());
      return null;
    });
  }

  @Test
  void outdatedPagesCannotOverwriteOrDeleteANewerOverrideOrRecreatedIdentity() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl(); new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence); var opened = management.getRuntimeDefinition("alpha-template");
      var initial = new Command(opened.defaultRevision(), opened.overrideRevision(), Map.of("isEnabled", false));
      var saved = management.saveRuntimeOverride("alpha-template", initial, "operator").definition();
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", initial, "operator"));
      var update = new Command(saved.defaultRevision(), saved.overrideRevision(), Map.of("title", "changed"));
      var updated = management.saveRuntimeOverride("alpha-template", update, "other").definition();
      assertThrows(BusinessException.class, () -> management.restoreDefault("alpha-template", update, "operator"));
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", update, "operator"));
      management.restoreDefault("alpha-template", new Command(updated.defaultRevision(), updated.overrideRevision(), null), "operator");
      var recreated = management.saveRuntimeOverride("alpha-template", initial, "operator").definition();
      assertNotEquals(saved.overrideRevision(), recreated.overrideRevision());
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", update, "operator"));
      assertEquals(1L, store.model.count(Map.of()).get());
      return null;
    });
  }

  @Test
  void noOpRestoreAndNoOpSaveDoNotClaimOrAdvanceAnOverride() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl(); new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence); var opened = management.getRuntimeDefinition("alpha-template");
      var restore = management.restoreDefault("alpha-template", new Command(opened.defaultRevision(), opened.overrideRevision(), null), "operator");
      assertFalse(restore.changed()); assertEquals(0L, store.model.count(Map.of()).get());
      var saved = management.saveRuntimeOverride("alpha-template", new Command(opened.defaultRevision(), opened.overrideRevision(), Map.of("isEnabled", false)), "operator").definition();
      var same = management.saveRuntimeOverride("alpha-template", new Command(saved.defaultRevision(), saved.overrideRevision(), Map.of()), "operator");
      assertFalse(same.changed()); assertEquals(saved.overrideRevision(), same.overrideRevision());
      return null;
    });
  }

  @Test
  void versionsReadOnlyFieldsAndMissingDefaultsAreEnforcedByTheUseCase() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl(); new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence); var opened = management.getRuntimeDefinition("alpha-template");
      for (var changes : java.util.List.<Map<String, Object>>of(Map.of("name", "renamed"), Map.of("payloadSchema", Map.of()), Map.of("id", "replacement"))) {
        var invalid = new Command(opened.defaultRevision(), opened.overrideRevision(), changes);
        assertFalse(management.validateRuntimeOverride("alpha-template", invalid).valid());
        assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", invalid, "operator"));
      }
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", new Command(null, "NONE", Map.of()), "operator"));
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", new Command("sha256:stale", "NONE", Map.of()), "operator"));
      assertThrows(BusinessException.class, () -> management.saveRuntimeOverride("alpha-template", new Command(opened.defaultRevision(), "NONE", Map.of()), null));
      assertTrue(persistence.insertOverride(NotificationTemplatePersistenceIntegrationTest.template("historic", "database-only")));
      var historical = management.getRuntimeDefinition("database-only");
      assertFalse(historical.defaultExists()); assertFalse(historical.availableActions().get("edit"));
      assertThrows(BusinessException.class, () -> management.restoreDefault("database-only", new Command(null, historical.overrideRevision(), null), "operator"));
      assertThrows(BusinessException.class, () -> management.getRuntimeDefinition("does-not-exist"));
      return null;
    });
  }

  @Test
  void higherAndEqualPriorityDefinitionsKeepTheOriginalWinnerAndRejectUnreliableWrites() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl(); new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence);
      for (var custom : java.util.List.of(new HigherPriorityProvider(), new EqualPriorityProvider())) {
        for (boolean databaseFirst : java.util.List.of(false, true)) {
          var providers = databaseFirst && custom instanceof EqualPriorityProvider
              ? java.util.List.of(new DatabaseNotificationTemplateProvider.Proxy(), custom)
              : java.util.List.of(custom, new DatabaseNotificationTemplateProvider.Proxy());
          providers(providers);
          var definition = management.getRuntimeDefinition("priority-test");
          assertFalse(definition.availableActions().get("edit"));
          assertEquals("custom", definition.effectiveTemplate().get("title"));
          var error = assertThrows(com.ouroboros.notification.service.model.NotificationTemplateManagement.ManagementException.class,
              () -> management.saveRuntimeOverride("priority-test", new Command(definition.defaultRevision(), "NONE", Map.of("isEnabled", false)), "admin"));
          assertEquals("OVERRIDE_SHADOWED", error.getDetails().reason());
          assertEquals(0L, store.model.count(Map.of()).get());
        }
      }
      return null;
    });
  }

  @Test
  void aChangedDeployedDefaultRejectsTheOldPageBeforeAnyWrite() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = new NotificationTemplateServiceImpl(); new DatabaseNotificationTemplateProvider(persistence);
      var provider = new MutableDefaultProvider(); providers(java.util.List.of(new DatabaseNotificationTemplateProvider.Proxy(), provider));
      var management = management(store, persistence); var opened = management.getRuntimeDefinition("priority-test");
      provider.template.setTitle("new deployment");
      var error = assertThrows(com.ouroboros.notification.service.model.NotificationTemplateManagement.ManagementException.class,
          () -> management.saveRuntimeOverride("priority-test", new Command(opened.defaultRevision(), "NONE", Map.of("isEnabled", false)), "admin"));
      assertEquals("DEFAULT_CHANGED", error.getDetails().reason()); assertEquals(0L, store.model.count(Map.of()).get());
      assertEquals("custom", opened.effectiveTemplate().get("title"));
      return null;
    });
  }

  @Test
  void postCommitReadFailureReportsCommittedRevisionWithoutRetryingTheWrite() {
    var store = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    store.withModels(() -> {
      var persistence = spy(new NotificationTemplateServiceImpl()); new DatabaseNotificationTemplateProvider(persistence);
      var management = management(store, persistence); var opened = management.getRuntimeDefinition("alpha-template");
      doAnswer(call -> {
        Object inserted = call.callRealMethod();
        doThrow(new IllegalStateException("read unavailable")).when(persistence).getByName(anyString());
        return inserted;
      }).when(persistence).insertOverride(any(NotificationTemplateModel.class));
      var result = management.saveRuntimeOverride("alpha-template", new Command(opened.defaultRevision(), "NONE", Map.of("isEnabled", false)), "admin");
      assertTrue(result.committed()); assertFalse(result.confirmed()); assertNotEquals("NONE", result.overrideRevision());
      assertEquals("COMMITTED_VISIBILITY_UNCONFIRMED", result.reason()); assertEquals(1L, store.model.count(Map.of()).get());
      verify(persistence, times(1)).insertOverride(any(NotificationTemplateModel.class));
      new DatabaseNotificationTemplateProvider(new NotificationTemplateServiceImpl());
      assertEquals(result.overrideRevision(), management.getRuntimeDefinition("alpha-template").overrideRevision());
      return null;
    });
  }

  @Test
  void nullFieldAdditionIsAChangeAndDoesNotDisappearFromTheDifference() {
    var after = new java.util.LinkedHashMap<String, Object>(); after.put("new", null);
    var added = NotificationTemplateRuntimeManagementService.differences(Map.of(), after);
    assertEquals(1, added.size()); assertEquals("ADD", added.getFirst().operation());
    var removed = NotificationTemplateRuntimeManagementService.differences(after, Map.of());
    assertEquals(1, removed.size()); assertEquals("REMOVE", removed.getFirst().operation());
  }

  private void providers(java.util.List<? extends NotificationTemplateProvider> providers) {
    if (spiBinding == null) spiBinding = org.mockito.Mockito.mockStatic(SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> SPI.getCachedServiceStream(NotificationTemplateProvider.class))
        .thenAnswer(ignored -> providers.stream());
  }

  public static class MutableDefaultProvider implements NotificationTemplateProvider {
    final DefaultNotificationTemplate template = new DefaultNotificationTemplate();
    public MutableDefaultProvider() {
      template.setName("priority-test"); template.setTitle("custom"); template.setEnabled(false); template.setChannel(Map.of());
    }
    public java.util.Optional<NotificationTemplate> getTemplate(String name) { return "priority-test".equals(name) ? java.util.Optional.of(template) : java.util.Optional.empty(); }
    public java.util.List<NotificationTemplate> getAllTemplate() { return java.util.List.of(template); }
  }
  @jakarta.annotation.Priority(-30)
  public static class HigherPriorityProvider extends MutableDefaultProvider { }
  @jakarta.annotation.Priority(-20)
  public static class EqualPriorityProvider extends MutableDefaultProvider { }

  private NotificationTemplateRuntimeManagementService management(NotificationTemplatePersistenceIntegrationTest.Fixture fixture,
      NotificationTemplateServiceImpl persistence) {
    return new NotificationTemplateRuntimeManagementService(persistence,
        new NotificationTemplateDefinitionValidator(mock(ConfigurationService.class)), fixture.manager);
  }
}
