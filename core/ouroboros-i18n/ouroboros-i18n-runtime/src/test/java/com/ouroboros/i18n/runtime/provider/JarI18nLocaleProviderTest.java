package com.ouroboros.i18n.runtime.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarI18nLocaleProviderTest {

  @Test
  void shouldLoadTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarI18nLocaleProvider();

    var locale = provider.getDictionary("jar-ZH");

    assertTrue(locale.isPresent());
    assertEquals("Jar Chinese", locale.get().getName());
    assertEquals("hello-from-jar", locale.get().getMessages().get("common").get("hello"));
    assertTrue(provider.getSupportedLocales().contains("jar-ZH"));
  }
}
