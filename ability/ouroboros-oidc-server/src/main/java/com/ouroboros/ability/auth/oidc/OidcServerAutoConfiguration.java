package com.ouroboros.ability.auth.oidc;

import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.ability.auth.oidc.service.CacheOAuth2AuthorizationService;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.session.UserSessionManager;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Conditional(OidcDeploymentCondition.class)
@EnableConfigurationProperties(OidcServerProperties.class)
public class OidcServerAutoConfiguration {
  @Bean
  public ConfigurationRegisteredClientRepository oidcRegisteredClientRepository(ConfigurationService configuration,
      CompositeSecretResolver secrets, OidcServerProperties properties) {
    return new ConfigurationRegisteredClientRepository(configuration);
  }

  @Bean
  public OAuth2AuthorizationService oidcAuthorizationService(CacheManager cache, UserSessionManager<?> sessions,
      UserCenter users, ConfigurationRegisteredClientRepository clients) {
    return new CacheOAuth2AuthorizationService(cache, sessions, users, clients);
  }

  @Bean
  public OidcAuthorizationPolicy oidcAuthorizationPolicy(ConfigurationRegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations, UserCenter users, ConfigurationService configuration) {
    return new OidcAuthorizationPolicy(clients, authorizations, users, configuration);
  }

  @Bean
  public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService oidcConsentService() {
    // Consent belongs to this immutable authorization, never to a reusable client/user approval.
    return new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService() {
      public void save(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent consent) { }
      public void remove(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent consent) { }
      public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent findById(String client, String user) { return null; }
    };
  }

  @Bean
  public org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<?> oidcTokenGenerator(
      JWKSource<SecurityContext> keys, OAuth2TokenCustomizer<JwtEncodingContext> customizer) {
    var jwt = new org.springframework.security.oauth2.server.authorization.token.JwtGenerator(new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(keys));
    jwt.setJwtCustomizer(customizer);
    var refresh = new org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator();
    org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<org.springframework.security.oauth2.core.OAuth2RefreshToken> gated = context -> {
      var draft = context.getAuthorization() == null ? null : context.getAuthorization()
          .<OidcAuthorizationPolicy.Draft>getAttribute(OidcAuthorizationPolicy.GRANT);
      return draft != null && draft.refreshAllowed() ? refresh.generate(context) : null;
    };
    return new org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator(jwt,
        new org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator(), gated);
  }

  @Bean
  public OidcAccessTokenIdentityResolver oidcAccessTokenIdentityResolver(JwtDecoder decoder,
      AuthorizationServerSettings settings, OAuth2AuthorizationService authorizations,
      ConfigurationRegisteredClientRepository clients, UserCenter users) {
    return new OidcAccessTokenIdentityResolver(decoder, settings.getIssuer(), authorizations, clients, users);
  }

  @Bean
  public OidcTokenIntrospectionProvider oidcTokenIntrospectionProvider(ConfigurationRegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations, OidcAccessTokenIdentityResolver identities) {
    return new OidcTokenIntrospectionProvider(clients, authorizations, identities);
  }

  @Bean
  public OidcTokenExchangeProvider oidcTokenExchangeProvider(ConfigurationRegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations, UserCenter users) {
    return new OidcTokenExchangeProvider(clients, authorizations, users);
  }

  @Bean
  public AuthorizationServerSettings oidcAuthorizationServerSettings(OidcServerProperties properties) {
    try {
      URI issuer = URI.create(properties.getIssuer());
      boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").contains(issuer.getHost());
      if (issuer.getHost() == null || issuer.getQuery() != null || issuer.getFragment() != null
          || issuer.getUserInfo() != null || !("https".equals(issuer.getScheme())
          || (loopback && "http".equals(issuer.getScheme())))) {
        throw new IllegalArgumentException("Issuer must use HTTPS");
      }
      return AuthorizationServerSettings.builder().issuer(properties.getIssuer()).build();
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new InternalException("OIDC issuer 必须配置为公开的 HTTPS 地址（本机允许 HTTP）", ex);
    }
  }

