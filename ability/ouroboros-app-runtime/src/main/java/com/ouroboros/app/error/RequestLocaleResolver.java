package com.ouroboros.app.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.util.SPI;

@Component
public class RequestLocaleResolver {
  private final List<I18nLocaleProvider> localeProviders;

  public RequestLocaleResolver() {
    this(SPI.getCachedServiceStream(I18nLocaleProvider.class).collect(Collectors.toList()));
  }

  public RequestLocaleResolver(List<I18nLocaleProvider> localeProviders) {
    this.localeProviders = localeProviders == null ? Collections.emptyList() : new ArrayList<>(localeProviders);
  }

  public String resolve(String acceptLanguage) {
    Map<String, I18nLocale> enabledLocales = getEnabledLocales();
    if (enabledLocales.isEmpty()) {
      return firstRequestedLocale(acceptLanguage);
    }

    var defaultLocale = resolveDefaultLocale(enabledLocales);
    for (var requestedLocale : parseRequestedLocales(acceptLanguage)) {
      var exactMatch = matchExact(enabledLocales, requestedLocale);
      if (exactMatch != null) {
        return exactMatch;
      }

      var weakMatch = matchByLanguage(enabledLocales, requestedLocale);
      if (weakMatch != null) {
        return weakMatch;
      }
    }
    return defaultLocale;
  }

  public String resolveCurrentLocale() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
      return resolve(servletRequestAttributes.getRequest().getHeader("Accept-Language"));
    }
    return resolve(null);
  }

  private Map<String, I18nLocale> getEnabledLocales() {
    Map<String, I18nLocale> locales = new LinkedHashMap<>();
    localeProviders.stream()
        .filter(Objects::nonNull)
        .flatMap(provider -> provider.getSupportedLocales().stream()
            .map(provider::getDictionary)
            .filter(Optional::isPresent)
            .map(Optional::get))
        .filter(locale -> Boolean.TRUE.equals(locale.getEnabled()))
        .forEach(locale -> locales.putIfAbsent(locale.getLocale(), locale));
    return locales;
  }

  private String resolveDefaultLocale(Map<String, I18nLocale> enabledLocales) {
    return enabledLocales.values().stream()
        .filter(locale -> Boolean.TRUE.equals(locale.getIsDefault()))
        .map(I18nLocale::getLocale)
        .findFirst()
        .orElseGet(() -> enabledLocales.keySet().stream().findFirst().orElse(null));
  }

  private List<String> parseRequestedLocales(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.trim().isEmpty()) {
      return Collections.emptyList();
    }

    List<String> locales = new ArrayList<>();
    for (String part : acceptLanguage.split(",")) {
      String locale = part.trim();
      int qIndex = locale.indexOf(';');
      if (qIndex >= 0) {
        locale = locale.substring(0, qIndex).trim();
      }
      locale = normalize(locale);
      if (!locale.isEmpty() && !"*".equals(locale)) {
        locales.add(locale);
      }
    }
    return locales;
  }

  private String matchExact(Map<String, I18nLocale> enabledLocales, String requestedLocale) {
    var normalizedRequested = normalize(requestedLocale);
    return enabledLocales.keySet().stream()
        .filter(locale -> normalize(locale).equalsIgnoreCase(normalizedRequested))
        .findFirst()
        .orElse(null);
  }

  private String matchByLanguage(Map<String, I18nLocale> enabledLocales, String requestedLocale) {
    var language = languagePart(requestedLocale);
    if (language.isEmpty()) {
      return null;
    }
    return enabledLocales.keySet().stream()
        .filter(locale -> languagePart(locale).equalsIgnoreCase(language))
        .findFirst()
        .orElse(null);
  }

  private String firstRequestedLocale(String acceptLanguage) {
    return parseRequestedLocales(acceptLanguage).stream().findFirst().orElse(null);
  }

  private static String normalize(String locale) {
    return locale == null ? "" : locale.trim().replace('_', '-');
  }

  private static String languagePart(String locale) {
    var normalized = normalize(locale);
    var index = normalized.indexOf('-');
    return index < 0 ? normalized.toLowerCase(Locale.ROOT) : normalized.substring(0, index).toLowerCase(Locale.ROOT);
  }
}
