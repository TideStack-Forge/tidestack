package com.ouroboros.app.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ouroboros.app.error.ErrorResponseResolver;
import com.ouroboros.app.error.RequestLocaleResolver;
import com.ouroboros.errorcode.SimpleErrorCodeReference;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExceptionControllerAdviceTest {

  @Test
  void controllerAdvice_matchesLocalizedBusinessExceptionPayload() {
    var advice = new ExceptionControllerAdvice(localizedResolver());

    WebApiResult<?> result = advice.exceptionHandler(
        request("GET", "/api/orders", "en-US"),
        new BusinessException(new SimpleErrorCodeReference(40301, "AUTH-001", "登录已过期"))
    );

    assertEquals(40301, result.getCode());
    assertEquals("Please sign in again", result.getMsg());
  }

  @Test
  void controllerAdvice_keepsLegacyBusinessExceptionFallback() {
    var advice = new ExceptionControllerAdvice(fallbackResolver());

    WebApiResult<?> result = advice.exceptionHandler(
        request("POST", "/api/orders", "zh-CN"),
        new BusinessException(41001, "库存不足")
    );

    assertEquals(41001, result.getCode());
    assertEquals("库存不足", result.getMsg());
  }

  @Test
  void controllerAdvice_keepsValidationErrors() {
    Map<String, List<String>> errors = new HashMap<>();
    errors.put("name", Collections.singletonList("必填"));
    var advice = new ExceptionControllerAdvice(fallbackResolver());

    WebApiResult<?> result = advice.exceptionHandler(
        request("POST", "/api/orders", "zh-CN"),
        new com.ouroboros.data.exception.ValidationException(errors)
    );

    assertEquals(422, result.getCode());
    assertEquals(Collections.singletonMap("name", "必填"), result.getErrors());
    assertFalse(result.getMsg().isEmpty());
  }

  private static MockHttpServletRequest request(String method, String uri, String locale) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.addHeader("Accept-Language", locale);
    return request;
  }

  private static ErrorResponseResolver localizedResolver() {
    return new ErrorResponseResolver(
        localeResolver(),
        (locale, descriptor) -> "en-US".equals(locale) ? "Please sign in again" : descriptor.getFallbackMessageTemplate(),
        registry(definition(40301, "未认证或登录已过期")),
        Collections.emptyList()
    );
  }

  private static ErrorResponseResolver fallbackResolver() {
    return new ErrorResponseResolver(localeResolver(), null, null, Collections.emptyList());
  }

  private static RequestLocaleResolver localeResolver() {
    return new RequestLocaleResolver(Collections.singletonList(provider(
        locale("zh-CN", true, true),
        locale("en-US", false, true)
    )));
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
}