  @Bean
  public JWKSet oidcSigningKeys(OidcServerProperties properties, CompositeSecretResolver secrets) {
    String json = secrets.resolve(properties.getJwkSetRef())
        .orElseThrow(() -> new InternalException("OIDC 签名密钥引用不可用"));
    try {
      JWKSet keys = JWKSet.parse(json);
      var ids = new HashSet<String>();
      if (keys.getKeys().isEmpty()) { throw new IllegalArgumentException("Empty JWK set"); }
      for (var key : keys.getKeys()) {
        if (!(key instanceof RSAKey rsa) || rsa.size() < 2048 || key.getKeyID() == null
            || key.getKeyID().isBlank() || !ids.add(key.getKeyID())
            || (key.getKeyUse() != null && !com.nimbusds.jose.jwk.KeyUse.SIGNATURE.equals(key.getKeyUse()))
            || (key.getAlgorithm() != null && !com.nimbusds.jose.JWSAlgorithm.RS256.equals(key.getAlgorithm()))) {
          throw new IllegalArgumentException("RSA signing keys require unique kid, RS256, and at least 2048 bits");
        }
      }
      if (!keys.getKeys().getFirst().isPrivate()) {
        throw new IllegalArgumentException("First JWK must contain the active signing private key");
      }
      return keys;
    } catch (java.text.ParseException | IllegalArgumentException ex) {
      throw new InternalException("OIDC 签名密钥格式无效", ex);
    }
  }

  @Bean
  public JWKSource<SecurityContext> oidcJwkSource(JWKSet keys) {
    return (selector, context) -> selector.select(keys);
  }

