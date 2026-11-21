package com.ouroboros.app.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import com.ouroboros.errorcode.ResolvedErrorDescriptor;
import com.ouroboros.errorcode.SimpleErrorCodeReference;
import com.ouroboros.errorcode.ThrowableErrorDescriptorMapper;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.error.ErrorMessageLocalizer;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;

import io.vavr.Tuple3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorResponseResolverTest {

  @Test
  void responseResolver_usesMapperRegistryAndLocalizerWhenAvailable() {
    var resolver = new ErrorResponseResolver(
        localeResolver(),
        (locale, descriptor) -> "en-US".equals(locale) ? "Please sign in again" : descriptor.getFallbackMessageTemplate(),
        registry(definition(40301, "未认证或登录已过期")),
        Collections.emptyList()
    );

    Tuple3<Integer, String, Object> result = resolver.resolve(
        new BusinessException(new SimpleErrorCodeReference(40301, "AUTH-LEGACY", "旧模板")),
        "en-US"
    );

    assertEquals(Integer.valueOf(40301), result._1);
    assertEquals("Please sign in again", result._2);
  }

  @Test
  void responseResolver_fallsBackWhenRegistryAndLocalizerAreMissing() {
    var resolver = new ErrorResponseResolver(localeResolver(), null, null, Collections.emptyList());

    Tuple3<Integer, String, Object> result = resolver.resolve(new BusinessException(41001, "库存不足"), null);

    assertEquals(Integer.valueOf(41001), result._1);
    assertEquals("库存不足", result._2);
  }

  @Test
  void responseResolver_keepsValidationErrors() {
    Map<String, String> errors = new HashMap<>();
    errors.put("name", "必填");
    var resolver = new ErrorResponseResolver(localeResolver(), null, null, Collections.emptyList());

    Tuple3<Integer, String, Object> result = resolver.resolve(new com.ouroboros.exception.ValidationException(errors), null);

    assertEquals(Integer.valueOf(422), result._1);
    assertEquals(errors, result._3);
    assertFalse(result._2.isEmpty());
  }

  @Test
  void responseResolver_allowsNonIntrusiveMapperRemapping() {
    var resolver = new ErrorResponseResolver(
        localeResolver(),
        localizer(),
        registry(definition(40301, "未认证或登录已过期")),
        Arrays.asList(new LowPriorityLegacyAuthMapper(), new HighPriorityLegacyAuthMapper())
    );

    Tuple3<Integer, String, Object> result = resolver.resolve(new LegacyAuthException("expired"), "en-US");

    assertEquals(Integer.valueOf(40301), result._1);
    assertEquals("Please sign in again", result._2);
  }

  private static RequestLocaleResolver localeResolver() {
    return new RequestLocaleResolver(Collections.singletonList(provider(
        locale("zh-CN", true, true),
        locale("en-US", false, true)
    )));
  }

  private static ErrorMessageLocalizer localizer() {
    return (locale, descriptor) -> "en-US".equals(locale) ? "Please sign in again" : descriptor.getFallbackMessageTemplate();
  }

  private static ErrorCodeRegistry registry(ErrorCodeDefinition... definitions) {
    return new ErrorCodeRegistry() {
      @Override
      public Optional<ErrorCodeDefinition> getDefinition(Integer code) {
        return Arrays.stream(definitions).filter(it -> code.equals(it.getCode())).findFirst();
      }

      @Override
      public List<ErrorCodeDefinition> getAllDefinitions() {
        return Arrays.asList(definitions);
      }
    };
  }

  private static ErrorCodeDefinition definition(int code, String template) {
    return new ErrorCodeDefinition()
        .setCode(code)
        .setTitle("CODE-" + code)
        .setDefaultMessageTemplate(template)
        .setEnabled(true);
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

  private static class LegacyAuthException extends RuntimeException {
    private LegacyAuthException(String message) {
      super(message);
    }
  }

  @Priority(10)
  private static class LowPriorityLegacyAuthMapper implements ThrowableErrorDescriptorMapper {
    @Override
    public boolean supports(Throwable throwable) {
      return throwable instanceof LegacyAuthException;
    }

    @Override
    public ResolvedErrorDescriptor map(Throwable throwable) {
      return ResolvedErrorDescriptor.builder()
          .code(49999)
          .fallbackMessageTemplate("wrong")
          .originalMessage(throwable.getMessage())
          .build();
    }
  }

  @Priority(100)
  private static class HighPriorityLegacyAuthMapper implements ThrowableErrorDescriptorMapper {
    @Override
    public boolean supports(Throwable throwable) {
      return throwable instanceof LegacyAuthException;
    }

    @Override
    public ResolvedErrorDescriptor map(Throwable throwable) {
      return ResolvedErrorDescriptor.builder()
          .code(40301)
          .fallbackMessageTemplate("登录已过期")
          .originalMessage(throwable.getMessage())
          .build();
    }
  }
}
