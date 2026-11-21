package com.ouroboros.ability.auth.oidc.service;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.session.UserSessionManager;

/** Uses the platform cache and session lifecycle for authorization codes and token grants. */
public class CacheOAuth2AuthorizationService implements OAuth2AuthorizationService {
  private static final String AUTHORIZATIONS = "ouroboros:oidc:authorizations";
  private static final String TOKEN_INDEX = "ouroboros:oidc:token-index";
  private final CacheManager cache;
  private final UserSessionManager<?> sessions;
  private final UserCenter users;
  private final RegisteredClientRepository clients;

  public CacheOAuth2AuthorizationService(CacheManager cache, UserSessionManager<?> sessions, UserCenter users,
      RegisteredClientRepository clients) {
    this.cache = cache;
    this.sessions = sessions;
    this.users = users;
    this.clients = clients;
  }

  @Override
  public void save(OAuth2Authorization authorization) {
    var original = authorization.<org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest>getAttribute(
        org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName());
    if (original != null && original.getAdditionalParameters().get(com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.DRAFT)
        instanceof com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.Draft draft) {
      if (!draft.user().equals(authorization.getPrincipalName()) || !draft.registration().equals(authorization.getRegisteredClientId())
          || !draft.requested().containsAll(authorization.getAuthorizedScopes())) {
        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant");
      }
      var restored = org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.from(original)
          .scopes(draft.requested()).additionalParameters(params -> params.remove(com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.DRAFT)).build();
      authorization = OAuth2Authorization.from(authorization)
          .attribute(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName(), restored)
          .attribute(com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.GRANT, draft)
          .attribute(com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.STAGE, draft.confirmation() ? "PENDING" : "AUTHORIZED").build();
    }
    var pendingDraft = authorization.<com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.Draft>getAttribute(
        com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.GRANT);
    if (pendingDraft == null) { throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant"); }
    if (authorization.getToken(OAuth2AuthorizationCode.class) != null) {
      authorization = OAuth2Authorization.from(authorization).attribute(com.ouroboros.ability.auth.oidc.OidcAuthorizationPolicy.STAGE, "AUTHORIZED").build();
    }
    User user = users.getUser(authorization.getPrincipalName()).filter(User::isEnabled)
        .orElseThrow(() -> new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant"));
    String subject = com.ouroboros.ability.auth.oidc.OidcIdentityClaims.subject(user);
    String boundSubject = authorization.getAttribute("ouroboros.publicSubject");
    if (boundSubject != null && !boundSubject.equals(subject)) {
      throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant");
    }
    authorization = OAuth2Authorization.from(authorization).attribute("ouroboros.publicSubject", subject).build();
    List<OAuth2Token> tokens = tokens(authorization);
    Instant expiresAt = tokens.stream().map(OAuth2Token::getExpiresAt).filter(Objects::nonNull)
        .max(Instant::compareTo).orElse(pendingDraft.expiresAt());
    Duration ttl = Duration.between(Instant.now(), expiresAt);
    if (ttl.isNegative() || ttl.isZero()) {
      remove(authorization);
      return;
    }
    var previous = cache.get(AUTHORIZATIONS, authorization.getId()).filter(OAuth2Authorization.class::isInstance)
        .map(OAuth2Authorization.class::cast).orElse(null);
    if (previous != null) { clearIndexes(previous); }
    cache.put(AUTHORIZATIONS, authorization.getId(), authorization, ttl);
    String consentState = authorization.getAttribute("state");
    if (consentState != null) { cache.put(TOKEN_INDEX, "state:" + tokenKey(consentState), authorization.getId(), ttl); }
    for (OAuth2Token token : tokens) {
      cache.put(TOKEN_INDEX, tokenKey(token.getTokenValue()), authorization.getId(), ttl);
    }
  }

  @Override
  public void remove(OAuth2Authorization authorization) {
    cache.evict(AUTHORIZATIONS, authorization.getId());
    clearIndexes(authorization);
  }

  private void clearIndexes(OAuth2Authorization authorization) {
    String state = authorization.getAttribute("state");
    if (state != null) { cache.evict(TOKEN_INDEX, "state:" + tokenKey(state)); }
    tokens(authorization).forEach(token -> cache.evict(TOKEN_INDEX, tokenKey(token.getTokenValue())));
  }

  @Override
  public OAuth2Authorization findById(String id) {
    OAuth2Authorization authorization = cache.get(AUTHORIZATIONS, id)
        .filter(OAuth2Authorization.class::isInstance).map(OAuth2Authorization.class::cast).orElse(null);
    if (authorization == null) { return null; }
    Authentication principal = authorization.getAttribute(Principal.class.getName());
    if (principal == null || !(principal.getDetails() instanceof String sessionId)
        || sessions.getSession(sessionId).filter(session -> session.getUser().equals(authorization.getPrincipalName()))
            .filter(session -> session.getExpiresAt() != null && session.getExpiresAt().isAfter(LocalDateTime.now())).isEmpty()
        || users.getUser(authorization.getPrincipalName()).filter(User::isEnabled).isEmpty()
        || clients.findById(authorization.getRegisteredClientId()) == null) {
      remove(authorization);
      return null;
    }
    String subject = authorization.getAttribute("ouroboros.publicSubject");
    User current = users.getUser(authorization.getPrincipalName()).orElse(null);
    if (current == null || subject == null || !subject.equals(com.ouroboros.ability.auth.oidc.OidcIdentityClaims.subject(current))) {
      remove(authorization);
      return null;
    }
    return authorization;
  }

  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    boolean state = tokenType != null && tokenType.getValue().equals("state");
    String id = cache.get(TOKEN_INDEX, state ? "state:" + tokenKey(token) : tokenKey(token)).filter(String.class::isInstance)
        .map(String.class::cast).orElse(null);
    if (id == null) { return null; }
    OAuth2Authorization authorization = findById(id);
    if (authorization == null) { return null; }
    if (state) { return token.equals(authorization.getAttribute("state")) ? authorization : null; }
    if (authorization.getToken(token) == null) { return null; }
    if (tokenType == null) { return authorization; }
    OAuth2Token expected = switch (tokenType.getValue()) {
      case "code" -> token(authorization.getToken(OAuth2AuthorizationCode.class));
      case "access_token" -> token(authorization.getAccessToken());
      case "refresh_token" -> token(authorization.getRefreshToken());
      case "id_token" -> token(authorization.getToken(OidcIdToken.class));
      default -> null;
    };
    return expected != null && expected.getTokenValue().equals(token) ? authorization : null;
  }

  private static OAuth2Token token(OAuth2Authorization.Token<?> token) {
    return token == null ? null : token.getToken();
  }

  private static List<OAuth2Token> tokens(OAuth2Authorization authorization) {
    return Stream.of(token(authorization.getToken(OAuth2AuthorizationCode.class)),
        token(authorization.getAccessToken()), token(authorization.getRefreshToken()),
        token(authorization.getToken(OidcIdToken.class))).filter(Objects::nonNull).toList();
  }

  public static String tokenKey(String token) {
    try {
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new InternalException("无法计算 OIDC 令牌索引", ex);
    }
  }
}