  @Bean
  public JwtDecoder oidcJwtDecoder(JWKSource<SecurityContext> keys) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
  }

  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> oidcTokenCustomizer(UserCenter users, JWKSet keys) {
    return context -> {
      context.getJwsHeader().algorithm(SignatureAlgorithm.RS256).keyId(keys.getKeys().getFirst().getKeyID());
      User user = users.getUser(context.getPrincipal().getName()).filter(User::isEnabled)
          .orElseThrow(() -> new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant"));
      context.getClaims().subject(OidcIdentityClaims.subject(user));
      if ("access_token".equals(context.getTokenType().getValue())) {
        context.getClaims().claim("token_use", "access_token");
        var draft = context.getAuthorization().<OidcAuthorizationPolicy.Draft>getAttribute(OidcAuthorizationPolicy.GRANT);
        if (draft != null && context.getAuthorizedScopes().stream().anyMatch(scope -> scope.startsWith("authority:"))) {
          context.getClaims().audience(java.util.List.of(com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API));
        }
      }
      if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
        OidcIdentityClaims.project(user, context.getAuthorizedScopes()).forEach((name, value) -> context.getClaims().claim(name, value));
      }
    };
  }

  @Bean
  public com.ouroboros.ability.auth.oidc.registration.DcrRegistrationEndpoint dcrRegistrationEndpoint(ConfigurationService configuration,
      com.ouroboros.configuration.management.ConfigurationManagementService management, CacheManager cache,
      com.ouroboros.ability.auth.oidc.registration.DcrCredentials credentials, AuthorizationServerSettings settings) {
    return new com.ouroboros.ability.auth.oidc.registration.DcrRegistrationEndpoint(configuration, management, credentials,
        new com.ouroboros.ability.auth.oidc.registration.DcrRateLimiter(cache), settings::getIssuer);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 10)
  public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, UserSessionManager<?> sessions,
      UserCenter users, OAuth2AuthorizationService authorizations, ConfigurationService configuration, OidcAuthorizationPolicy policy, OidcTokenIntrospectionProvider introspection,
      OidcTokenExchangeProvider exchanges, ConfigurationRegisteredClientRepository clients,
      com.ouroboros.ability.auth.oidc.registration.DcrRegistrationEndpoint registration) throws Exception {
    var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
    var logout = new OidcLogoutAuthenticationSuccessHandler();
    logout.setLogoutHandler((request, response, authentication) -> {
      var session = request.getSession(false);
      String sessionId = session == null ? null
          : (String) session.getAttribute(OidcPlatformSessionFilter.PLATFORM_SESSION);
      if (authentication instanceof org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken oidc) {
        var grant = authorizations.findByToken(oidc.getIdTokenHint(),
            new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("id_token"));
        org.springframework.security.core.Authentication principal = grant == null ? null
            : grant.getAttribute(java.security.Principal.class.getName());
        if (principal != null && principal.getDetails() instanceof String id) { sessionId = id; }
      }
      if (sessionId != null) {
        sessions.destroySession(sessionId).getOrElseThrow(error -> new InternalException("撤销 OIDC 平台会话失败", error));
      }
      new SecurityContextLogoutHandler().logout(request, response, authentication);
    });
    var loginEndpoints = new AntPathRequestMatcher("/api/auth/oidc/**");
    var registrationPath = new AntPathRequestMatcher(com.ouroboros.ability.auth.oidc.registration.DcrRegistrationEndpoint.PATH);
    var registrationPost = new AntPathRequestMatcher(com.ouroboros.ability.auth.oidc.registration.DcrRegistrationEndpoint.PATH, "POST");
    var bearerTokens = new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver();
    var requestCache = new OidcRequestCache();
    http.csrf(csrf -> csrf.addObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.web.csrf.CsrfFilter>() {
      public <O extends org.springframework.security.web.csrf.CsrfFilter> O postProcess(O filter) {
        var defaults = org.springframework.security.web.csrf.CsrfFilter.DEFAULT_CSRF_MATCHER;
        filter.setRequireCsrfProtectionMatcher(request -> defaults.matches(request)
            && (request.getRequestURI().substring(request.getContextPath().length()).equals("/oauth2/authorize") || request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/auth/oidc/")
                || request.getRequestURI().substring(request.getContextPath().length()).equals("/connect/logout")));
        return filter;
      }
    }));
    http.securityMatcher(new OrRequestMatcher(server.getEndpointsMatcher(), loginEndpoints, registrationPath))
        .with(server, authorizationServer -> authorizationServer
            .authorizationServerMetadataEndpoint(endpoint -> endpoint.authorizationServerMetadataCustomizer(
                metadata -> metadata.claims(claims -> { supportedProtocolClaims(claims); registration.customizeDiscovery(claims); })))
            .tokenEndpoint(endpoint -> endpoint.authenticationProviders(exchanges::configureProviders))
            .tokenIntrospectionEndpoint(endpoint -> endpoint.authenticationProviders(introspection::configureProviders))
            .authorizationEndpoint(endpoint -> endpoint.consentPage("/login/oidc/consent")
                .authorizationRequestConverters(converters -> { converters.clear(); converters.add(policy); })
                .authenticationProviders(policy::configureProviders)
                .authorizationResponseHandler((request, response, authentication) -> {
                  var success = (org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
                  var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(success.getRedirectUri())
                      .queryParam("code", success.getAuthorizationCode().getTokenValue())
                      .queryParam("scope", String.join(" ", success.getScopes()));
                  if (success.getState() != null) { uri.queryParam("state", success.getState()); }
                  response.sendRedirect(uri.build().encode().toUriString());
                })
                .errorResponseHandler((request, response, error) -> {
                  response.setHeader("Cache-Control", "no-store");
                  response.setHeader("Referrer-Policy", "no-referrer");
                  if (error instanceof OidcAuthorizationPolicy.LocalFailure local) {
                    var uri = org.springframework.web.util.UriComponentsBuilder.fromPath("/login/oidc/consent")
                        .queryParam("result", local.result);
                    if (local.contextId != null) { uri.queryParam("contextId", local.contextId); }
                    response.setStatus(303); response.setHeader("Location", uri.build().encode().toUriString());
                  } else if (error instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException protocol
                      && protocol.getAuthorizationCodeRequestAuthentication() != null
                      && trustedRedirect(clients, protocol.getAuthorizationCodeRequestAuthentication())) {
                    var failure = protocol.getAuthorizationCodeRequestAuthentication();
                    var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(failure.getRedirectUri())
                        .queryParam("error", protocol.getError().getErrorCode());
                    if (failure.getState() != null) { uri.queryParam("state", failure.getState()); }
                    response.sendRedirect(uri.build().encode().toUriString());
                  } else if (request.getMethod().equals("POST")) {
                    response.setStatus(303); response.setHeader("Location", "/login/oidc/consent?result=invalid");
                  } else { response.sendError(400, "授权请求无效，请回到接入应用重新发起"); }
                }))
            .oidc(oidc -> oidc.providerConfigurationEndpoint(endpoint -> endpoint.providerConfigurationCustomizer(
                    metadata -> metadata.claims(claims -> {
                      supportedProtocolClaims(claims);
                      registration.customizeDiscovery(claims);
                      claims.put("scopes_supported", ConfigurationRegisteredClientRepository.IDENTITY_SCOPES.stream().sorted().toList());
                    })))
                .clientRegistrationEndpoint(endpoint -> endpoint
                    .clientRegistrationRequestConverters(converters -> { converters.clear(); converters.add(registration); })
                    .authenticationProviders(providers -> { providers.clear(); providers.add(registration); })
                    .clientRegistrationResponseHandler(registration::success).errorResponseHandler(registration::failure))
                .logoutEndpoint(endpoint -> endpoint.logoutResponseHandler(logout))
                .userInfoEndpoint(endpoint -> endpoint.userInfoMapper(info -> {
                  var grant = info.getAuthorization();
                  var user = users.getUser(grant.getPrincipalName()).filter(User::isEnabled)
                      .orElseThrow(() -> new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token"));
                  var current = clients.findById(grant.getRegisteredClientId());
                  var scopes = new java.util.HashSet<>(grant.getAccessToken().getToken().getScopes());
                  if (current == null || !current.getScopes().contains("openid")) {
                    throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");
                  }
                  scopes.retainAll(current.getScopes());
                  return new org.springframework.security.oauth2.core.oidc.OidcUserInfo(OidcIdentityClaims.project(user, scopes));
                }))))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(loginEndpoints, registrationPost).permitAll().anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) -> {
          Object id = request.getAttribute(OidcRequestCache.REQUEST_ID);
          response.sendRedirect("/login/oidc" + (id == null ? "" : "?loginRequestId=" + id));
        }))
        .requestCache(cache -> cache.requestCache(requestCache))
        .oauth2ResourceServer(resourceServer -> resourceServer
            .bearerTokenResolver(request -> (loginEndpoints.matches(request) || registrationPath.matches(request)) ? null : bearerTokens.resolve(request))
            .jwt(Customizer.withDefaults()))
        .addFilterAfter(new OidcPlatformSessionFilter(sessions, users), SecurityContextHolderFilter.class)
        .addFilterBefore(new com.ouroboros.ability.auth.oidc.registration.DcrRegistrationGuardFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(new OidcTokenExchangeLockFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(new OidcIssuanceGateFilter(configuration, users), OidcTokenExchangeLockFilter.class);
    return http.build();
  }

  private static void supportedProtocolClaims(Map<String, Object> claims) {
    claims.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
    claims.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "none"));
    claims.put("introspection_endpoint_auth_methods_supported", List.of("client_secret_basic"));
    claims.put("revocation_endpoint_auth_methods_supported", List.of("client_secret_basic"));
    claims.remove("device_authorization_endpoint");
    claims.remove("tls_client_certificate_bound_access_tokens");
    claims.remove("dpop_signing_alg_values_supported");
  }

  private boolean trustedRedirect(ConfigurationRegisteredClientRepository clients,
      org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken request) {
    if (request.getRedirectUri() == null || request.getClientId() == null) { return false; }
    var registered = clients.findByClientId(request.getClientId());
    return registered != null && OidcRedirectUris.matches(registered.getRedirectUris(), request.getRedirectUri());
  }
}
