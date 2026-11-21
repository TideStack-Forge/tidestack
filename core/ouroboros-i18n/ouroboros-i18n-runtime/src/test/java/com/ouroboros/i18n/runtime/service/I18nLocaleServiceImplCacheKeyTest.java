package com.ouroboros.i18n.runtime.service;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nLocaleServiceImplCacheKeyTest {

  @Test
  void cacheKeyShouldNotDependOnJavaParameterNameMetadata() throws Exception {
    Method method = I18nLocaleServiceImpl.class.getDeclaredMethod("getLocale", String.class);

    assertEquals("#p0", method.getAnnotation(Cacheable.class).key());
  }
}
