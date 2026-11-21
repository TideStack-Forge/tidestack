package com.ouroboros.i18n.runtime.error;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ouroboros.errorcode.ResolvedErrorDescriptor;
import com.ouroboros.i18n.error.ErrorMessageLocalizer;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.runtime.service.I18nLocaleService;

@Component
public class DefaultErrorMessageLocalizer implements ErrorMessageLocalizer {
  private static final String ERROR_NAMESPACE = "errors";

  private final I18nLocaleService i18nLocaleService;

  public DefaultErrorMessageLocalizer(I18nLocaleService i18nLocaleService) {
    this.i18nLocaleService = i18nLocaleService;
  }

  @Override
  public String localize(String locale, ResolvedErrorDescriptor descriptor) {
    if (descriptor == null) {
      return null;
    }

    Optional<String> translatedMessage = resolveTranslatedMessage(locale, descriptor);
    if (!translatedMessage.isPresent()) {
      translatedMessage = resolveTranslatedMessage(resolveDefaultLocale(), descriptor);
    }

    return translatedMessage
        .map(message -> formatMessage(message, descriptor.getMessageArgs()))
        .orElseGet(() -> fallbackMessage(descriptor));
  }

  private Optional<String> resolveTranslatedMessage(String locale, ResolvedErrorDescriptor descriptor) {
    if (locale == null || locale.trim().isEmpty() || descriptor.getCode() == null) {
      return Optional.empty();
    }

    var localeResult = i18nLocaleService.getLocale(locale);
    if (localeResult.isLeft()) {
      return Optional.empty();
    }

    return Optional.ofNullable(localeResult.get())
        .map(I18nLocale::getMessages)
        .map(messages -> messages.get(ERROR_NAMESPACE))
        .map(namespaceMessages -> namespaceMessages.get(String.valueOf(descriptor.getCode())));
  }

  private String resolveDefaultLocale() {
    return i18nLocaleService.getSupportedLanguages().stream()
        .filter(locale -> Boolean.TRUE.equals(locale.getIsDefault()))
        .map(I18nLocale::getLocale)
        .findFirst()
        .orElse(null);
  }

  private static String fallbackMessage(ResolvedErrorDescriptor descriptor) {
    if (descriptor.getFallbackMessageTemplate() != null) {
      return formatMessage(descriptor.getFallbackMessageTemplate(), descriptor.getMessageArgs());
    }
    return descriptor.getOriginalMessage();
  }

  private static String formatMessage(String template, Object[] messageArgs) {
    if (template == null) {
      return null;
    }
    if (messageArgs == null || messageArgs.length == 0) {
      return template;
    }
    try {
      return MessageFormat.format(template, messageArgs);
    } catch (IllegalArgumentException formatFailure) {
      return template;
    }
  }
}
