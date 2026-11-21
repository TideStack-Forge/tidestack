package com.ouroboros.ability.auth.oidc;

import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.identity.*;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenIdentityResolver;

/** Uses the original bearer authentication path; ID Tokens and identity-only grants cannot enter APIs. */
public final class OidcAccessTokenIdentityResolver implements TokenIdentityResolver {
  private final JwtDecoder decoder;
  private final String issuer;
  private final OAuth2AuthorizationService authorizations;
  private final ConfigurationRegisteredClientRepository clients;
  private final UserCenter users;

  public OidcAccessTokenIdentityResolver(JwtDecoder decoder, String issuer, OAuth2AuthorizationService authorizations,
      ConfigurationRegisteredClientRepository clients, UserCenter users) {
    this.decoder = decoder; this.issuer = issuer; this.authorizations = authorizations; this.clients = clients; this.users = users;
  }

  public Optional<SecurityIdentity> resolve(String value) {
    org.springframework.security.oauth2.jwt.Jwt jwt;
    try { jwt = decoder.decode(value); } catch (JwtException error) { return Optional.empty(); }
    if (!issuer.equals(jwt.getClaimAsString("iss")) || !jwt.getAudience().contains(AuthorizationEvaluator.PLATFORM_API)
        || !"access_token".equals(jwt.getClaimAsString("token_use"))) { return Optional.empty(); }
    var authorization = authorizations.findByToken(value, OAuth2TokenType.ACCESS_TOKEN);
    if (authorization == null || authorization.getAccessToken() == null || !authorization.getAccessToken().isActive()) { return Optional.empty(); }
    OidcAuthorizationPolicy.Draft grant = authorization.getAttribute(OidcAuthorizationPolicy.GRANT);
    var spec = clients.findSpec(authorization.getRegisteredClientId());
    if (grant == null || spec == null || !spec.getApiAuthorization().isEnabled() || grant.permissions().isEmpty()) { return Optional.empty(); }
    var user = users.getUser(authorization.getPrincipalName()).filter(com.ouroboros.security.rbac.User::isEnabled).orElse(null);
    if (user == null || !jwt.getSubject().equals(OidcIdentityClaims.subject(user))) { return Optional.empty(); }
    var permissions = new LinkedHashMap<String, ResolvedAccessControlParams>();
    var tokenScopes = jwt.getClaimAsStringList("scope");
    grant.permissions().forEach((name, params) -> {
      if (spec.getApiAuthorization().getAllowedBoundary().getPermissions().contains(name)
          && tokenScopes != null && tokenScopes.contains(ConfigurationRegisteredClientRepository.businessScope(name))
          && OidcGrantPermissions.current(grant, name)) { permissions.put(name, params); }
    });
    if (permissions.isEmpty()) { return Optional.empty(); }
    return Optional.of(new DefaultSecurityIdentity(user,
        AuthorizationBoundary.restricted(AuthorizationEvaluator.PLATFORM_API, permissions),
        new CredentialContext(CredentialContext.Kind.DELEGATED, spec.getClientId(), authorization.getId(), AuthorizationEvaluator.PLATFORM_API)));
  }
}
