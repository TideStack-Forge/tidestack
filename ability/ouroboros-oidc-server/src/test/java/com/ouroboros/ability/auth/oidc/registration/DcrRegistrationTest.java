package com.ouroboros.ability.auth.oidc.registration;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.util.Json;

class DcrRegistrationTest {
  @Test void defaultsAreClosedAndMetadataCannotGrantBusinessAccess() {
    var settings = DcrSettings.defaults();
    assertThat(settings.enabled()).isFalse();
    var input = Map.<String,Object>of("redirect_uris", List.of("https://app.example/callback"),
        "token_endpoint_auth_method", "none", "apiAuthorization", Map.of("enabled", true));
    var candidate = DcrMetadata.accept(input, settings);
    var client = candidate.client();
    assertThat(client.getOidc().getScopes()).containsExactly("openid", "profile");
    assertThat(client.getApiAuthorization().isEnabled()).isFalse();
    assertThat(client.getApiAuthorization().getAllowedBoundary().getPermissions()).isEmpty();
    assertThat(client.getIntrospectionResources()).isEmpty();
    assertThat(client.getGrantTypes()).containsExactly("authorization_code");
    assertThat(candidate.secret()).isNull();
    assertThat(ConfigurationRegisteredClientRepository.parseAndValidate(List.of(client))).hasSize(1);
  }
  @Test void unsupportedRecognizedMetadataIsRejectedAndUnknownMetadataIgnored() {
    for (var fields : List.of(Map.of("grant_types", List.of("client_credentials")),
        Map.of("scope", "openid authority:admin"), Map.of("jwks_uri", "https://keys.example"),
        Map.of("token_endpoint_auth_method", "client_secret_post"))) {
      var request = new java.util.LinkedHashMap<String,Object>(fields);
      request.put("redirect_uris", List.of("https://app.example/callback"));
      assertThatThrownBy(() -> DcrMetadata.accept(request, DcrSettings.defaults())).hasMessageContaining("invalid_client_metadata");
    }
    assertThatThrownBy(() -> DcrMetadata.accept(Map.of("redirect_uris", List.of("https://evil.example/cb#token")), DcrSettings.defaults()))
        .hasMessageContaining("invalid_redirect_uri");
  }
  @Test void confidentialSecretIsGeneratedOnceAndOnlyHashEntersCandidate() {
    var candidate = DcrMetadata.accept(Map.of("redirect_uris", List.of("https://app.example/callback")), DcrSettings.defaults());
    assertThat(candidate.client().getClientType()).isEqualTo(OidcClientSpec.ClientType.CONFIDENTIAL);
    assertThat(candidate.secret()).hasSize(43);
    assertThat(candidate.client().getClientSecretHash()).startsWith("{bcrypt}");
    assertThat(Json.toJsonString(candidate.client())).doesNotContain(candidate.secret());
    assertThat(candidate.toString()).doesNotContain(candidate.secret());
  }
  @Test void initialCredentialCanBeClaimedOnlyOnceAndCannotBeRevokedAfterClaim() {
    var credentials = new DcrCredentials(new MemoryCacheManager(), () -> "https://issuer.example");
    var issued = credentials.issue("operator", Duration.ofMinutes(30));
    var claim = credentials.claim(issued.token(), "request", "client", "registration", "candidate", "7");
    credentials.verify(claim, "request", "client", "registration", "candidate", "7");
    assertThatThrownBy(() -> credentials.claim(issued.token(), "replay", "c", "r", "x", "7")).hasMessageContaining("invalid_token");
    assertThat(credentials.revoke(issued.id())).isFalse();
    assertThatThrownBy(() -> credentials.verify(claim, "different", "client", "registration", "candidate", "7"))
        .hasMessageContaining("invalid_token");
    credentials.finish(claim, true);
    assertThatThrownBy(() -> credentials.verify(claim, "request", "client", "registration", "candidate", "7"))
        .hasMessageContaining("invalid_token");
    assertThat(claim.toString()).doesNotContain(issued.token());
  }
  @Test void revokedAndForeignIssuerCredentialsAreRejected() {
    var cache = new MemoryCacheManager();
    var first = new DcrCredentials(cache, () -> "https://issuer.example");
    var other = new DcrCredentials(cache, () -> "https://other.example");
    var issued = first.issue("operator", Duration.ofMinutes(1));
    assertThatThrownBy(() -> other.claim(issued.token(), "r", "c", "i", "h", "1")).hasMessageContaining("invalid_token");
    assertThat(first.revoke(issued.id())).isTrue();
    assertThatThrownBy(() -> first.claim(issued.token(), "r", "c", "i", "h", "1")).hasMessageContaining("invalid_token");
  }
}
