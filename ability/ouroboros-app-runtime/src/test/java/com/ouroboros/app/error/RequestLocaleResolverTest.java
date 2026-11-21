package com.ouroboros.app.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestLocaleResolverTest {

  @Test
  void requestLocaleResolver_normalizesAcceptLanguageAndFallsBackToDefault() {
    var resolver = new RequestLocaleResolver(Collections.singletonList(provider(
        locale("zh-CN", true, true),
        locale("en-US", false, true)
    )));

    assertEquals("zh-CN", resolver.resolve("zh_CN,zh;q=0.9"));
    assertEquals("en-US", resolver.resolve("en-GB,en;q=0.8"));
    assertEquals("zh-CN", resolver.resolve(null));
    assertEquals("zh-CN", resolver.resolve("fr-FR,fr;q=0.9"));
  }

  private static I18nLocaleProvider provider(I18nLocale... locales) {
    return new I18nLocaleProvider() {
      private final Map<String, I18nLocale> localeMap = map(locales);

      @Override
      public Optional<I18nLocale> getDictionary(String locale) {
        return Optional.ofNullable(localeMap.get(locale));
      }

      @Override
      public List<String> getSupportedLocales() {
        return Arrays.asList(localeMap.keySet().toArray(new String[0]));
      }
    };
  }

  private static Map<String, I18nLocale> map(I18nLocale... locales) {
    Map<String, I18nLocale> localeMap = new HashMap<>();
    for (I18nLocale locale : locales) {
      localeMap.put(locale.getLocale(), locale);
    }
    return localeMap;
  }

  private static I18nLocale locale(String locale, boolean isDefault, boolean enabled) {
    return new SimpleI18nLocale(locale, isDefault, enabled);
  }

  private static class SimpleI18nLocale implements I18nLocale {
    private final String locale;
    private final Boolean isDefault;
    private final Boolean enabled;

    private SimpleI18nLocale(String locale, Boolean isDefault, Boolean enabled) {
      this.locale = locale;
      this.isDefault = isDefault;
      this.enabled = enabled;
    }

    @Override
    public String getLocale() {
      return locale;
    }

    @Override
    public String getName() {
      return locale;
    }

    @Override
    public String getVersion() {
      return "1.0.0";
    }

    @Override
    public Boolean getEnabled() {
      return enabled;
    }

    @Override
    public Boolean getIsDefault() {
      return isDefault;
    }

    @Override
    public Map<String, Map<String, String>> getMessages() {
      return Collections.emptyMap();
    }

    @Override
    public TextDirection getDirection() {
      return TextDirection.LTR;
    }

    @Override
    public Long getTimestamp() {
      return 1L;
    }
  }
}
