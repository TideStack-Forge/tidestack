package com.ouroboros.ability.auth.oidc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.security.authorize.AuthorizationEvaluator;

/** Restricts the standard introspection provider to explicitly assigned resource audiences. */
public final class OidcTokenIntrospectionProvider implements AuthenticationProvider {
  private final ConfigurationRegisteredClientRepository clients;
  private final OAuth2AuthorizationService authorizations;
  private final OidcAccessTokenIdentityResolver identities;
  private OAuth2TokenIntrospectionAuthenticationProvider delegate;

  public OidcTokenIntrospectionProvider(ConfigurationRegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations, OidcAccessTokenIdentityResolver identities) {
    this.clients = clients;
    this.authorizations = authorizations;
    this.identities = identities;
  }

  public void configureProviders(List<AuthenticationProvider> providers) {
    delegate = providers.stream().filter(OAuth2TokenIntrospectionAuthenticationProvider.class::isInstance)
        .map(OAuth2TokenIntrospectionAuthenticationProvider.class::cast).findFirst().orElseThrow();
    providers.remove(delegate);
    providers.add(0, this);
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    var request = (OAuth2TokenIntrospectionAuthenticationToken) authentication;
    if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken caller) || !caller.isAuthenticated()
        || caller.getRegisteredClient() == null) { return delegate.authenticate(authentication); }
    var consumer = clients.findSpec(caller.getRegisteredClient().getId());
    var grant = authorizations.findByToken(request.getToken(), OAuth2TokenType.ACCESS_TOKEN);
    if (consumer == null || grant == null || grant.getAccessToken() == null || !grant.getAccessToken().isActive()) {
      return inactive(request);
    }
    var claims = grant.getAccessToken().getClaims();
    if (claims == null || !"access_token".equals(claims.get("token_use"))
        || !(claims.get("aud") instanceof java.util.Collection<?> audiences)
        || audiences.stream().noneMatch(consumer.getIntrospectionResources()::contains)) { return inactive(request); }
    var issuedBy = clients.findById(grant.getRegisteredClientId());
    if (issuedBy == null) { return inactive(request); }
    var scopes = new java.util.HashSet<>(grant.getAccessToken().getToken().getScopes());
    scopes.retainAll(issuedBy.getScopes());
    if (audiences.contains(AuthorizationEvaluator.PLATFORM_API)) {
      var identity = identities.resolve(request.getToken()).orElse(null);
      if (identity == null) { return inactive(request); }
      Set<String> effective = AuthorizationEvaluator.standard().effectiveAuthorities(identity, AuthorizationEvaluator.PLATFORM_API)
          .keySet().stream().map(ConfigurationRegisteredClientRepository::businessScope).collect(java.util.stream.Collectors.toSet());
      scopes.removeIf(scope -> scope.startsWith("authority:") && !effective.contains(scope));
      if (scopes.stream().noneMatch(effective::contains)) { return inactive(request); }
    } else if (!scopes.contains("openid")) { return inactive(request); }
    var standard = (OAuth2TokenIntrospectionAuthenticationToken) delegate.authenticate(authentication);
    if (standard.getTokenClaims() == null || !standard.getTokenClaims().isActive()) { return inactive(request); }
    Map<String, Object> projected = new LinkedHashMap<>();
    for (String name : List.of("active", "iss", "sub", "aud", "iat", "exp", "token_type", "client_id")) {
      if (standard.getTokenClaims().getClaims().containsKey(name)) { projected.put(name, standard.getTokenClaims().getClaims().get(name)); }
    }
    projected.put("scope", scopes.stream().sorted().toList());
    return new OAuth2TokenIntrospectionAuthenticationToken(request.getToken(), caller,
        OAuth2TokenIntrospection.withClaims(projected).build());
  }

  private Authentication inactive(OAuth2TokenIntrospectionAuthenticationToken request) {
    return new OAuth2TokenIntrospectionAuthenticationToken(request.getToken(), (Authentication) request.getPrincipal(),
        OAuth2TokenIntrospection.builder().build());
  }

  @Override
  public boolean supports(Class<?> type) { return OAuth2TokenIntrospectionAuthenticationToken.class.isAssignableFrom(type); }
}
