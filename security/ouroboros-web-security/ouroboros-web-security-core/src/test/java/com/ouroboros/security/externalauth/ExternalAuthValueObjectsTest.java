package com.ouroboros.security.externalauth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.externalauth.config.CasProviderSpec;
import com.ouroboros.security.externalauth.config.LdapProviderSpec;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalAuthValueObjectsTest {

  @Test
  void authErrorFactoriesSetCodes() {
    assertEquals(40101, AuthError.invalidCredentials("m").getCode());
    assertEquals(40102, AuthError.invalidState("m").getCode());
    assertEquals(40901, AuthError.bindingRequired("m").getCode());
    assertEquals(50201, AuthError.providerUnavailable("m").getCode());
  }

  @Test
  void authResultAndAuthorizeUrlResultPreserveState() {
    var session = new SimpleUserSession("s1");

    var loggedIn = AuthResult.loggedIn(session, "u1");
    assertEquals(AuthResult.Type.LOGGED_IN, loggedIn.getType());
    assertSame(session, loggedIn.getSession());
    assertEquals("u1", loggedIn.getUserIdentity());
    assertNull(loggedIn.getBindingToken());

    var bindingRequired = AuthResult.bindingRequired("bt-1");
    assertEquals(AuthResult.Type.BINDING_REQUIRED, bindingRequired.getType());
    assertEquals("bt-1", bindingRequired.getBindingToken());
    assertNull(bindingRequired.getSession());

    var cache = new HashMap<String, Object>();
    cache.put("k", "v");
    var result = AuthorizeUrlResult.of("https://idp/auth", "state-1", cache);
    cache.put("k", "changed");
    assertEquals("https://idp/auth", result.getAuthorizeUrl());
    assertEquals("state-1", result.getState());
    assertEquals("v", result.getCacheEntry().get("k"));
    assertNotSame(cache, result.getCacheEntry());
  }

  @Test
  void callbackExchangeAndIdentityObjectsExposeImmutableViews() {
    var cachedState = new HashMap<String, Object>();
    cachedState.put("returnUrl", "/home");
    var callbackParams = CallbackParams.fromRedirectCallback(
        "state-1", Map.of("code", List.of("code-1")), cachedState, "corr-1");
    cachedState.put("returnUrl", "/changed");

    assertEquals("code-1", callbackParams.getSingleCallbackParameter("code"));
    assertEquals("state-1", callbackParams.getState());
    assertEquals("/home", callbackParams.getCachedState().get("returnUrl"));
    assertEquals("corr-1", callbackParams.getCorrelationId());

    var rawCallbackParameters = new HashMap<String, List<String>>();
    rawCallbackParameters.put("authCode", List.of("ding-code"));
    var redirectCallback = CallbackParams.fromRedirectCallback(
        "state-2", rawCallbackParameters, cachedState, "corr-2");
    rawCallbackParameters.put("authCode", List.of("changed"));
    assertNull(redirectCallback.getSingleCallbackParameter("code"));
    assertEquals("ding-code", redirectCallback.getSingleCallbackParameter("authCode"));
    assertTrue(redirectCallback.hasCallbackParameter("authCode"));
    assertTrue(redirectCallback.hasCallbackParameters());

    var code = ExchangeParams.ofCode("code-2", "https://cb", "verifier", "corr-2");
    assertTrue(code.isCodeExchange());
    assertEquals("code-2", code.getTicketOrCode());
    assertEquals("https://cb", code.getRedirectUri());
    assertEquals("verifier", code.getCodeVerifier());
    assertEquals("corr-2", code.getCorrelationId());

    var ticket = ExchangeParams.ofTicket("ticket-1", "corr-3");
    assertTrue(ticket.isCodeExchange());
    assertEquals("ticket-1", ticket.getTicketOrCode());
    assertEquals("ticket-1", ticket.getTicket());

    var idToken = ExchangeParams.ofIdToken("jwt-1", "corr-4");
    assertFalse(idToken.isCodeExchange());
    assertEquals("jwt-1", idToken.getIdToken());
    assertEquals("corr-4", idToken.getCorrelationId());

    var attrs = new HashMap<String, Object>();
    attrs.put("sub", "123");
    var identity = ExternalIdentity.of("ldap", "123", "Alice", "alice@example.com", attrs);
    attrs.put("sub", "456");
    assertEquals("ldap", identity.getProviderKey());
    assertEquals("123", identity.getExternalId());
    assertEquals("Alice", identity.getDisplayName());
    assertEquals("alice@example.com", identity.getEmail());
    assertEquals("123", identity.getAttributes().get("sub"));

    var spec = new ProviderSpec();
    spec.setKey("oauth2");
    spec.setType("oauth2");
    spec.setDisplayName("OAuth2");
    spec.setEnabled(false);
    assertEquals("oauth2", spec.getKey());
    assertFalse(spec.isEnabled());

    var cas = new CasProviderSpec();
    cas.setValidatePath("/p3/serviceValidate");
    cas.setCasBaseUrl("https://cas");
    cas.setExternalIdField("uid");
    cas.setAttributeMapping(java.util.Collections.singletonMap("mail", "email"));
    assertEquals("/p3/serviceValidate", cas.getValidatePath());
    assertEquals("https://cas", cas.getCasBaseUrl());
    assertEquals("uid", cas.getExternalIdField());
    assertEquals("email", cas.getAttributeMapping().get("mail"));

    var oauth = new OAuth2ProviderSpec();
    oauth.setAuthorizationUri("https://idp/auth");
    oauth.setTokenUri("https://idp/token");
    oauth.setUserInfoUri("https://idp/user");
    oauth.setJwksUri("https://idp/jwks");
    oauth.setIssuer("https://issuer");
    oauth.setClientId("client");
    oauth.setClientSecret("secret");
    oauth.setClientSecretRef("vault://secret");
    oauth.setScopes(java.util.Arrays.asList("openid", "email"));
    assertEquals("sub", oauth.getExternalIdField());
    assertTrue(oauth.isPkceEnabled() == false);
    oauth.setPkceEnabled(true);
    oauth.setPkceMethod("plain");
    oauth.setExternalIdField("uid");
    assertEquals("https://idp/auth", oauth.getAuthorizationUri());
    assertEquals("https://idp/token", oauth.getTokenUri());
    assertEquals("https://idp/user", oauth.getUserInfoUri());
    assertEquals("https://idp/jwks", oauth.getJwksUri());
    assertEquals("https://issuer", oauth.getIssuer());
    assertEquals("client", oauth.getClientId());
    assertEquals("secret", oauth.getClientSecret());
    assertEquals("vault://secret", oauth.getClientSecretRef());
    assertEquals(2, oauth.getScopes().size());
    assertEquals("uid", oauth.getExternalIdField());
    assertEquals("plain", oauth.getPkceMethod());
    assertTrue(oauth.isPkceEnabled());

    var ldapDefault = new LdapProviderSpec();
    assertEquals("search", ldapDefault.getMode());
    assertEquals("AUTO", ldapDefault.getLoginIdMode());

    var ldap = new LdapProviderSpec();
    ldap.setUrls(java.util.Arrays.asList("ldap://a", "ldap://b"));
    ldap.setBaseDn("dc=test,dc=org");
    ldap.setMode("bind");
    ldap.setSearchBindDn("cn=reader");
    ldap.setSearchBindPassword("pwd");
    ldap.setSearchBindPasswordRef("vault://ldap");
    ldap.setSearchFilter("(uid={0})");
    ldap.setDomain("TEST");
    ldap.setLoginIdMode("USERNAME");
    ldap.setExternalIdAttribute("entryUUID");
    ldap.setAttributeMapping(java.util.Collections.singletonMap("mail", "email"));
    assertEquals(2, ldap.getUrls().size());
    assertEquals("dc=test,dc=org", ldap.getBaseDn());
    assertEquals("bind", ldap.getMode());
    assertEquals("cn=reader", ldap.getSearchBindDn());
    assertEquals("pwd", ldap.getSearchBindPassword());
    assertEquals("vault://ldap", ldap.getSearchBindPasswordRef());
    assertEquals("(uid={0})", ldap.getSearchFilter());
    assertEquals("TEST", ldap.getDomain());
    assertEquals("USERNAME", ldap.getLoginIdMode());
    assertEquals("entryUUID", ldap.getExternalIdAttribute());
    assertEquals("email", ldap.getAttributeMapping().get("mail"));

    assertEquals(1, new ProviderSpecListSource().getProviderSpecs().size());
  }

  @Test
  void tokenAndExceptionTypesCarryConstructorState() {
    var claims = new HashMap<String, Object>();
    claims.put("sub", "u1");
    var info = new TokenInfo("u1", "sid", claims, 99L);
    assertEquals("u1", info.getUserIdentity());
    assertEquals("sid", info.getSessionId());
    assertSame(claims, info.getClaims());
    assertEquals(99L, info.getExpiresAt());

    var pair = new TokenPair("a", 10L, "r", 20L);
    assertEquals("a", pair.getAccessToken());
    assertEquals(10L, pair.getAccessTokenExpiresAt());
    assertEquals("r", pair.getRefreshToken());
    assertEquals(20L, pair.getRefreshTokenExpiresAt());

    assertTrue(new com.ouroboros.security.exception.TokenException("x") {
    }.isInvalidToken());

    var cause = new RuntimeException("root");
    assertEquals("m1", new com.ouroboros.security.exception.InvalidTokenException("m1").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.InvalidTokenException("m2", cause).getCause());
    assertEquals("m3", new com.ouroboros.security.exception.TokenAlreadyUsedException("m3").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.TokenAlreadyUsedException("m4", cause).getCause());
    assertEquals("m5", new com.ouroboros.security.exception.TokenExpiredException("m5").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.TokenExpiredException("m6", cause).getCause());
    assertEquals("m7", new com.ouroboros.security.exception.TokenParseException("m7").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.TokenParseException("m8", cause).getCause());
    assertEquals("m9", new com.ouroboros.security.exception.TokenRevokedException("m9").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.TokenRevokedException("m10", cause).getCause());
    assertEquals("m11", new com.ouroboros.security.exception.SessionException("m11").getMessage());
    assertSame(cause, new com.ouroboros.security.exception.SessionException("m12", cause).getCause());
  }

  private static class SimpleUserSession implements UserSession {
    private final String id;

    private SimpleUserSession(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getUser() {
      return "u";
    }

    @Override
    public String getClientType() {
      return "web";
    }

    @Override
    public String getIpAddress() {
      return "127.0.0.1";
    }

    @Override
    public Map<String, String> getClientDetails() {
      return java.util.Collections.emptyMap();
    }

    @Override
    public java.time.LocalDateTime getLastAccessedAt() {
      return null;
    }

    @Override
    public void setLastAccessedAt(java.time.LocalDateTime lastAccessedAt) {
    }

    @Override
    public java.time.LocalDateTime getCreatedAt() {
      return null;
    }

    @Override
    public java.time.LocalDateTime getUpdatedAt() {
      return null;
    }

    @Override
    public void setUpdatingAt(java.time.LocalDateTime updatedAt) {
    }

    @Override
    public java.time.LocalDateTime getExpiresAt() {
      return null;
    }

    @Override
    public void setExpiresAt(java.time.LocalDateTime expiresAt) {
    }

    @Override
    public Map<String, Object> getAttributes() {
      return java.util.Collections.emptyMap();
    }

    @Override
    public <T> T getAttribute(String name) {
      return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
    }

    @Override
    public void removeAttribute(String name) {
    }
  }

  private static class ProviderSpecListSource implements com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource {
    @Override
    public java.util.List<ProviderSpec> getProviderSpecs() {
      return java.util.Collections.<ProviderSpec>singletonList(new ProviderSpec());
    }
  }
}
