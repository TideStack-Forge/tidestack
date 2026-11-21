package com.ouroboros.ability.auth.oidc;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class OidcClientConfigurationTest {
  @Test
  void publicClientsRequirePkceAndConfidentialClientsReceiveRotatingRefreshTokens() {
    var values = new OidcClientConfigurationFixture();
    var secrets = mock(CompositeSecretResolver.class);
    var repository = new ConfigurationRegisteredClientRepository(values.service);
    var spec = client();
    values.setClients(List.of(spec));
    var publicClient = repository.findByClientId("app");
    assertTrue(publicClient.getClientSettings().isRequireProofKey());
    assertEquals(java.util.Set.of(ClientAuthenticationMethod.NONE), publicClient.getClientAuthenticationMethods());
    assertFalse(publicClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
    assertThrows(ValidationException.class, () -> repository.save(publicClient));
    String oldId = publicClient.getId();
    spec.setClientType(OidcClientSpec.ClientType.CONFIDENTIAL);
    spec.setClientSecretHash("{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret"));
    values.setClients(List.of(spec));
    when(secrets.resolve("test:secret")).thenReturn(Optional.of("{bcrypt}$2a$10$test-encoded-secret"));
    var confidential = repository.findByClientId("app");
    assertTrue(confidential.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
    assertFalse(confidential.getTokenSettings().isReuseRefreshTokens());
    assertEquals(oldId, repository.findById(oldId).getId());
    assertEquals(confidential.getId(), repository.findById(confidential.getId()).getId());
    spec.setClientSecretHash(null);
    values.setClients(List.of(spec));
    assertThrows(ValidationException.class, () -> repository.findByClientId("app"));
    spec.setClientSecretHash("plaintext");
    values.setClients(List.of(spec));
    assertThrows(ValidationException.class, () -> repository.findByClientId("app"));
    spec.setClientSecretHash("{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret"));
    spec.setEnabled(false);
    values.setClients(List.of(spec));
    assertNull(repository.findByClientId("app"));
    assertNull(repository.findByClientId("missing"));
  }

  @Test
  void rejectsInvalidRegistrationsBeforeSaving() {
    var validator = new OidcClientConfigurationValidator();
    assertTrue(validator.supports(ConfigurationRegisteredClientRepository.KEY));
    assertFalse(validator.supports("other"));
    for (String raw : List.of("broken-json", "null", "{}", "[null]", "[{\"clientId\":\"app\"}]")) {
      assertThrows(ValidationException.class, () -> validator.validate(raw));
    }
    var spec = client();
    assertThrows(ValidationException.class, () -> validator.validate(List.of(spec, spec)));
    for (String uri : List.of("http://public.example/callback", "https://client.example/*", "/callback",
        "https://user:password@client.example/callback", "https://client.example/callback#fragment")) {
      spec.setRedirectUris(List.of(uri));
      assertThrows(ValidationException.class, () -> validator.validate(List.of(spec)));
    }
    spec.setRedirectUris(List.of("http://127.0.0.1:3000/callback"));
    assertDoesNotThrow(() -> validator.validate(List.of(spec)));
    spec.getOidc().setScopes(List.of("admin"));
    assertThrows(ValidationException.class, () -> validator.validate(List.of(spec)));
    assertDoesNotThrow(() -> validator.validate(List.of()));
  }

  private OidcClientSpec client() {
    var spec = new OidcClientSpec();
    spec.setClientId("app");
    spec.setRedirectUris(List.of("https://client.example/callback"));
    spec.setRegistrationId(java.util.UUID.nameUUIDFromBytes(spec.getClientId().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    spec.getConsentPolicy().setMode(OidcClientSpec.ConsentMode.ADMIN_APPROVED);
    return spec;
  }
}
