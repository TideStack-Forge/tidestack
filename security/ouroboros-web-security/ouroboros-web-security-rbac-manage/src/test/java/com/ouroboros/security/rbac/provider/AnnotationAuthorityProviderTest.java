package com.ouroboros.security.rbac.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.rbac.Application;
import com.ouroboros.webapi.RequestMappingMetadata;
import com.ouroboros.webapi.RequestMappingMetadataProvider;

@SpringBootTest(classes = {
    Application.class,
    AnnotationAuthorityProviderTest.TestController.class,
    AnnotationAuthorityProviderTest.RegisteredOnlyController.class,
    AnnotationAuthorityProviderTest.TestConfig.class
})
class AnnotationAuthorityProviderTest {

  @Autowired
  private AnnotationAuthorityProvider provider;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @BeforeEach
  void reloadAuthorities() {
    publishMetadataRefreshed();
  }

  @Test
  void singleValueAuthorityStillRegistersMappedApi() {
    var authorities = provider.getAuthority("system.test.single");

    assertEquals(1, authorities.size());
    assertTrue(authorities.get(0).getApis().contains("GET /api/test/authority/single"));
  }

  @Test
  void multiValueAuthorityRegistersSameApiUnderEachAuthority() {
    var firstAuthorities = provider.getAuthority("system.test.multi.first");
    var secondAuthorities = provider.getAuthority("system.test.multi.second");

    assertEquals(1, firstAuthorities.size());
    assertEquals(1, secondAuthorities.size());
    assertTrue(firstAuthorities.get(0).getApis().contains("POST /api/test/authority/multi"));
    assertTrue(secondAuthorities.get(0).getApis().contains("POST /api/test/authority/multi"));
  }

  @Test
  void metadataRefreshShouldDiscoverDeclaredRequestMappingMetadataWhenHandlerMethodsAreEmpty() {
    List<Authority> raw = provider.getAuthority("system.test.raw");
    assertEquals(1, raw.size());
    assertTrue(raw.get(0).getApis().contains("GET /api/test/authority/raw"));
  }

  @Test
  void metadataRefreshShouldIgnoreRegisteredMappingsWithoutDeclaredMetadata() {
    assertTrue(provider.getAuthority("system.test.registered.only").isEmpty());
  }

  @Test
  void getAuthorityShouldHandleNullAndCaseInsensitiveLookup() {
    assertTrue(provider.getAuthority(null).isEmpty());
    assertEquals(1, provider.getAuthority("SYSTEM.TEST.SINGLE").size());
  }

  @Test
  void getAllAuthoritiesShouldReturnUnmodifiableList() {
    var all = provider.getAllAuthorities();
    assertFalse(all.isEmpty());
    var unmodifiable = false;
    try {
      all.add(all.get(0));
    } catch (UnsupportedOperationException expected) {
      unmodifiable = true;
    }
    assertTrue(unmodifiable);
  }

  private void publishMetadataRefreshed() {
    publishMetadataRefreshed(this);
  }

  private void publishMetadataRefreshed(Object source) {
    eventPublisher.publishEvent(new OuroborosMetadataRefreshedEvent(source));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    RequestMappingMetadataProvider declaredRequestMappingMetadataProvider() throws NoSuchMethodException {
      List<RequestMappingMetadata> metadata = Arrays.asList(
          requestMappingMetadata("GET /api/test/authority/single", TestController.class, TestController.class.getMethod("single")),
          requestMappingMetadata("POST /api/test/authority/multi", TestController.class, TestController.class.getMethod("multi")),
          requestMappingMetadata("GET /api/test/authority/raw", RawController.class, RawController.class.getMethod("raw"))
      );
      return () -> metadata;
    }

    private RequestMappingMetadata requestMappingMetadata(String api, Class<?> handlerType, Method method) {
      return new RequestMappingMetadata(api, handlerType, method);
    }
  }

  @RestController
  @RequestMapping("/api/test/authority")
  static class TestController {

    @GetMapping("/single")
    @com.ouroboros.security.annotation.Authority("system.test.single")
    public String single() {
      return "single";
    }

    @PostMapping("/multi")
    @com.ouroboros.security.annotation.Authority({"system.test.multi.first", "system.test.multi.second"})
    public String multi() {
      return "multi";
    }
  }

  @RestController
  static class RegisteredOnlyController {

    @GetMapping("/api/test/authority/registered-only")
    @com.ouroboros.security.annotation.Authority("system.test.registered.only")
    public String registeredOnly() {
      return "registered-only";
    }
  }

  static class RawController {

    @com.ouroboros.security.annotation.Authority("system.test.raw")
    public String raw() {
      return "raw";
    }
  }
}
