package com.ouroboros.i18n.runtime.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import io.vavr.control.Either;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.util.SPI;

/**
 * 字典管理服务实现
 */
@Service
public class I18nLocaleServiceImpl implements I18nLocaleService {

  private static final Logger logger = LoggerFactory.getLogger(I18nLocaleServiceImpl.class);

  @Cacheable(value = "i18n-locale", key = "#p0")
  @Override
  public Either<OuroborosException, I18nLocale> getLocale(String locale) {
    if (locale == null || locale.trim().isEmpty()) {
      return Either.left(new BusinessException("Locale cannot be null or empty"));
    }

    List<I18nLocale> locales = new ArrayList<>();

    SPI.getSortedServiceStream(I18nLocaleProvider.class).forEach(provider -> {
      try {
        provider.getDictionary(locale).ifPresent(locales::add);
      } catch (Exception e) {
        logger.error("Failed to get locale from provider: {}", provider.getClass().getName(), e);
      }
    });

    if (locales.isEmpty()) {
      return Either.left(new BusinessException("Locale not found: " + locale));
    }

    if (locales.size() == 1) {
      return Either.right(locales.get(0));
    }

    I18nLocale merged = mergeLocales(locales);
    return Either.right(merged);
  }

  @Cacheable(value = "i18n-languages")
  @Override
  public List<I18nLocale> getSupportedLanguages() {
    Set<String> locales = new HashSet<>();

    SPI.getServiceStream(I18nLocaleProvider.class).forEach(provider -> {
      try {
        List<String> supportedLocales = provider.getSupportedLocales();
        if (supportedLocales != null) {
          locales.addAll(supportedLocales);
        }
      } catch (Exception e) {
        logger.error("Failed to get supported locales from provider: {}", provider.getClass().getName(), e);
      }
    });

    List<I18nLocale> languages = new ArrayList<>();
    for (String locale : locales) {
      getLocale(locale).peek(dict -> {
        // 只返回启用的语言
        if (dict.getEnabled()) {
          // 不传递 messages，语言列表不需要翻译内容
          I18nLocale metadata = new I18nLocaleImpl(
              dict.getLocale(),
              dict.getName(),
              dict.getVersion(),
              dict.getEnabled(),
              dict.getIsDefault(),
              null,
              dict.getTimestamp(),
              dict.getDirection()
          );
          languages.add(metadata);
        }
      });
    }

    return languages;
  }

  @Override
  public Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale) {
    if (sourceLocale == null || sourceLocale.trim().isEmpty()) {
      return Either.left(new BusinessException("Source locale cannot be null or empty"));
    }

    List<I18nEntry> allEntries = new ArrayList<>();

    SPI.getServiceStream(TextExtractor.class).forEach(extractor -> {
      try {
        extractor.extract().peek(entries -> {
          if (entries != null) {
            allEntries.addAll(entries);
          }
        }).peekLeft(e -> {
          logger.warn("Failed to extract texts from extractor: {}", extractor.getClass().getName(), e);
        });
      } catch (Exception e) {
        logger.error("Failed to extract texts from extractor: {}", extractor.getClass().getName(), e);
      }
    });

    Map<String, Map<String, String>> entries = new HashMap<>();
    for (I18nEntry entry : allEntries) {
      entries.computeIfAbsent(entry.getNamespace(), k -> new HashMap<>())
          .put(entry.getKey(), entry.getSourceText());
    }

    I18nLocale locale = new I18nLocaleImpl(
        sourceLocale,
        sourceLocale,
        "1.0.0",
        true,
        false,
        entries,
        System.currentTimeMillis(),
        null
    );

    logger.info("Extracted {} entries for locale: {}", allEntries.size(), sourceLocale);
    return Either.right(locale);
  }

  private I18nLocale mergeLocales(List<I18nLocale> locales) {
    if (locales.isEmpty()) {
      return null;
    }

    List<I18nLocale> sorted = locales.stream()
        .sorted(Comparator.comparing(I18nLocale::getVersion))
        .collect(Collectors.toList());

    Map<String, Map<String, String>> mergedEntries = new HashMap<>();
    I18nLocale dbLocale = null;

    for (I18nLocale locale : sorted) {
      Map<String, Map<String, String>> messages = locale.getMessages();
      if (messages != null) {
        messages.forEach((namespace, keyMap) -> {
          mergedEntries.computeIfAbsent(namespace, k -> new HashMap<>())
              .putAll(keyMap);
        });
      }

      // 检查是否是数据库Provider（通过版本号判断，数据库Provider通常版本更高）
      // 或者通过其他方式识别数据库Provider
      if (locale.getVersion().compareTo("2.0") >= 0) {
        dbLocale = locale;
      }
    }

    // 优先使用数据库Provider的状态，否则使用最高版本Provider的状态
    I18nLocale controllingLocale = dbLocale != null ? dbLocale : sorted.get(sorted.size() - 1);

    return new I18nLocaleImpl(
        controllingLocale.getLocale(),
        controllingLocale.getName(),
        controllingLocale.getVersion(),
        controllingLocale.getEnabled(),
        controllingLocale.getIsDefault(),
        mergedEntries,
        System.currentTimeMillis(),
        controllingLocale.getDirection()
    );
  }
}
