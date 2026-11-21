package com.ouroboros.i18n.runtime.provider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.AppContext;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.util.Json;

/**
 * 文件系统字典提供者
 * 从应用的 i18n/ 目录加载 JSON 格式的字典文件
 */
@Component
public class FileSystemI18nLocaleProvider {

  private static final Logger logger = LoggerFactory.getLogger(FileSystemI18nLocaleProvider.class);
  private static final String I18N_DIR = "i18n";
  private static final String JSON_SUFFIX = ".i18n.json";

  private AppContext appContext;
  private final Map<String, I18nLocale> localeCache = new ConcurrentHashMap<>();

  public FileSystemI18nLocaleProvider(AppContext appContext) {
    this.appContext = appContext;
    Proxy.provider = this;
  }

  @PostConstruct
  public void init() {
    loadAllLocales();
  }

  private AppContext getAppContext() {
    return appContext;
  }

  private void loadAllLocales() {
    AppContext appContext = getAppContext();
    if (appContext == null) {
      logger.error("AppContext is not available");
      return;
    }

    String homePath = appContext.getHomePath();
    if (homePath == null) {
      logger.error("AppContext.getHomePath() returned null");
      return;
    }

    File i18nDir = new File(homePath, I18N_DIR);
    if (!i18nDir.exists() || !i18nDir.isDirectory()) {
      logger.debug("I18n directory not found: {}", i18nDir.getAbsolutePath());
      return;
    }

    File[] files = i18nDir.listFiles();
    if (files == null) {
      logger.warn("Failed to list files in directory: {}", i18nDir.getAbsolutePath());
      return;
    }

    // 按 locale 分组，保留最高版本
    Map<String, List<I18nLocale>> localeGroups = new HashMap<>();

    for (File file : files) {
      if (!file.isFile() || !file.getName().endsWith(JSON_SUFFIX)) {
        continue;
      }

      try {
        String content = FileUtils.readFileToString(file, "UTF-8");
        I18nLocale locale = Json.tryToBean(I18nLocaleImpl.class, content)
            .onFailure(e -> logger.error("Failed to parse locale file: {}, error: {}", file.getAbsolutePath(), e.getMessage(), e))
            .getOrNull();

        if (locale != null && locale.getLocale() != null) {
          localeGroups.computeIfAbsent(locale.getLocale(), k -> new ArrayList<>()).add(locale);
          logger.debug("Loaded locale file: {}, locale: {}, version: {}", file.getName(), locale.getLocale(), locale.getVersion());
        }
      } catch (Exception e) {
        logger.error("Failed to read locale file: {}", file.getAbsolutePath(), e);
      }
    }

    // 合并同语言的不同版本，保留最高版本
    localeGroups.forEach((localeCode, locales) -> {
      if (locales.size() == 1) {
        localeCache.put(localeCode, locales.get(0));
      } else {
        // 按版本号排序，取最高版本
        I18nLocale highest = locales.stream()
            .max((a, b) -> compareVersion(a.getVersion(), b.getVersion()))
            .orElse(locales.get(0));
        localeCache.put(localeCode, highest);
        logger.info("Multiple versions found for locale: {}, using version: {}", localeCode, highest.getVersion());
      }
    });

    logger.info("Loaded {} locales from filesystem", localeCache.size());
  }

  private int compareVersion(String v1, String v2) {
    if (v1 == null && v2 == null) return 0;
    if (v1 == null) return -1;
    if (v2 == null) return 1;

    try {
      String[] parts1 = v1.split("\\.");
      String[] parts2 = v2.split("\\.");
      int len = Math.max(parts1.length, parts2.length);

      for (int i = 0; i < len; i++) {
        int num1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("\\D.*", "")) : 0;
        int num2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("\\D.*", "")) : 0;
        if (num1 != num2) {
          return Integer.compare(num1, num2);
        }
      }
      return 0;
    } catch (Exception e) {
      return v1.compareTo(v2);
    }
  }

  public Optional<I18nLocale> getLocale(String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      logger.debug("Locale is null or empty");
      return Optional.empty();
    }

    return Optional.ofNullable(localeCache.get(locale));
  }

  public List<String> getSupportedLocales() {
    return new ArrayList<>(localeCache.keySet());
  }

  @Priority(0)
  public static class Proxy implements I18nLocaleProvider {
    private static FileSystemI18nLocaleProvider provider;

    @Override
    public Optional<I18nLocale> getDictionary(String locale) {
      return Optional.ofNullable(provider)
          .map(p -> p.getLocale(locale))
          .orElse(Optional.empty());
    }

    @Override
    public List<String> getSupportedLocales() {
      return Optional.ofNullable(provider)
          .map(FileSystemI18nLocaleProvider::getSupportedLocales)
          .orElse(Collections.emptyList());
    }
  }
}
