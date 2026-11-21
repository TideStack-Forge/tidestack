package com.ouroboros.ability.auth.config;

import com.ouroboros.security.externalauth.config.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.*;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.configuration.metadata.ConfigScope;

class ExternalAuthConfigurationMetadataProviderTest {
  @Test
  void importsExistingSpringPropertiesAsBackendOnlyUnifiedDefaults() {
    try (var context = new GenericApplicationContext();
         var spring = mockStatic(SpringContextUtil.class)) {
      context.setEnvironment(new MockEnvironment()
          .withProperty("ouroboros.security.external-auth.public-base-url", "https://platform.example")
          .withProperty("ouroboros.security.external-auth.ldap[0].key", "corp")
          .withProperty("ouroboros.security.external-auth.ldap[0].urls[0]", "ldaps://corp.example")
          .withProperty("ouroboros.security.external-auth.external-call.oauth2.connect-timeout", "3s"));
      spring.when(SpringContextUtil::getApplicationContext).thenReturn(context);
      var metadata = new ExternalAuthConfigurationMetadataProvider().getAllConfigurationMetadata();
      var external = metadata.stream().filter(item -> item.getKey().equals(UnifiedExternalAuthConfiguration.KEY)).findFirst().orElseThrow();
      var defaults = UnifiedExternalAuthConfiguration.parse(external.getDefaultValue());
      assertEquals("corp", defaults.getLdap().getFirst().getKey());
      assertEquals("https://platform.example", defaults.getPublicBaseUrl());
      assertEquals(java.time.Duration.ofSeconds(3), defaults.getExternalCall().getOauth2().getConnectTimeout());
      assertTrue(metadata.stream().allMatch(item -> item.getScope() == ConfigScope.BACKEND && item.isModifiable()));
    }
  }
}
