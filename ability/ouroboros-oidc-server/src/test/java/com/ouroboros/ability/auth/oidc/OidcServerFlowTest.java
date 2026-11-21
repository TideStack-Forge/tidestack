package com.ouroboros.ability.auth.oidc;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.session.UserSessionManager;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenService;
import io.vavr.control.Either;
import io.vavr.control.Try;

class OidcServerFlowTest {
  private static final String SUBJECT = "65eb15bb-becd-4f68-a69e-068f951c4507";
  private static final String CLIENT = "business-app";
  private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._~";
  private static final String CALLBACK = "https://client.example/callback";
  private final ObjectMapper json = new ObjectMapper();
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;
  private Fixture fixture;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new Fixture();
    fixture.configuration.setClients(List.of(client()));
    var pair = KeyPairGenerator.getInstance("RSA");
    pair.initialize(2048);
    var keyPair = pair.generateKeyPair();
    var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
        .privateKey(keyPair.getPrivate()).keyID("test-key").build();
    when(fixture.secrets.resolve("test:jwks")).thenReturn(Optional.of(new com.nimbusds.jose.jwk.JWKSet(key).toString(false)));
    when(fixture.secrets.resolve("test:client-secret"))
        .thenReturn(Optional.of("{bcrypt}" + new BCryptPasswordEncoder().encode("client-secret")));
    User user = new User() {
      public com.ouroboros.security.claims.Claims getClaims() {
        return com.ouroboros.security.claims.Claims.builder().claim("public_subject_id", SUBJECT)
            .claim("name", "Alice").claim("nickname", "Ally").build();
      }
      public String getUserIdentity() { return "alice"; }
      public String getDisplayName() { return "Alice"; }
      public String getEmail() { return "alice@example.com"; }
    };
    when(fixture.users.publicSubjectsReady()).thenReturn(true);
    when(fixture.users.getUser("alice")).thenReturn(Optional.of(user));
    UserSession session = mock(UserSession.class);
    when(session.getUser()).thenReturn("alice");
    when(session.getExpiresAt()).thenReturn(LocalDateTime.now().plusHours(4));
    when(fixture.sessions.getSession("platform-session")).thenReturn(Optional.of(session));
    when(fixture.sessions.destroySession("platform-session")).thenAnswer(call -> {
      when(fixture.sessions.getSession("platform-session")).thenReturn(Optional.empty());
      return Try.success(true);
    });
    when(fixture.tokens.verifyAccessToken("platform-token"))
        .thenReturn(Either.right(new TokenInfo("alice", "platform-session", Map.of(), System.currentTimeMillis() + 60000)));
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("oidc-test", Map.of(
        "ouroboros.security.oidc.enabled", "false",
        "ouroboros.security.oidc.issuer", "https://platform.example",
        "ouroboros.security.oidc.jwk-set-ref", "test:jwks")));
    context.register(TestConfiguration.class);
    context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("fixture", fixture));
    context.refresh();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void tearDown() { if (context != null) { context.close(); } }

  @Test
  void refusesIssuanceUntilAccountProvidersAreReadyWithoutHidingDiscovery() throws Exception {
    when(fixture.users.publicSubjectsReady()).thenReturn(false);
    mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk());
    mvc.perform(get("/oauth2/authorize")).andExpect(status().isServiceUnavailable());
    mvc.perform(post("/oauth2/token")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void publishesDiscoveryAndPublicKeysWithoutPrivateMaterial() throws Exception {
    mvc.perform(get("/.well-known/openid-configuration"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.issuer").value("https://platform.example"))
        .andExpect(jsonPath("$.authorization_endpoint").value("https://platform.example/oauth2/authorize"))
        .andExpect(jsonPath("$.end_session_endpoint").value("https://platform.example/connect/logout"))
        .andExpect(jsonPath("$.grant_types_supported").value(org.hamcrest.Matchers.contains("authorization_code", "refresh_token")))
        .andExpect(jsonPath("$.token_endpoint_auth_methods_supported").value(org.hamcrest.Matchers.contains("client_secret_basic", "none")))
        .andExpect(jsonPath("$.scopes_supported").value(org.hamcrest.Matchers.hasItems("openid", "profile", "email", "phone", "offline_access")))
        .andExpect(jsonPath("$.device_authorization_endpoint").doesNotExist());
    mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk())
        .andExpect(jsonPath("$.grant_types_supported").value(org.hamcrest.Matchers.contains("authorization_code", "refresh_token")))
        .andExpect(jsonPath("$.introspection_endpoint_auth_methods_supported").value(org.hamcrest.Matchers.contains("client_secret_basic")))
        .andExpect(jsonPath("$.device_authorization_endpoint").doesNotExist());
    mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist()).andExpect(jsonPath("$.keys[0].p").doesNotExist());
  }

  @Test
  void completesCodeFlowWithNonceScopedClaimsUserInfoAndRotatingRefreshToken() throws Exception {
    String code = authorize(login());
    JsonNode tokens = exchange(code, VERIFIER);
    assertTrue(tokens.hasNonNull("id_token"));
    assertTrue(tokens.hasNonNull("refresh_token"));
    var idToken = context.getBean(JwtDecoder.class).decode(tokens.get("id_token").asText());
    assertEquals(SUBJECT, idToken.getSubject());
    assertEquals("nonce-123", idToken.getClaimAsString("nonce"));
    assertEquals(List.of(CLIENT), idToken.getAudience());
    assertEquals("Alice", idToken.getClaimAsString("name"));
    assertEquals("Ally", idToken.getClaimAsString("nickname"));
    assertFalse(idToken.hasClaim("displayName"));
    assertEquals("alice@example.com", idToken.getClaimAsString("email"));
    mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(SUBJECT))
        .andExpect(jsonPath("$.name").value("Alice"))
        .andExpect(jsonPath("$.nickname").value("Ally"))
        .andExpect(jsonPath("$.displayName").doesNotExist())
        .andExpect(jsonPath("$.email").value("alice@example.com"));
    JsonNode refreshed = refresh(tokens.get("refresh_token").asText(), 200);
    assertNotEquals(tokens.get("refresh_token").asText(), refreshed.get("refresh_token").asText());
    refresh(tokens.get("refresh_token").asText(), 400);
  }

  @Test
  void pausesIssuanceDynamicallyAndResumesWithoutRevokingValidExistingTokens() throws Exception {
    MockHttpSession browser = login();
    JsonNode tokens = exchange(authorize(browser), VERIFIER);
    fixture.configuration.setSettings(false);
    mvc.perform(authorizationRequest().session(browser)).andExpect(status().isServiceUnavailable());
    refresh(tokens.get("refresh_token").asText(), 503);
    mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk());
    mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk());
    mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText()))
        .andExpect(status().isOk());
    fixture.configuration.setSettings(true);
    refresh(tokens.get("refresh_token").asText(), 200);
  }

  @Test
  void rejectsWrongPkceReplayAndUnregisteredRedirectUris() throws Exception {
    String code = authorize(login());
    mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "authorization_code").param("code", code)
        .param("redirect_uri", CALLBACK).param("code_verifier", "invalid-verifier"))
        .andExpect(status().isBadRequest());
    String validCode = authorize(login());
    exchange(validCode, VERIFIER);
    mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "authorization_code").param("code", validCode)
        .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
        .andExpect(status().isBadRequest());
    mvc.perform(authorizationRequest().param("redirect_uri", "https://attacker.example/callback"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesLoginBridgeWithoutCsrfOrPendingAuthorization() throws Exception {
    mvc.perform(get("/api/auth/oidc/login-context")).andExpect(status().isBadRequest());
    var first = mvc.perform(authorizationRequest()).andExpect(status().is3xxRedirection()).andReturn();
    var session = (MockHttpSession) first.getRequest().getSession(false);
    mvc.perform(post("/api/auth/oidc/session").session(session).header("Authorization", "Bearer platform-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deniesOldGrantsWhenAClientOrPlatformUserIsDisabled() throws Exception {
    JsonNode tokens = exchange(authorize(login()), VERIFIER);
    var disabled = client();
    disabled.setEnabled(false);
    fixture.configuration.setClients(List.of(disabled));
    refresh(tokens.get("refresh_token").asText(), 401);
    fixture.configuration.setClients(List.of(client()));
    JsonNode next = exchange(authorize(login()), VERIFIER);
    User disabledUser = mock(User.class);
    when(disabledUser.isEnabled()).thenReturn(false);
    when(fixture.users.getUser("alice")).thenReturn(Optional.of(disabledUser));
    refresh(next.get("refresh_token").asText(), 400);
  }

  @Test
  void rejectsLogoutRedirectsOutsideTheRegistrationAndRevokesThePlatformSession() throws Exception {
    MockHttpSession session = login();
    JsonNode tokens = exchange(authorize(session), VERIFIER);
    mvc.perform(get("/connect/logout").session(session).queryParam("id_token_hint", tokens.get("id_token").asText())
        .queryParam("post_logout_redirect_uri", "https://attacker.example/logout"))
        .andExpect(status().isBadRequest());
    // A POST logout carries the CSRF token and the registered RP return URI.
    mvc.perform(post("/connect/logout").session(session)
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .param("id_token_hint", tokens.get("id_token").asText())
        .param("post_logout_redirect_uri", "https://client.example/signed-out").param("state", "logout-state"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://client.example/signed-out?state=logout-state"));
    verify(fixture.sessions).destroySession("platform-session");
    refresh(tokens.get("refresh_token").asText(), 400);
  }

  @Test
  void logsOutTheHintedPlatformSessionWithoutABrowserCookie() throws Exception {
    JsonNode tokens = exchange(authorize(login()), VERIFIER);
    mvc.perform(get("/connect/logout").queryParam("id_token_hint", tokens.get("id_token").asText())
        .queryParam("post_logout_redirect_uri", "https://client.example/signed-out"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://client.example/signed-out"));
    verify(fixture.sessions).destroySession("platform-session");
    refresh(tokens.get("refresh_token").asText(), 400);
  }

  @Test
  void redeemsAnAuthorizationCodeOnlyOnceUnderConcurrentRequests() throws Exception {
    String code = authorize(login());
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Integer> redeem = () -> {
      assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
      return mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
          .param("grant_type", "authorization_code").param("code", code)
          .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
          .andReturn().getResponse().getStatus();
    };
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(redeem);
      var second = executor.submit(redeem);
      start.countDown();
      assertEquals(List.of(200, 400), java.util.stream.Stream.of(
          first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList());
    }
  }

  @Test
  void publicClientsGetOnlyRequestedClaimsAndNoRefreshToken() throws Exception {
    var spec = client();
    spec.setClientType(OidcClientSpec.ClientType.PUBLIC);
    spec.setClientSecretHash(null);
    spec.getOidc().setScopes(List.of("openid", "profile", "email"));
    fixture.configuration.setClients(List.of(spec));
    var session = login("openid");
    String location = mvc.perform(authorizationRequest("openid").session(session))
        .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    JsonNode tokens = read(mvc.perform(post("/oauth2/token").param("client_id", CLIENT)
        .param("grant_type", "authorization_code").param("code", code)
        .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
        .andExpect(status().isOk()).andReturn());
    assertFalse(tokens.has("refresh_token"));
    var claims = context.getBean(JwtDecoder.class).decode(tokens.get("id_token").asText()).getClaims();
    assertFalse(claims.containsKey("email"));
    assertFalse(claims.containsKey("name"));
    assertFalse(claims.containsKey("nickname"));
    mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(SUBJECT))
        .andExpect(jsonPath("$.name").doesNotExist())
        .andExpect(jsonPath("$.nickname").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void rejectsExpiredPlatformSessionsAndRevokedRefreshTokens() throws Exception {
    var session = login();
    JsonNode tokens = exchange(authorize(session), VERIFIER);
    mvc.perform(post("/oauth2/revoke").with(httpBasic(CLIENT, "client-secret"))
        .param("token", tokens.get("refresh_token").asText()).param("token_type_hint", "refresh_token"))
        .andExpect(status().isOk());
    refresh(tokens.get("refresh_token").asText(), 400);
    when(fixture.sessions.getSession("platform-session")).thenReturn(Optional.empty());
    mvc.perform(authorizationRequest().session(session)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/login/oidc?loginRequestId=*"));
    mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void introspectionRequiresExplicitResourcePermissionAndRejectsIdentityAndRefreshTokens() throws Exception {
    JsonNode tokens = exchange(authorize(login()), VERIFIER);
    mvc.perform(post("/oauth2/introspect").with(httpBasic(CLIENT, "client-secret"))
        .param("token", tokens.get("access_token").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.sub").doesNotExist());
    var allowed = client();
    allowed.setIntrospectionResources(List.of(CLIENT));
    fixture.configuration.setClients(List.of(allowed));
    mvc.perform(post("/oauth2/introspect").with(httpBasic(CLIENT, "client-secret"))
        .param("token", tokens.get("access_token").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.sub").value(SUBJECT))
        .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.username").doesNotExist());
    for (String name : List.of("id_token", "refresh_token")) {
      mvc.perform(post("/oauth2/introspect").with(httpBasic(CLIENT, "client-secret"))
          .param("token", tokens.get(name).asText()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
    allowed.setIntrospectionResources(List.of("other-resource"));
    fixture.configuration.setClients(List.of(allowed));
    mvc.perform(post("/oauth2/introspect").with(httpBasic(CLIENT, "client-secret"))
        .param("token", tokens.get("access_token").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void omittedOfflineChoiceDoesNotIssueRefreshAndConsentCannotBeReplayed() throws Exception {
    var browser = login();
    var view = consent(browser, "openid profile email offline_access");
    var submit = decision(browser, view).param("identity_approved", "true");
    var location = mvc.perform(submit).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    var code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    var tokens = exchange(code, VERIFIER);
    assertFalse(tokens.has("refresh_token"));
    assertFalse(tokens.get("scope").asText().contains("offline_access"));
    mvc.perform(submit).andExpect(status().isSeeOther()).andExpect(redirectedUrl("/login/oidc/consent?result=processed"));
  }

  @Test
  void consentRejectsForgedFieldsCrossBrowserAccessAndMissingCsrf() throws Exception {
    var browser = login();
    var view = consent(browser, "openid profile email offline_access");
    mvc.perform(get("/api/auth/oidc/consent-context").session(login())
        .param("contextId", view.get("contextId").asText())).andExpect(status().isForbidden());
    mvc.perform(post("/oauth2/authorize").session(browser)
        .param("context_id", view.get("contextId").asText()).param("view_revision", view.get("viewRevision").asText())
        .param("decision", "continue").param("identity_approved", "true")).andExpect(status().isForbidden());
    mvc.perform(decision(browser, view).param("identity_approved", "true").param("scope", "openid"))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/login/oidc/consent?result=invalid"));
    mvc.perform(decision(browser, view).param("identity_approved", "true", "false"))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/login/oidc/consent?result=invalid"));
    mvc.perform(decision(browser, view).param("identity_approved", "false"))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern(CALLBACK + "?error=access_denied*"));
  }

  @Test
  void pendingAuthorizationSurvivesCacheSerializationWithoutPlainTokenIndexes() throws Exception {
    var browser = login();
    var view = consent(browser, "openid profile email offline_access");
    var grant = context.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class)
        .findById(view.get("contextId").asText());
    byte[] serialized;
    try (var bytes = new java.io.ByteArrayOutputStream(); var output = new java.io.ObjectOutputStream(bytes)) {
      output.writeObject(grant);
      serialized = bytes.toByteArray();
    }
    try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(serialized))) {
      var restored = (org.springframework.security.oauth2.server.authorization.OAuth2Authorization) input.readObject();
      assertEquals(grant.getId(), restored.getId());
      assertEquals(grant.<OidcAuthorizationPolicy.Draft>getAttribute(OidcAuthorizationPolicy.GRANT),
          restored.<OidcAuthorizationPolicy.Draft>getAttribute(OidcAuthorizationPolicy.GRANT));
    }
  }

  @Test
  void refreshScopeReductionPersistsAndCannotBeExpandedOnLaterRefresh() throws Exception {
    var initial = exchange(authorize(login()), VERIFIER);
    var reduced = read(mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "refresh_token").param("refresh_token", initial.get("refresh_token").asText())
        .param("scope", "openid profile offline_access")).andExpect(status().isOk()).andReturn());
    assertFalse(reduced.get("scope").asText().contains("email"));
    assertFalse(context.getBean(JwtDecoder.class).decode(reduced.get("id_token").asText()).hasClaim("email"));
    var next = refresh(reduced.get("refresh_token").asText(), 200);
    assertFalse(next.get("scope").asText().contains("email"));
    mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "refresh_token").param("refresh_token", next.get("refresh_token").asText())
        .param("scope", "openid email")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_scope"));
  }

  @Test
  void businessOnlyOAuthUsesOriginalRolesAndFrozenGrantAndNeverTreatsIdTokenAsApiCredential() throws Exception {
    var authority = new com.ouroboros.security.authority.BaseAuthority();
    authority.setFullName("orders.search"); authority.setTitle("查询订单"); authority.setDelegable(true);
    var dataDefinition = mock(com.ouroboros.security.authority.accesscontrol.AccessControlDefine.class);
    when(dataDefinition.getCommonFields()).thenReturn(List.of(
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("id", "编号", "String", ""),
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("name", "名称", "String", "")));
    when(dataDefinition.getControlledFields()).thenReturn(List.of());
    when(dataDefinition.getFilterDefine()).thenReturn(List.of());
    authority.setAccessControlDefine(dataDefinition);
    try (var definitions = mockStatic(com.ouroboros.security.authority.AuthorityCenter.class)) {
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority("orders.search"))
          .thenReturn(Optional.of(authority));
      var role = mock(com.ouroboros.security.rbac.Role.class);
      when(role.getAuthorities()).thenAnswer(call -> List.of(new com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority(
          authority, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all())));
      User original = fixture.users.getUser("alice").orElseThrow();
      User withRoles = new User() {
        public String getUserIdentity() { return "alice"; }
        public String getDisplayName() { return "Alice"; }
        public com.ouroboros.security.claims.Claims getClaims() { return original.getClaims(); }
        public List<com.ouroboros.security.rbac.Role> getRoles() { return List.of(role); }
      };
      when(fixture.users.getUser("alice")).thenReturn(Optional.of(withRoles));
      var spec = client(); spec.getApiAuthorization().setEnabled(true);
      spec.getApiAuthorization().getAllowedBoundary().setPermissions(List.of("orders.search"));
      spec.setIntrospectionResources(List.of("platform-api"));
      spec.getOidc().setEnabled(false);
      spec.getConsentPolicy().setMode(OidcClientSpec.ConsentMode.EXPLICIT);
      fixture.configuration.setClients(List.of(spec));
      String scope = ConfigurationRegisteredClientRepository.businessScope("orders.search");
      var browser = login(scope);
      var view = consent(browser, scope);
      assertFalse(view.has("identity")); assertTrue(view.has("business"));
      var redirect = mvc.perform(decision(browser, view).param("business_approved", "true"))
          .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
      var tokens = exchange(UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("code"), VERIFIER);
      assertFalse(tokens.has("id_token")); assertTrue(tokens.has("refresh_token"));
      var jwt = context.getBean(JwtDecoder.class).decode(tokens.get("access_token").asText());
      assertEquals(List.of("platform-api"), jwt.getAudience());
      var resolver = context.getBean(OidcAccessTokenIdentityResolver.class);
      var identity = resolver.resolve(tokens.get("access_token").asText()).orElseThrow();
      assertTrue(com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
          .evaluate(identity, "platform-api", "orders.search") instanceof com.ouroboros.security.authorize.AuthorizationDecision.Permit);
      assertEquals(com.ouroboros.security.authority.accesscontrol.FieldConstraint.Mode.ONLY,
          identity.getAuthorizationBoundary().permissions().get("orders.search").getFieldConstraint().mode());
      authority.setApis(List.of("GET /api/changed-target"));
      assertTrue(resolver.resolve(tokens.get("access_token").asText()).isEmpty());
      refresh(tokens.get("refresh_token").asText(), 400);
      authority.setApis(List.of());
      var refreshed = refresh(tokens.get("refresh_token").asText(), 200);
      when(fixture.users.getUser("alice")).thenReturn(Optional.of(original));
      var revokedRole = resolver.resolve(refreshed.get("access_token").asText()).orElseThrow();
      assertTrue(com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
          .evaluate(revokedRole, "platform-api", "orders.search") instanceof com.ouroboros.security.authorize.AuthorizationDecision.Deny);
      mvc.perform(post("/oauth2/introspect").with(httpBasic(CLIENT, "client-secret"))
          .param("token", refreshed.get("access_token").asText())).andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(false));
      refresh(refreshed.get("refresh_token").asText(), 400);
    }
    fixture.configuration.setClients(List.of(client()));
    var identityTokens = exchange(authorize(login()), VERIFIER);
    var resolver = context.getBean(OidcAccessTokenIdentityResolver.class);
    assertTrue(resolver.resolve(identityTokens.get("id_token").asText()).isEmpty());
    assertTrue(resolver.resolve(identityTokens.get("access_token").asText()).isEmpty());
  }

  @Test
  void registrationRemainsClosedWithoutPersistentPolicyAndNeverRedirectsToLogin() throws Exception {
    mvc.perform(post("/connect/register").contentType("application/json").content("{}"))
        .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Location"));
    mvc.perform(get("/connect/register").param("client_id", CLIENT)).andExpect(status().isMethodNotAllowed());
    mvc.perform(put("/connect/register")).andExpect(status().isMethodNotAllowed());
    mvc.perform(delete("/connect/register")).andExpect(status().isMethodNotAllowed());
    mvc.perform(get("/.well-known/openid-configuration")).andExpect(jsonPath("$.registration_endpoint").doesNotExist());
    mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(jsonPath("$.registration_endpoint").doesNotExist());
  }

  @Test
  void openRegistrationUsesSasAndPolicyWithoutJwtPreemptionOrPrivilegedConfigurationProjection() throws Exception {
    enableRegistration("OPEN");
    var result = mvc.perform(post("/connect/register").header("Authorization", "Bearer invalid.jwt")
        .contentType("application/json").content("{\"redirect_uris\":[\"https://app.example/callback\"],\"token_endpoint_auth_method\":\"none\",\"scope\":\"openid profile\"}"))
        .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.client_secret").doesNotExist()).andExpect(jsonPath("$.clientSecretHash").doesNotExist())
        .andExpect(jsonPath("$.application_type").value("web"))
        .andExpect(jsonPath("$.subject_type").value("public"))
        .andExpect(jsonPath("$.id_token_signed_response_alg").value("RS256"))
        .andExpect(jsonPath("$.registration_access_token").doesNotExist()).andExpect(jsonPath("$.registration_client_uri").doesNotExist())
        .andExpect(jsonPath("$.grant_types").value(org.hamcrest.Matchers.contains("authorization_code"))).andReturn();
    String id = read(result).get("client_id").asText();
    var registered = context.getBean(ConfigurationRegisteredClientRepository.class).findByClientId(id);
    assertEquals(java.util.Set.of("openid", "profile"), registered.getScopes());
    assertEquals(java.util.Set.of(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE), registered.getAuthorizationGrantTypes());
    mvc.perform(get("/.well-known/openid-configuration")).andExpect(jsonPath("$.registration_endpoint").value("https://platform.example/connect/register"));
    mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(jsonPath("$.registration_endpoint").value("https://platform.example/connect/register"));
  }

  @Test
  void opaqueInitialCredentialIsConsumedOnceByRegistrationAndCannotBecomeAUserCredential() throws Exception {
    enableRegistration("INITIAL_ACCESS_TOKEN");
    mvc.perform(post("/connect/register").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    var issued = context.getBean(com.ouroboros.ability.auth.oidc.registration.DcrCredentials.class).issue("operator", java.time.Duration.ofMinutes(5));
    String body = "{\"redirect_uris\":[\"http://127.0.0.1:43111/callback\"]}";
    mvc.perform(post("/connect/register").header("Authorization", "Bearer " + issued.token()).contentType("application/json").content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.client_secret").isString())
        .andExpect(jsonPath("$.token_endpoint_auth_method").value("client_secret_basic"));
    mvc.perform(post("/connect/register").header("Authorization", "Bearer " + issued.token()).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/userinfo").header("Authorization", "Bearer " + issued.token())).andExpect(status().isUnauthorized());
  }

  @Test
  void registrationCountsFailedRequestsAndRejectsUnsupportedMetadataBeforeWriting() throws Exception {
    enableRegistration("OPEN");
    mvc.perform(post("/connect/register").contentType("application/json").content("{\"redirect_uris\":[\"https://app.example/callback#bad\"]}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_redirect_uri"));
    for (int i = 0; i < 4; i++) {
      mvc.perform(post("/connect/register").contentType("application/json").content("{}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post("/connect/register").contentType("application/json").content("{}"))
        .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    verifyNoInteractions(context.getBean(com.ouroboros.configuration.management.ConfigurationManagementService.class));
  }

  private void enableRegistration(String mode) {
    var values = new java.util.LinkedHashMap<>(com.ouroboros.ability.auth.oidc.registration.DcrSettings.defaults().toValue());
    values.put("enabled", true); values.put("mode", mode);
    var policy = new com.ouroboros.configuration.ConfigurationSnapshot(com.ouroboros.ability.auth.oidc.registration.DcrSettings.KEY,
        values, com.ouroboros.util.Json.toJsonString(values), "dcr-1", null);
    when(fixture.configuration.service.getSnapshot(policy.key())).thenReturn(Optional.of(policy));
    var management = context.getBean(com.ouroboros.configuration.management.ConfigurationManagementService.class);
    when(management.mutateManagementConfigurations(anyMap(), anyMap())).thenAnswer(call -> {
      Map<String, com.ouroboros.configuration.management.ConfigurationMutation> commands = call.getArgument(0);
      String key = ConfigurationRegisteredClientRepository.KEY;
      var result = context.getBean(com.ouroboros.ability.auth.oidc.management.OidcConfigurationManagementPolicy.class).prepareWrite(
          new com.ouroboros.configuration.management.ConfigurationManagementContext(null, com.ouroboros.configuration.management.ConfigurationManagementContext.Purpose.MANAGEMENT),
          fixture.configuration.service.getSnapshot(key).orElseThrow(), commands.get(key), Map.of(policy.key(), policy));
      fixture.configuration.setClients(ConfigurationRegisteredClientRepository.parseAndValidate(result.value()));
      return Map.of();
    });
  }

  private JsonNode consent(MockHttpSession browser, String scopes) throws Exception {
    var location = mvc.perform(authorizationRequest(scopes).session(browser)).andExpect(status().is3xxRedirection())
        .andReturn().getResponse().getRedirectedUrl();
    assertTrue(location.contains("/login/oidc/consent"), location);
    var state = java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(location).build()
        .getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
    return read(mvc.perform(get("/api/auth/oidc/consent-context").session(browser).param("state", state))
        .andExpect(status().isOk()).andReturn()).get("data");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decision(MockHttpSession browser, JsonNode view) {
    return post("/oauth2/authorize").session(browser).param("context_id", view.get("contextId").asText())
        .param("view_revision", view.get("viewRevision").asText()).param("decision", "continue")
        .param(view.get("csrf").get("parameterName").asText(), view.get("csrf").get("token").asText());
  }

  private MockHttpSession login() throws Exception { return login("openid profile email offline_access"); }

  private MockHttpSession login(String scopes) throws Exception {
    var first = mvc.perform(authorizationRequest(scopes)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/login/oidc?loginRequestId=*")).andReturn();
    MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);
    JsonNode csrf = read(mvc.perform(get("/api/auth/oidc/login-context").session(session))
        .andExpect(status().isOk()).andReturn()).get("data");
    mvc.perform(post("/api/auth/oidc/session").session(session).header("Authorization", "Bearer platform-token")
        .header(csrf.get("csrfHeader").asText(), csrf.get("csrfToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.redirectUrl").exists());
    return session;
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizationRequest() throws Exception {
    return authorizationRequest("openid profile email offline_access");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizationRequest(String scopes) throws Exception {
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
    return get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", CLIENT)
        .queryParam("redirect_uri", CALLBACK).queryParam("scope", scopes)
        .queryParam("nonce", "nonce-123").queryParam("state", "state-123")
        .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256");
  }

  private String authorize(MockHttpSession session) throws Exception {
    String location = mvc.perform(authorizationRequest().session(session)).andExpect(status().is3xxRedirection())
        .andReturn().getResponse().getRedirectedUrl();
    if (location.contains("/login/oidc/consent")) {
      String state = java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
      var view = read(mvc.perform(get("/api/auth/oidc/consent-context").session(session).param("state", state))
          .andExpect(status().isOk()).andReturn()).get("data");
      var submit = post("/oauth2/authorize").session(session)
          .param("context_id", view.get("contextId").asText()).param("view_revision", view.get("viewRevision").asText())
          .param("decision", "continue").param("identity_approved", "true")
          .param(view.get("csrf").get("parameterName").asText(), view.get("csrf").get("token").asText());
      if (view.has("offlineAccess")) { submit.param("offline_approved", "true"); }
      if (view.has("business")) { submit.param("business_approved", "true"); }
      location = mvc.perform(submit).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    }
    String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    assertNotNull(code, location);
    return code;
  }

  private JsonNode exchange(String code, String verifier) throws Exception {
    return read(mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "authorization_code").param("code", code)
        .param("redirect_uri", CALLBACK).param("code_verifier", verifier))
        .andExpect(status().isOk()).andReturn());
  }

  private JsonNode refresh(String token, int status) throws Exception {
    return read(mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT, "client-secret"))
        .param("grant_type", "refresh_token").param("refresh_token", token))
        .andExpect(status().is(status)).andReturn());
  }

  private JsonNode read(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private OidcClientSpec client() {
    var spec = new OidcClientSpec();
    spec.setClientId(CLIENT);
    spec.setClientType(OidcClientSpec.ClientType.CONFIDENTIAL);
    spec.setClientSecretHash("{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("client-secret"));
    spec.setRedirectUris(List.of(CALLBACK));
    spec.getOidc().setPostLogoutRedirectUris(List.of("https://client.example/signed-out"));
    spec.getOidc().setScopes(List.of("openid", "profile", "email", "offline_access"));
    spec.setRegistrationId(java.util.UUID.nameUUIDFromBytes(spec.getClientId().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    spec.getConsentPolicy().setMode(OidcClientSpec.ConsentMode.ADMIN_APPROVED);
    return spec;
  }

  static class Fixture {
    final OidcClientConfigurationFixture configuration = new OidcClientConfigurationFixture();
    final CompositeSecretResolver secrets = mock(CompositeSecretResolver.class);
    final UserCenter users = mock(UserCenter.class);
    final UserSessionManager<UserSession> sessions = mock();
    final TokenService tokens = mock(TokenService.class);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebSecurity
  @EnableWebMvc
  @Import(OidcAutoConfiguration.class)
  static class TestConfiguration {
    @Bean com.ouroboros.configuration.management.ConfigurationManagementService management() { return mock(com.ouroboros.configuration.management.ConfigurationManagementService.class); }
    @Bean ConfigurationService configuration(Fixture fixture) { return fixture.configuration.service; }
    @Bean CompositeSecretResolver secrets(Fixture fixture) { return fixture.secrets; }
    @Bean UserCenter users(Fixture fixture) { return fixture.users; }
    @Bean UserSessionManager<UserSession> sessions(Fixture fixture) { return fixture.sessions; }
    @Bean TokenService tokens(Fixture fixture) { return fixture.tokens; }
    @Bean CacheManager cache() { return new MemoryCacheManager(); }
  }
}
