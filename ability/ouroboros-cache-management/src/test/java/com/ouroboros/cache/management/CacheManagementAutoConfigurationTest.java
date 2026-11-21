package com.ouroboros.cache.management;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagementAutoConfigurationTest {

  @Test
  void cacheManagementAutoConfiguration_doesNotExposeAnyAuthenticationApiProvider() {
    boolean hasAnyAuthenticationBean = Arrays.stream(CacheManagementAutoConfiguration.class.getDeclaredMethods())
        .filter(method -> method.getReturnType() == AnyAuthenticationApiProvider.class)
        .anyMatch(method -> method.getAnnotation(Bean.class) != null);

    assertFalse(hasAnyAuthenticationBean);
  }
}
