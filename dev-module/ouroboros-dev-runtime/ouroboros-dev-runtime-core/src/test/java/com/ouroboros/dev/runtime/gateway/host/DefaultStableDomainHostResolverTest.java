package com.ouroboros.dev.runtime.gateway.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DefaultStableDomainHostResolverTest {

  @Test
  void shouldResolveStableDomainHostFromAppProfileAndSuffix() {
    DefaultStableDomainHostResolver resolver = new DefaultStableDomainHostResolver("develop.example.com");

    assertEquals("crm-dev.develop.example.com", resolver.resolve("crm", "dev"));
  }

  @Test
  void shouldSanitizeAppAndProfileUsingKubernetesCompatibleRules() {
    DefaultStableDomainHostResolver resolver = new DefaultStableDomainHostResolver("develop.example.com");

    assertEquals("crm-center-team-a.develop.example.com", resolver.resolve("  CRM_Center  ", " Team_A "));
  }

  @Test
  void shouldReturnNullWhenSuffixIsBlank() {
    DefaultStableDomainHostResolver resolver = new DefaultStableDomainHostResolver("   ");

    assertNull(resolver.resolve("crm", "dev"));
  }

  @Test
  void shouldReturnNullWhenAppOrProfileIsBlank() {
    DefaultStableDomainHostResolver resolver = new DefaultStableDomainHostResolver("develop.example.com");

    assertNull(resolver.resolve(null, "dev"));
    assertNull(resolver.resolve("crm", " "));
  }
}
