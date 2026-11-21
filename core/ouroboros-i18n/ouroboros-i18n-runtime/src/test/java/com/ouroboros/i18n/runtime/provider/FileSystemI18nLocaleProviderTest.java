package com.ouroboros.i18n.runtime.provider;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemI18nLocaleProviderTest {

  @TempDir
  Path appHome;

  @Test
  void shouldOnlyLoadTypedI18nJsonFilesFromAppHome() throws Exception {
    Path i18nDir = appHome.resolve("i18n");
    Files.createDirectories(i18nDir);
    Files.writeString(i18nDir.resolve("zh-CN.json"), localeJson("legacy-ZH"));
    Files.writeString(i18nDir.resolve("en-US.i18n.json"), localeJson("typed-EN"));

    AppContext appContext = () -> appHome.toString();

    var provider = new FileSystemI18nLocaleProvider(appContext);
    provider.init();

    assertFalse(provider.getLocale("legacy-ZH").isPresent());
    assertTrue(provider.getLocale("typed-EN").isPresent());
  }

  private String localeJson(String locale) {
    return """
        {
          "locale": "%s",
          "name": "%s",
          "version": "1.0.0",
          "messages": {}
        }
        """.formatted(locale, locale);
  }
}
