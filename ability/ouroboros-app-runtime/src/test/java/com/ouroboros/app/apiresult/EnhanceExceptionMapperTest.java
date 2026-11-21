package com.ouroboros.app.apiresult;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ouroboros.app.error.ErrorResponseResolver;
import com.ouroboros.app.error.RequestLocaleResolver;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.errorcode.SimpleErrorCodeReference;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.error.ErrorMessageLocalizer;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EnhanceExceptionMapperTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();
  private GenericApplicationContext applicationContext;

  @AfterEach
  void cleanup() throws Exception {
    RequestContextHolder.resetRequestAttributes();
    if (applicationContext != null) {
      applicationContext.close();
      applicationContext = null;
    }
    setSpringApplicationContext(null);
  }

  @Test
  void webApiResultOf_preservesCodeAndLocalizedMessage() throws Exception {
    installResolver(localizedResolver());
    setCurrentRequestLocale("en-US");

    WebApiResult<?> result = WebApiResult.of(
        new BusinessException(new SimpleErrorCodeReference(40301, "AUTH-001", "登录已过期"))
    );

    assertEquals(40301, result.getCode());
    assertEquals("Please sign in again", result.getMsg());
  }

  @Test
  void webApiResultOf_keepsLegacyBusinessExceptionFallback() {
    WebApiResult<?> result = WebApiResult.of(new BusinessException(41001, "库存不足"));

    assertEquals(41001, result.getCode());
    assertEquals("库存不足", result.getMsg());
  }

  @Test
  void webApiResultOf_keepsValidationErrors() {
    Map<String, String> errors = new HashMap<>();
    errors.put("name", "必填");

    WebApiResult<?> result = WebApiResult.of(new com.ouroboros.exception.ValidationException(errors));

    assertEquals(422, result.getCode());
    assertEquals(errors, result.getErrors());
    assertFalse(result.getMsg().isEmpty());
  }

  private void installResolver(ErrorResponseResolver resolver) throws Exception {
    applicationContext = new GenericApplicationContext();
    applicationContext.registerBean(ErrorResponseResolver.class, () -> resolver);
    applicationContext.refresh();
    setSpringApplicationContext(applicationContext);
  }

  private static void setCurrentRequestLocale(String locale) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", locale);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private static void setSpringApplicationContext(ApplicationContext applicationContext) throws Exception {
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static ErrorResponseResolver localizedResolver() {
    return new ErrorResponseResolver(
        localeResolver(),
        (locale, descriptor) -> "en-US".equals(locale) ? "Please sign in again" : descriptor.getFallbackMessageTemplate(),
        registry(definition(40301, "未认证或登录已过期")),
        Collections.emptyList()
    );
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
