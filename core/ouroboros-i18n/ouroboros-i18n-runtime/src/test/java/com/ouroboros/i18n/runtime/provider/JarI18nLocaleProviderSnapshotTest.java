package com.ouroboros.i18n.runtime.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class JarI18nLocaleProviderSnapshotTest {
  @Test
  void partialDefinitionsAreUsableAndSameProviderRetriesAfterFailure() {
    var broken = new AtomicReference<>("{");
    var provider = new JarI18nLocaleProvider(spec -> List.of(
        new com.ouroboros.metadata.resource.MetadataResource("good.i18n.json", "{\"locale\":\"test-ZH\",\"messages\":{}}"),
        new com.ouroboros.metadata.resource.MetadataResource("broken.good.i18n.json", broken.get())));
    assertEquals(1, provider.getSupportedLocales().size());
    assertTrue(provider.getDictionary("test-ZH").isPresent());
    broken.set("{\"locale\":\"test-EN\",\"messages\":{}}");
    assertEquals(2, provider.getSupportedLocales().size());
  }

  @Test
  void sourceFailureIsNonblockingAndSameProviderCanRecover() {
    var broken = new AtomicBoolean(true);
    var provider = new JarI18nLocaleProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new com.ouroboros.metadata.resource.MetadataResource("good.i18n.json", "{\"locale\":\"test-ZH\",\"messages\":{}}"));
    });
    assertTrue(provider.getSupportedLocales().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getSupportedLocales().size());
  }

  @Test
  void recreatedProviderKeepsDefinitionsAfterResourceRemovalAndCallerMutation() throws Exception {
    var file = Path.of(getClass().getResource("/META-INF/ouroboros/metadata/jar-ZH.i18n.json").toURI());
    var content = Files.readString(file);
    var first = new JarI18nLocaleProvider().getDictionary("jar-ZH").orElseThrow();
    first.getMessages().get("common").clear();
    try {
      Files.delete(file);

      var second = new JarI18nLocaleProvider().getDictionary("jar-ZH").orElseThrow();

      assertEquals("hello-from-jar", second.getMessages().get("common").get("hello"));
    } finally {
      Files.writeString(file, content);
    }
  }
}
