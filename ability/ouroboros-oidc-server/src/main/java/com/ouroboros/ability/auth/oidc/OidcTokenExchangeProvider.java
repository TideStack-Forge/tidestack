package com.ouroboros.ability.auth.oidc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.UserCenter;

/** Revalidates the current role/client restrictions before the standard code and refresh providers issue tokens. */
public final class OidcTokenExchangeProvider implements AuthenticationProvider {
  private final ConfigurationRegisteredClientRepository clients;
  private final OAuth2AuthorizationService authorizations;
  private final UserCenter users;
  private OAuth2AuthorizationCodeAuthenticationProvider code;
  private OAuth2RefreshTokenAuthenticationProvider refresh;

  public OidcTokenExchangeProvider(ConfigurationRegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations, UserCenter users) {
    this.clients = clients;
    this.authorizations = authorizations;
    this.users = users;
  }

  public void configureProviders(List<AuthenticationProvider> providers) {
    code = providers.stream().filter(OAuth2AuthorizationCodeAuthenticationProvider.class::isInstance)
        .map(OAuth2AuthorizationCodeAuthenticationProvider.class::cast).findFirst().orElseThrow();
    refresh = providers.stream().filter(OAuth2RefreshTokenAuthenticationProvider.class::isInstance)
        .map(OAuth2RefreshTokenAuthenticationProvider.class::cast).findFirst().orElseThrow();
    providers.remove(code);
    providers.remove(refresh);
    providers.add(0, this);
  }

  @Override
  public Authentication authenticate(Authentication request) {
    boolean refreshing = request instanceof OAuth2RefreshTokenAuthenticationToken;
    String value = refreshing ? ((OAuth2RefreshTokenAuthenticationToken) request).getRefreshToken()
        : ((OAuth2AuthorizationCodeAuthenticationToken) request).getCode();
    var authorization = authorizations.findByToken(value, new OAuth2TokenType(refreshing ? "refresh_token" : "code"));
    if (authorization == null || !(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken caller)
        || !caller.isAuthenticated() || caller.getRegisteredClient() == null
        || !authorization.getRegisteredClientId().equals(caller.getRegisteredClient().getId())) {
      return refreshing ? refresh.authenticate(request) : code.authenticate(request);
    }
    var token = authorization.getToken(value);
    if (token == null || !token.isActive()) { throw new OAuth2AuthenticationException("invalid_grant"); }
    var requested = refreshing ? ((OAuth2RefreshTokenAuthenticationToken) request).getScopes() : Set.<String>of();
    if (!authorization.getAuthorizedScopes().containsAll(requested)) { throw new OAuth2AuthenticationException("invalid_scope"); }
    var registered = clients.findById(authorization.getRegisteredClientId());
    var spec = clients.findSpec(authorization.getRegisteredClientId());
    var user = users.getUser(authorization.getPrincipalName()).filter(com.ouroboros.security.rbac.User::isEnabled).orElse(null);
    OidcAuthorizationPolicy.Draft grant = authorization.getAttribute(OidcAuthorizationPolicy.GRANT);
    if (registered == null || spec == null || user == null || grant == null || (refreshing && !grant.refreshAllowed())) {
      throw new OAuth2AuthenticationException("invalid_grant");
    }
    var effective = new HashSet<>(authorization.getAuthorizedScopes());
    effective.retainAll(registered.getScopes());
    if (!requested.isEmpty()) { effective.retainAll(requested); }
    var identity = new DefaultSecurityIdentity(user,
        AuthorizationBoundary.restricted(AuthorizationEvaluator.PLATFORM_API, grant.permissions()),
        new CredentialContext(CredentialContext.Kind.DELEGATED, spec.getClientId(), authorization.getId(), AuthorizationEvaluator.PLATFORM_API));
    effective.removeIf(scope -> scope.startsWith("authority:") && grant.permissions().keySet().stream().noneMatch(name ->
        ConfigurationRegisteredClientRepository.businessScope(name).equals(scope)
            && OidcGrantPermissions.current(grant, name)
            && AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, name) instanceof AuthorizationDecision.Permit));
    if (effective.isEmpty()) { throw new OAuth2AuthenticationException("invalid_grant"); }
    if (!effective.equals(authorization.getAuthorizedScopes())) {
      authorizations.save(OAuth2Authorization.from(authorization).authorizedScopes(effective).build());
    }
    if (refreshing) {
      var original = (OAuth2RefreshTokenAuthenticationToken) request;
      return refresh.authenticate(new OAuth2RefreshTokenAuthenticationToken(original.getRefreshToken(), caller,
          effective, original.getAdditionalParameters()));
    }
    return code.authenticate(request);
  }

  @Override
  public boolean supports(Class<?> type) {
    return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(type)
        || OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(type);
  }
}
