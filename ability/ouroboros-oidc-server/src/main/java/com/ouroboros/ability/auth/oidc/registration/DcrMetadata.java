package com.ouroboros.ability.auth.oidc.registration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.OidcRedirectUris;
import com.ouroboros.ability.auth.oidc.management.ClientSecrets;
import com.ouroboros.exception.ValidationException;

/** Narrow identity-only registration profile. Unknown extension metadata never maps to internal fields. */
public final class DcrMetadata {
  private DcrMetadata() { }
  public record Candidate(OidcClientSpec client, String secret) {
    @Override public String toString() { return "Candidate[REDACTED]"; }
  }
  private static final Set<String> UNSUPPORTED = Set.of("jwks_uri", "jwks", "sector_identifier_uri", "request_uris",
      "request_object_signing_alg", "request_object_encryption_alg", "request_object_encryption_enc", "id_token_encrypted_response_alg",
      "id_token_encrypted_response_enc", "userinfo_signed_response_alg", "userinfo_encrypted_response_alg", "userinfo_encrypted_response_enc",
      "default_max_age", "require_auth_time", "default_acr_values", "initiate_login_uri", "software_statement", "software_id", "software_version",
      "tls_client_certificate_bound_access_tokens", "dpop_bound_access_tokens", "client_uri", "logo_uri", "tos_uri", "policy_uri", "contacts");
  public static Candidate accept(Map<String,Object> request, DcrSettings settings) {
    if (request.keySet().stream().anyMatch(UNSUPPORTED::contains)) { throw invalid(); }
    String method = text(request, "token_endpoint_auth_method", "client_secret_basic");
    boolean confidential = method.equals("client_secret_basic");
    if (!confidential && !method.equals("none")) { throw invalid(); }
    var type = confidential ? OidcClientSpec.ClientType.CONFIDENTIAL : OidcClientSpec.ClientType.PUBLIC;
    if (!settings.allowedClientTypes().contains(type.name())) { throw invalid(); }
    String applicationType = text(request, "application_type", "web");
    if (!applicationType.matches("web|native")
        || !text(request, "subject_type", "public").equals("public")
        || !text(request, "id_token_signed_response_alg", "RS256").equals("RS256")) { throw invalid(); }
    List<String> grants = strings(request, "grant_types", List.of("authorization_code"));
    if (!grants.contains("authorization_code") || !settings.allowedGrantTypes().containsAll(grants)
        || !confidential && grants.contains("refresh_token")
        || !strings(request, "response_types", List.of("code")).equals(List.of("code"))) { throw invalid(); }
    String scope = text(request, "scope", settings.allowedIdentityScopes().contains("profile") ? "openid profile" : "openid");
    if (scope.isBlank() || !scope.equals(scope.trim())) { throw invalid(); }
    var scopes = List.of(scope.split(" +"));
    if (!scopes.contains("openid") || !settings.allowedIdentityScopes().containsAll(scopes)
        || scopes.contains("offline_access") && (!confidential || !grants.contains("refresh_token"))) { throw invalid(); }
    var client = new OidcClientSpec();
    client.setClientId("dcr-" + UUID.randomUUID());
    client.setRegistrationId(UUID.randomUUID().toString());
    client.setClientType(type); client.setGrantTypes(grants);
    client.setApplicationType(applicationType);
    String name = text(request, "client_name", client.getClientId());
    if (name.isBlank() || name.length() > 200 || name.chars().anyMatch(Character::isISOControl)) { throw invalid(); }
    client.setClientName(name);
    client.setRedirectUris(strings(request, "redirect_uris", List.of()));
    if (client.getRedirectUris().isEmpty() || client.getRedirectUris().size() > 20) { throw new DcrFailure(400, "invalid_redirect_uri"); }
    client.getOidc().setScopes(new ArrayList<>(scopes));
    client.getOidc().setPostLogoutRedirectUris(strings(request, "post_logout_redirect_uris", List.of()));
    client.getRedirectUris().forEach(uri -> validateRedirect(uri, settings));
    client.getOidc().getPostLogoutRedirectUris().forEach(uri -> validateRedirect(uri, settings));
    String secret = confidential ? ClientSecrets.generate() : null;
    if (secret != null) { client.setClientSecretHash(ClientSecrets.encode(secret)); }
    return new Candidate(client, secret);
  }
  /** Revalidate the complete frozen candidate under the locked policy, without generating or encoding anything. */
  public static void validateCandidate(OidcClientSpec client, DcrSettings settings) {
    if (!settings.allowedClientTypes().contains(client.getClientType().name())
        || !settings.allowedIdentityScopes().containsAll(client.getOidc().getScopes())
        || !settings.allowedGrantTypes().containsAll(client.getGrantTypes()) || client.getApiAuthorization().isEnabled()
        || !client.getApiAuthorization().getAllowedBoundary().getPermissions().isEmpty() || !client.getIntrospectionResources().isEmpty()
        || !client.getOidc().isEnabled() || client.getConsentPolicy().getMode() != OidcClientSpec.ConsentMode.EXPLICIT
        || !client.isEnabled()) { throw invalid(); }
    client.getRedirectUris().forEach(uri -> validateRedirect(uri, settings));
    client.getOidc().getPostLogoutRedirectUris().forEach(uri -> validateRedirect(uri, settings));
  }
  private static void validateRedirect(String value, DcrSettings settings) {
    try {
      URI uri = OidcRedirectUris.validate(value);
      if ("http".equals(uri.getScheme()) && (!settings.redirectPolicy().allowLoopbackHttp() || !OidcRedirectUris.loopbackIp(uri))) {
        throw new DcrFailure(400, "invalid_redirect_uri");
      }
      String origin = uri.getScheme() + "://" + uri.getRawAuthority();
      if ("https".equals(uri.getScheme()) && !settings.redirectPolicy().allowedHttpsOrigins().isEmpty()
          && !settings.redirectPolicy().allowedHttpsOrigins().contains(origin)) { throw new DcrFailure(400, "invalid_redirect_uri"); }
    } catch (ValidationException error) { throw new DcrFailure(400, "invalid_redirect_uri"); }
  }
  private static String text(Map<String,Object> request, String name, String defaults) {
    if (!request.containsKey(name)) { return defaults; }
    if (request.get(name) instanceof String text) { return text; }
    throw invalid();
  }
  private static List<String> strings(Map<String,Object> request, String name, List<String> defaults) {
    if (!request.containsKey(name)) { return defaults; }
    if (!(request.get(name) instanceof List<?> values) || values.stream().anyMatch(v -> !(v instanceof String text) || text.isBlank())) { throw invalid(); }
    return values.stream().map(String.class::cast).distinct().toList();
  }
  private static DcrFailure invalid() { return new DcrFailure(400, "invalid_client_metadata"); }
}
