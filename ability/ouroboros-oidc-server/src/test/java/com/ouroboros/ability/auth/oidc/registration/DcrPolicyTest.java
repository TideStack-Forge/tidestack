package com.ouroboros.ability.auth.oidc.registration;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.management.OidcConfigurationManagementPolicy;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.util.Json;

class DcrPolicyTest {
  private final DcrCredentials credentials = new DcrCredentials(new MemoryCacheManager(), () -> "https://issuer.example");
  private final OidcConfigurationManagementPolicy policy = new OidcConfigurationManagementPolicy(() -> true, credentials);
  private final ConfigurationManagementContext anonymous = new ConfigurationManagementContext(null, ConfigurationManagementContext.Purpose.MANAGEMENT);

  @Test void lockedOpenPolicyAppendsWithoutReplacingOtherClientsAndSuppliesAuditActor() {
    var settings = settings("OPEN");
    var candidate = candidate(settings, null);
    String request = UUID.randomUUID().toString();
    var command = command(candidate, request, null);
    var existing = candidate(settings, null);
    var result = policy.prepareWrite(anonymous, clients(List.of(existing)), command, Map.of(DcrSettings.KEY, row(settings)));
    assertThat(ConfigurationRegisteredClientRepository.parseAndValidate(result.value())).hasSize(2)
        .extracting(OidcClientSpec::getClientId).containsExactly(existing.getClientId(), candidate.getClientId());
    assertThat(result.auditActor()).isEqualTo("dcr:open:" + request);
    assertThat(policy.projectRead(anonymous, clients(result.value()))).isEmpty();
  }
  @Test void missingClosedChangedPolicyAndExhaustedQuotaRejectWithoutTrustingTheCommandType() {
    var settings = settings("OPEN");
    var candidate = candidate(settings, null);
    var command = command(candidate, UUID.randomUUID().toString(), null);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command, Map.of())).isInstanceOf(DcrFailure.class);
    var missing = new ConfigurationSnapshot(DcrSettings.KEY, settings, null, "7", null);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command, Map.of(DcrSettings.KEY, missing))).isInstanceOf(DcrFailure.class);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command,
        Map.of(DcrSettings.KEY, new ConfigurationSnapshot(DcrSettings.KEY, settings, "{}", "8", null)))).isInstanceOf(DcrFailure.class);
    var values = new java.util.LinkedHashMap<>(settings.toValue()); values.put("enabled", false);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command, Map.of(DcrSettings.KEY, row(DcrSettings.parse(values))))).isInstanceOf(DcrFailure.class);
    values.put("enabled", true); values.put("maxDynamicClients", 1);
    var existing = candidate(settings, null); existing.setEnabled(false);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of(existing)), command, Map.of(DcrSettings.KEY, row(DcrSettings.parse(values)))))
        .hasMessageContaining("registration_limit_exceeded");
  }
  @Test void credentialProofBindsTheExactFrozenCandidateAndCannotBeForgedOrReused() {
    var settings = settings("INITIAL_ACCESS_TOKEN");
    var token = credentials.issue("operator", Duration.ofMinutes(5));
    var candidate = candidate(settings, token.id());
    String request = UUID.randomUUID().toString();
    String persistent = Json.toJsonString(candidate);
    var claim = credentials.claim(token.token(), request, candidate.getClientId(), candidate.getRegistrationId(), DcrCredentials.digest(persistent), "7");
    var command = new DcrRegisterClientMutation(persistent, "7", request, claim);
    var accepted = policy.prepareWrite(anonymous, clients(List.of()), command, Map.of(DcrSettings.KEY, row(settings)));
    assertThat(accepted.auditActor()).isEqualTo("dcr:iat:" + token.id());
    candidate.setClientName("tampered");
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command(candidate, request, claim), Map.of(DcrSettings.KEY, row(settings))))
        .hasMessageContaining("invalid_token");
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command(candidate, request, new DcrCredentials.Claim(token.id(), "forged")),
        Map.of(DcrSettings.KEY, row(settings)))).hasMessageContaining("invalid_token");
    credentials.finish(claim, false);
    assertThatThrownBy(() -> policy.prepareWrite(anonymous, clients(List.of()), command, Map.of(DcrSettings.KEY, row(settings))))
        .hasMessageContaining("invalid_token");
  }
  @Test void concurrentClaimsAcrossAdaptersHaveExactlyOneWinnerAndRevokeDoesNotRestoreQualification() throws Exception {
    var cache = new MemoryCacheManager();
    var first = new DcrCredentials(cache, () -> "https://issuer.example");
    var second = new DcrCredentials(cache, () -> "https://issuer.example");
    var token = first.issue("operator", Duration.ofMinutes(5));
    CountDownLatch start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      var jobs = java.util.stream.IntStream.range(0, 16).mapToObj(index -> pool.submit(() -> {
        start.await();
        try { (index % 2 == 0 ? first : second).claim(token.token(), "request" + index, "client", "registration", "hash", "7"); return true; }
        catch (DcrFailure failure) { assertThat(failure.status()).isEqualTo(401); return false; }
      })).toList();
      start.countDown();
      int winners = 0;
      for (var job : jobs) { if (job.get(10, java.util.concurrent.TimeUnit.SECONDS)) { winners++; } }
      assertThat(winners).isEqualTo(1);
      assertThat(second.revoke(token.id())).isFalse();
    }
  }
  @Test void policyParsingAndMetadataEnforceScopeGrantAndCallbackRestrictions() {
    var values = new java.util.LinkedHashMap<>(settings("OPEN").toValue());
    values.put("allowedIdentityScopes", List.of("openid", "offline_access"));
    assertThatThrownBy(() -> DcrSettings.parse(values)).hasMessageContaining("DCR_SETTINGS_INVALID");
    values.put("allowedGrantTypes", List.of("authorization_code", "refresh_token"));
    var refresh = DcrSettings.parse(values);
    assertThat(DcrMetadata.accept(Map.of("redirect_uris", List.of("https://app.example/cb"), "grant_types", List.of("authorization_code", "refresh_token"), "scope", "openid offline_access"), refresh)
        .client().getGrantTypes()).contains("refresh_token");
    assertThatThrownBy(() -> DcrMetadata.accept(Map.of("redirect_uris", List.of("https://app.example/cb"), "token_endpoint_auth_method", "none", "grant_types", List.of("authorization_code", "refresh_token")), refresh))
        .hasMessageContaining("invalid_client_metadata");
    values.put("redirectPolicy", Map.of("allowedHttpsOrigins", List.of("https://allowed.example"), "allowLoopbackHttp", false));
    var restricted = DcrSettings.parse(values);
    for (String uri : List.of("https://other.example/cb", "http://127.0.0.1:1234/cb", "http://localhost:1234/cb")) {
      assertThatThrownBy(() -> DcrMetadata.accept(Map.of("redirect_uris", List.of(uri)), restricted)).hasMessageContaining("invalid_redirect_uri");
    }
    assertThat(DcrSettings.parse(Json.toJsonString(refresh)).toValue()).isEqualTo(refresh.toValue());
  }
  private DcrSettings settings(String mode) {
    var values = new java.util.LinkedHashMap<>(DcrSettings.defaults().toValue()); values.put("enabled", true); values.put("mode", mode);
    return DcrSettings.parse(values);
  }
  private OidcClientSpec candidate(DcrSettings settings, String tokenId) {
    var client = DcrMetadata.accept(Map.of("redirect_uris", List.of("https://app.example/cb"), "token_endpoint_auth_method", "none"), settings).client();
    client.setRegistration(new OidcClientSpec.Registration("DCR_" + settings.mode().name(), java.time.Instant.now().getEpochSecond(), tokenId));
    return client;
  }
  private DcrRegisterClientMutation command(OidcClientSpec client, String request, DcrCredentials.Claim claim) {
    return new DcrRegisterClientMutation(Json.toJsonString(client), "7", request, claim);
  }
  private ConfigurationSnapshot row(DcrSettings settings) { return new ConfigurationSnapshot(DcrSettings.KEY, settings, Json.toJsonString(settings), "7", null); }
  private ConfigurationSnapshot clients(Object value) { return new ConfigurationSnapshot(ConfigurationRegisteredClientRepository.KEY, value, "[]", "3", null); }
}
