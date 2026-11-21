package com.ouroboros.notification.template.provider;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.notification.template.NotificationTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNotificationTemplateProviderTest {

  @TempDir
  Path tempDir;

  @AfterEach
  void clearProxyProvider() throws Exception {
    Field providerField = FileNotificationTemplateProvider.Proxy.class.getDeclaredField("provider");
    providerField.setAccessible(true);
    providerField.set(null, null);
  }

  @Test
  void constructorShouldLoadTemplatesRecursivelyAndProxyShouldSeeThem() throws Exception {
    writeTemplate(tempDir.resolve("notification-template/alpha.notification.json"), "alpha-template", "Alpha");
    writeTemplate(tempDir.resolve("notification-template/nested/beta.notification.json"), "beta-template", "Beta");

    FileNotificationTemplateProvider provider = new FileNotificationTemplateProvider(appContext());

    Optional<NotificationTemplate> alpha = new FileNotificationTemplateProvider.Proxy().getTemplate("ALPHA-TEMPLATE");
    List<NotificationTemplate> proxied = new FileNotificationTemplateProvider.Proxy().getAllTemplate();

    assertTrue(alpha.isPresent());
    assertEquals("Alpha", alpha.get().getTitle());
    assertEquals(2, proxied.size());
    assertEquals("alpha-template", proxied.get(0).getName());
    assertEquals("beta-template", proxied.get(1).getName());
  }

  @Test
  void constructorShouldSkipMalformedTemplateFilesAndKeepValidTemplatesLoaded() throws Exception {
    writeTemplate(tempDir.resolve("notification-template/alpha.notification.json"), "alpha-template", "Alpha");
    writeRawTemplate(tempDir.resolve("notification-template/broken.notification.json"), "{broken-json");

    assertDoesNotThrow(() -> new FileNotificationTemplateProvider(appContext()));

    Optional<NotificationTemplate> alpha = new FileNotificationTemplateProvider.Proxy().getTemplate("alpha-template");
    List<NotificationTemplate> proxied = new FileNotificationTemplateProvider.Proxy().getAllTemplate();

    assertTrue(alpha.isPresent());
    assertEquals("Alpha", alpha.get().getTitle());
    assertEquals(1, proxied.size());
    assertEquals("alpha-template", proxied.get(0).getName());
  }

  @Test
  void refreshEventShouldReloadNewTemplates() throws Exception {
    writeTemplate(tempDir.resolve("notification-template/alpha.notification.json"), "alpha-template", "Alpha");
    FileNotificationTemplateProvider provider = new FileNotificationTemplateProvider(appContext());

    writeTemplate(tempDir.resolve("notification-template/gamma.notification.json"), "gamma-template", "Gamma");
    provider.refresh(new OuroborosMetadataRefreshingEvent(this));

    Optional<NotificationTemplate> gamma = new FileNotificationTemplateProvider.Proxy().getTemplate("gamma-template");
    assertTrue(gamma.isPresent());
    assertEquals("Gamma", gamma.get().getTitle());
    assertEquals(2, new FileNotificationTemplateProvider.Proxy().getAllTemplate().size());
  }

  @Test
  void refreshShouldIgnoreMalformedTemplateAndPreserveKnownGoodTemplates() throws Exception {
    Path alphaPath = tempDir.resolve("notification-template/alpha.notification.json");
    writeTemplate(alphaPath, "alpha-template", "Alpha");
    writeTemplate(tempDir.resolve("notification-template/beta.notification.json"), "beta-template", "Beta");
    FileNotificationTemplateProvider provider = new FileNotificationTemplateProvider(appContext());

    writeRawTemplate(alphaPath, "{broken-json");
    provider.refresh(new OuroborosMetadataRefreshingEvent(this));

    Optional<NotificationTemplate> alpha = new FileNotificationTemplateProvider.Proxy().getTemplate("alpha-template");
    Optional<NotificationTemplate> beta = new FileNotificationTemplateProvider.Proxy().getTemplate("beta-template");

    assertTrue(alpha.isPresent());
    assertEquals("Alpha", alpha.get().getTitle());
    assertTrue(beta.isPresent());
    assertEquals("Beta", beta.get().getTitle());
    assertEquals(2, new FileNotificationTemplateProvider.Proxy().getAllTemplate().size());
  }

  @Test
  void refreshShouldRemoveDeletedValidTemplatesWhileKeepingOtherValidTemplates() throws Exception {
    Path alphaPath = tempDir.resolve("notification-template/alpha.notification.json");
    writeTemplate(alphaPath, "alpha-template", "Alpha");
    writeTemplate(tempDir.resolve("notification-template/beta.notification.json"), "beta-template", "Beta");
    FileNotificationTemplateProvider provider = new FileNotificationTemplateProvider(appContext());

    Files.delete(alphaPath);
    provider.refresh(new OuroborosMetadataRefreshingEvent(this));

    Optional<NotificationTemplate> alpha = new FileNotificationTemplateProvider.Proxy().getTemplate("alpha-template");
    Optional<NotificationTemplate> beta = new FileNotificationTemplateProvider.Proxy().getTemplate("beta-template");

    assertFalse(alpha.isPresent());
    assertTrue(beta.isPresent());
    assertEquals("Beta", beta.get().getTitle());
    assertEquals(1, new FileNotificationTemplateProvider.Proxy().getAllTemplate().size());
  }

  @Test
  void proxyShouldFailOpenWhenProviderIsUnset() {
    FileNotificationTemplateProvider.Proxy proxy = new FileNotificationTemplateProvider.Proxy();

    assertFalse(proxy.getTemplate("missing").isPresent());
    assertTrue(proxy.getAllTemplate().isEmpty());
  }

  private AppContext appContext() {
    return new AppContext() {
      @Override
      public String getHomePath() {
        return tempDir.toString();
      }
    };
  }

  private void writeTemplate(Path path, String name, String title) throws Exception {
    Files.createDirectories(path.getParent());
    Files.write(path, (
        "{"
            + "\"name\":\"" + name + "\"," 
            + "\"title\":\"" + title + "\"," 
            + "\"isEnabled\":true,"
             + "\"channel\":{}"
             + "}").getBytes(StandardCharsets.UTF_8));
  }

  private void writeRawTemplate(Path path, String content) throws Exception {
    Files.createDirectories(path.getParent());
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }
}
