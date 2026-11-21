package com.ouroboros.ability.auth.oidc.registration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ouroboros.ability.auth.oidc.OidcRuntimeSettings;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

class DcrFailureBoundaryTest {
  private final ConfigurationService configuration = mock(ConfigurationService.class);
  private final ConfigurationManagementService management = mock(ConfigurationManagementService.class);
  private final MemoryCacheManager cache = new MemoryCacheManager();
  private final DcrCredentials credentials = new DcrCredentials(cache, () -> "https://issuer.example");
  private final DcrRegistrationEndpoint endpoint = new DcrRegistrationEndpoint(configuration, management, credentials,
      new DcrRateLimiter(cache), () -> "https://issuer.example");
  @BeforeEach void configure() {
    var settings = new java.util.LinkedHashMap<>(DcrSettings.defaults().toValue()); settings.put("enabled", true);
    when(configuration.getSnapshot(DcrSettings.KEY)).thenReturn(Optional.of(new ConfigurationSnapshot(DcrSettings.KEY, settings, Json.toJsonString(settings), "7", null)));
    when(configuration.getSnapshot(OidcRuntimeSettings.KEY)).thenReturn(Optional.of(new ConfigurationSnapshot(OidcRuntimeSettings.KEY,
        new OidcRuntimeSettings(true, Duration.ofMinutes(5), Duration.ofHours(8)), "{}", "1", null)));
    when(configuration.getSnapshot(ConfigurationRegisteredClientRepository.KEY)).thenReturn(Optional.of(new ConfigurationSnapshot(
        ConfigurationRegisteredClientRepository.KEY, List.of(), "[]", "3", null)));
  }
  @Test void knownRollbackRetriesTheSameFrozenCandidateAndConsumesOneCredential() throws Exception {
    var token = credentials.issue("operator", Duration.ofMinutes(5));
    AtomicInteger attempts = new AtomicInteger();
    var commands = new java.util.ArrayList<ConfigurationMutation>();
    when(management.mutateManagementConfigurations(anyMap(), anyMap())).thenAnswer(call -> {
      Map<String, ConfigurationMutation> mutations = call.getArgument(0); commands.add(mutations.values().iterator().next());
      if (attempts.incrementAndGet() < 3) { throw new BusinessException(409, "[CONFIGURATION_REVISION_CONFLICT] contention"); }
      return Map.of();
    });
    var authenticated = endpoint.authenticate(endpoint.convert(request(token.token())));
    var response = new MockHttpServletResponse(); endpoint.success(request(token.token()), response, authenticated);
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(commands).hasSize(3); assertThat(commands.get(1)).isSameAs(commands.getFirst()); assertThat(commands.get(2)).isSameAs(commands.getFirst());
    var value = Json.toMap(response.getContentAsString());
    assertThat(value.get("client_secret")).isInstanceOf(String.class);
    assertThatThrownBy(() -> endpoint.authenticate(endpoint.convert(request(token.token())))).hasMessageContaining("invalid_token");
    verify(management,times(3)).mutateManagementConfigurations(anyMap(),anyMap());
    assertThat(state(token.id())).isEqualTo("CONSUMED");
  }
  @Test void unknownCommitAndPostCommitVisibilityFailuresNeverRetryOrReleaseQualification() {
    for (RuntimeException error : List.of(new IllegalStateException("database commit outcome unknown"),
        new BusinessException(503, "[CONFIGURATION_COMMITTED_VISIBILITY_UNCONFIRMED] committed"))) {
      reset(management);
      var token = credentials.issue("operator", Duration.ofMinutes(5));
      when(management.mutateManagementConfigurations(anyMap(),anyMap())).thenThrow(error);
      assertThatThrownBy(() -> endpoint.authenticate(endpoint.convert(request(token.token())))).hasMessageContaining("temporarily_unavailable");
      assertThat(state(token.id())).isEqualTo("CLOSED");
      assertThatThrownBy(() -> endpoint.authenticate(endpoint.convert(request(token.token())))).hasMessageContaining("invalid_token");
      verify(management,times(1)).mutateManagementConfigurations(anyMap(),anyMap());
    }
  }
  private String state(String id) { return (String) Json.toMap((String)cache.get(DcrCredentials.NAMESPACE,id).orElseThrow()).get("status"); }
  private MockHttpServletRequest request(String token) {
    var request = new MockHttpServletRequest("POST", DcrRegistrationEndpoint.PATH);
    request.setContentType("application/json"); request.setRemoteAddr("127.0.0.1"); request.addHeader("Authorization","Bearer " + token);
    request.setContent("{\"redirect_uris\":[\"https://app.example/callback\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return request;
  }
}
