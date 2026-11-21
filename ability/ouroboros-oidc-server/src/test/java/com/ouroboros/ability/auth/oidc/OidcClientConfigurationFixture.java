package com.ouroboros.ability.auth.oidc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.util.Json;

/** Mutable client registrations at the OIDC configuration-service boundary. */
class OidcClientConfigurationFixture {
  final ConfigurationService service = mock(ConfigurationService.class);
  private long revision;

  void setSettings(boolean enabled) {
    when(service.getSnapshot(OidcRuntimeSettings.KEY)).thenReturn(Optional.of(new com.ouroboros.configuration.ConfigurationSnapshot(
        OidcRuntimeSettings.KEY, new OidcRuntimeSettings(enabled, java.time.Duration.ofMinutes(5), java.time.Duration.ofHours(8)), null, "settings", null)));
  }

  void setClients(List<OidcClientSpec> clients) {
    String key = ConfigurationRegisteredClientRepository.KEY;
    setSettings(true);
    when(service.getSnapshot(key)).thenAnswer(call -> Optional.of(new com.ouroboros.configuration.ConfigurationSnapshot(
        key, Json.toList(Json.toJsonString(clients)), Json.toJsonString(clients), Long.toString(revision), null)));
    when(service.getRevision(key)).thenReturn(Optional.of(Long.toString(++revision)));
    when(service.getStoredRawValue(key)).thenReturn(Optional.of(Json.tryToJsonString(clients)
        .getOrElseThrow(error -> new IllegalArgumentException("Cannot serialize test OIDC clients", error))));
  }
}
