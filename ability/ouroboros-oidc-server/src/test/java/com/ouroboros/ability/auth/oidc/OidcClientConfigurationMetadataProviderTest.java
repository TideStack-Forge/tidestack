package com.ouroboros.ability.auth.oidc;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.mockStatic;

import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.configuration.metadata.ConfigScope;

class OidcClientConfigurationMetadataProviderTest {
  @Test
  void importsSpringClientRegistrationsAsBackendOnlyUnifiedDefaults() {
    try (var context = new GenericApplicationContext();
         var spring = mockStatic(SpringContextUtil.class)) {
      context.setEnvironment(new MockEnvironment()
          .withProperty("ouroboros.security.oidc.clients[0].client-id", "existing-app")
          .withProperty("ouroboros.security.oidc.clients[0].redirect-uris[0]", "https://client.example/callback"));
      spring.when(SpringContextUtil::getApplicationContext).thenReturn(context);
      var metadata = new OidcClientConfigurationMetadataProvider().getAllConfigurationMetadata().getFirst();
      assertEquals(ConfigurationRegisteredClientRepository.KEY, metadata.getKey());
      assertEquals(ConfigScope.BACKEND, metadata.getScope());
      assertTrue(metadata.isModifiable());
      assertEquals("/system-setting/integration-management", metadata.getManagementEntry());
      assertTrue(metadata.isManagementTakenOver());
      var clients = ConfigurationRegisteredClientRepository.parseAndValidate(metadata.getDefaultValue());
      assertEquals("existing-app", clients.getFirst().getClientId());
      assertEquals(List.of("https://client.example/callback"), clients.getFirst().getRedirectUris());
    }
  }

  @Test
  void defaultsToNoRegistrationsWhenNoSpringContextIsAvailable() {
    try (var spring = mockStatic(SpringContextUtil.class)) {
      spring.when(SpringContextUtil::getApplicationContext).thenReturn(null);
      var metadata = new OidcClientConfigurationMetadataProvider().getAllConfigurationMetadata().getFirst();
      assertTrue(ConfigurationRegisteredClientRepository.parseAndValidate(metadata.getDefaultValue()).isEmpty());
    }
  }
}
