package com.ouroboros.i18n.runtime.error;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.errorcode.ResolvedErrorDescriptor;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.i18n.runtime.service.I18nLocaleService;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultErrorMessageLocalizerTest {

  @Test
  void localizer_prefersTargetLocaleThenDefaultThenOriginalMessage() {
    Map<String, I18nLocale> locales = new HashMap<>();
    locales.put("zh-CN", locale("zh-CN", true, Collections.singletonMap("40301", "登录已过期")));
    locales.put("en-US", locale("en-US", false, Collections.singletonMap("40301", "Please sign in again")));
    locales.put("fr-FR", locale("fr-FR", false, Collections.emptyMap()));
    var localizer = new DefaultErrorMessageLocalizer(service(locales));
    var descriptor = ResolvedErrorDescriptor.builder()
        .code(40301)
        .fallbackMessageTemplate("登录已过期")
        .originalMessage("session expired")
        .build();

    assertEquals("Please sign in again", localizer.localize("en-US", descriptor));
    assertEquals("登录已过期", localizer.localize("fr-FR", descriptor));
  }

  @Test
  void localizer_formatsMessageArgumentsAndFallsBackToOriginalMessage() {
    Map<String, I18nLocale> locales = new HashMap<>();
    locales.put("zh-CN", locale("zh-CN", true, Collections.emptyMap()));
    locales.put("en-US", locale("en-US", false, Collections.singletonMap("41002", "Order {0} is invalid")));
    var localizer = new DefaultErrorMessageLocalizer(service(locales));
    var translatedDescriptor = ResolvedErrorDescriptor.builder()
        .code(41002)
        .fallbackMessageTemplate("订单 {0} 状态不正确")
        .messageArgs(new Object[]{"A1001"})
        .originalMessage("order status invalid")
        .build();
    var originalOnlyDescriptor = ResolvedErrorDescriptor.builder()
        .code(50001)
        .originalMessage("raw internal error")
        .build();

    assertEquals("Order A1001 is invalid", localizer.localize("en-US", translatedDescriptor));
    assertEquals("raw internal error", localizer.localize("fr-FR", originalOnlyDescriptor));
  }

  private static I18nLocale locale(String locale, boolean isDefault, Map<String, String> errors) {
    return new I18nLocaleImpl(
        locale,
        locale,
        "1.0.0",
        true,
        isDefault,
        Collections.singletonMap("errors", errors),
        1L,
        TextDirection.LTR
    );
  }

  private static I18nLocaleService service(Map<String, I18nLocale> locales) {
    return new I18nLocaleService() {
      @Override
      public Either<OuroborosException, I18nLocale> getLocale(String locale) {
        var target = locales.get(locale);
        if (target == null) {
          return Either.left(new BusinessException("Locale not found"));
        }
        return Either.right(target);
      }

      @Override
      public List<I18nLocale> getSupportedLanguages() {
        return new ArrayList<>(locales.values());
      }

      @Override
      public Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
