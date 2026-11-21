package com.ouroboros.security.externalauth.oauth2;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.vavr.control.Either;

import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExchangeParams;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;

public class OAuth2Provider implements RedirectAuthProvider {

  private static final Logger logger = LoggerFactory.getLogger(OAuth2Provider.class);

  private final OAuth2ProviderSpec spec;
  private final String clientId;
  private final String clientSecret;
  private final OAuth2HttpClient httpClient;
  private final JwksKeyResolver jwksKeyResolver;
  private final String publicBaseUrl;

  public OAuth2Provider(OAuth2ProviderSpec spec,
                        String clientSecret,
                        OAuth2HttpClient httpClient,
                        JwksKeyResolver jwksKeyResolver,
                        String publicBaseUrl) {
    this(spec, clientSecret, httpClient, jwksKeyResolver, publicBaseUrl, spec.getClientId());
  }

  public OAuth2Provider(OAuth2ProviderSpec spec,
                        String clientSecret,
                        OAuth2HttpClient httpClient,
                        JwksKeyResolver jwksKeyResolver,
                        String publicBaseUrl,
                        String resolvedClientId) {
    this.spec = spec;
    this.clientId = resolvedClientId;
    this.clientSecret = clientSecret;
    this.httpClient = httpClient;
    this.jwksKeyResolver = jwksKeyResolver;
    this.publicBaseUrl = publicBaseUrl;
  }

  @Override
  public String getProviderKey() {
    return spec.getKey();
  }

  @Override
  public ProviderSpec getSpec() {
    return spec;
  }

  @Override
  public AuthorizeUrlResult buildAuthorizeUrl(String returnUrl) {
    String state = com.ouroboros.security.externalauth.SecurityTokens.generateSecureToken();
    String redirectUri = buildRedirectUri();

    Map<String, Object> cacheEntry = new HashMap<String, Object>();
    cacheEntry.put("returnUrl", returnUrl);
    cacheEntry.put("redirectUri", redirectUri);
    if (spec.isConnectionScoped()) {
      cacheEntry.put("connectionId", spec.getKey());
    }

    StringBuilder url = new StringBuilder(spec.getAuthorizationUri());
    appendParam(url, "response_type", "code");
    appendParam(url, "client_id", clientId);
    appendParam(url, "redirect_uri", redirectUri);
    appendParam(url, "state", state);

    List<String> scopes = spec.getScopes();
    if (scopes != null && !scopes.isEmpty()) {
      appendParam(url, "scope", String.join(" ", scopes));
    }

    if (requiresNonce(scopes)) {
      String nonce = com.ouroboros.security.externalauth.SecurityTokens.generateSecureToken();
      cacheEntry.put("nonce", nonce);
      appendParam(url, "nonce", nonce);
    }

    if (spec.isPkceEnabled()) {
      String codeVerifier = com.ouroboros.security.externalauth.SecurityTokens.generatePkceCodeVerifier();
      cacheEntry.put("codeVerifier", codeVerifier);
      String pkceMethod = normalizePkceMethod(spec.getPkceMethod());
      appendParam(url, "code_challenge", buildCodeChallenge(codeVerifier, pkceMethod));
      appendParam(url, "code_challenge_method", pkceMethod);
    }

    return AuthorizeUrlResult.of(url.toString(), state, cacheEntry);
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params) {
    if (params == null || params.hasCallbackParameter("error")) {
      return Either.left(AuthError.invalidCredentials("OAuth2 authorization failed"));
    }
    String code = params.getSingleCallbackParameter("code");
    if (!hasText(code)) {
      return Either.left(AuthError.invalidCredentials("OAuth2 code is required"));
    }
    String redirectUri = stringValue(params.getCachedState().get("redirectUri"));
    String codeVerifier = stringValue(params.getCachedState().get("codeVerifier"));
    ExchangeParams exchangeParams = ExchangeParams.ofCode(
        code,
        redirectUri,
        codeVerifier,
        params.getCorrelationId()
    );
    try {
      return handleCodeExchange(exchangeParams, stringValue(params.getCachedState().get("nonce")));
    } catch (Exception failure) {
      return exchangeFailure(failure);
    }
  }

  @Override
  public Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params) {
    try {
      if (params.isCodeExchange()) {
        return handleCodeExchange(params, null);
      }
      return handleIdTokenExchange(params);
    } catch (Exception e) {
      return exchangeFailure(e);
    }
  }

  private Either<AuthError, ExternalIdentity> exchangeFailure(Exception failure) {
    logger.warn("OAuth2 exchange failed: provider={}, errorType={}", spec.getKey(), failure.getClass().getSimpleName());
    return Either.left(AuthError.providerUnavailable("OAuth2 provider request failed"));
  }

  private Either<AuthError, ExternalIdentity> handleCodeExchange(
      ExchangeParams params, String expectedNonce) {
    if (!hasText(params.getCode())) {
      return Either.left(AuthError.invalidCredentials("OAuth2 code is required"));
    }
    if (spec.isPkceEnabled() && !hasText(params.getCodeVerifier())) {
      return Either.left(AuthError.invalidCredentials("codeVerifier is required when PKCE is enabled"));
    }

    Map<String, Object> tokenResponse = httpClient.exchangeCode(
        spec.getTokenUri(),
        params.getCode(),
        params.getRedirectUri(),
        params.getCodeVerifier(),
        clientId,
        clientSecret
    );

    if (tokenResponse == null) {
      return Either.left(AuthError.providerUnavailable("Token endpoint returned empty response"));
    }

    String idToken = (String) tokenResponse.get("id_token");
    if (idToken != null && spec.getJwksUri() != null && !spec.getJwksUri().isEmpty()) {
      Either<AuthError, Claims> claimsResult = jwksKeyResolver.verifyAndParse(
          idToken,
          spec.getJwksUri(),
          clientId,
          spec.getIssuer()
      );
      if (claimsResult.isRight()) {
        if (!nonceMatches(claimsResult.get(), expectedNonce)) {
          return Either.left(AuthError.invalidCredentials("id_token nonce mismatch"));
        }
        return buildIdentityFromClaims(claimsResult.get());
      }
      return Either.left(claimsResult.getLeft());
    }

    String accessToken = (String) tokenResponse.get("access_token");
    if (accessToken == null) {
      return Either.left(AuthError.invalidCredentials("Token response missing access_token"));
    }

    if (spec.getUserInfoUri() != null && !spec.getUserInfoUri().isEmpty()) {
      Map<String, Object> userInfo = httpClient.fetchUserInfo(spec.getUserInfoUri(), accessToken);
      if (userInfo == null) {
        return Either.left(AuthError.providerUnavailable("UserInfo endpoint returned empty response"));
      }
      return buildIdentityFromUserInfo(userInfo);
    }

    return Either.left(AuthError.providerUnavailable("No identity source configured (neither jwksUri nor userInfoUri)"));
  }

  private Either<AuthError, ExternalIdentity> handleIdTokenExchange(ExchangeParams params) {
    if (!hasText(spec.getJwksUri())) {
      return Either.left(AuthError.providerUnavailable("jwksUri not configured for id_token verification"));
    }
    if (!hasText(params.getIdToken())) {
      return Either.left(AuthError.invalidCredentials("idToken is required"));
    }

    Either<AuthError, Claims> claimsResult = jwksKeyResolver.verifyAndParse(
        params.getIdToken(),
        spec.getJwksUri(),
        clientId,
        spec.getIssuer()
    );
    if (claimsResult.isLeft()) {
      return Either.left(claimsResult.getLeft());
    }

    return buildIdentityFromClaims(claimsResult.get());
  }

  private Either<AuthError, ExternalIdentity> buildIdentityFromClaims(Claims claims) {
    String externalIdField = spec.getExternalIdField() != null ? spec.getExternalIdField() : "sub";
    String externalId = requiredExternalId(claims.get(externalIdField));
    if (externalId == null) {
      return Either.left(AuthError.invalidCredentials("id_token missing stable external ID"));
    }
    String displayName = (String) claims.get("name");
    String email = (String) claims.get("email");

    HashMap<String, Object> attributes = new HashMap<String, Object>(claims);
    putMemberId(attributes, claims);
    return Either.right(ExternalIdentity.of(spec.getKey(), externalId, displayName, email, attributes));
  }

  private Either<AuthError, ExternalIdentity> buildIdentityFromUserInfo(Map<String, Object> userInfo) {
    Map<String, Object> identityPayload = userInfo;
    if (hasText(spec.getUserInfoDataField())) {
      Object nested = userInfo.get(spec.getUserInfoDataField());
      if (!(nested instanceof Map<?, ?> nestedMap)) {
        return Either.left(AuthError.invalidCredentials(
            "UserInfo response missing configured identity data field"));
      }
      identityPayload = new HashMap<>();
      for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
        if (!(entry.getKey() instanceof String field)) {
          return Either.left(AuthError.invalidCredentials(
              "UserInfo identity data contains a non-text field"));
        }
        identityPayload.put(field, entry.getValue());
      }
    }
    String externalIdField = spec.getExternalIdField() != null ? spec.getExternalIdField() : "sub";
    String externalId = requiredExternalId(identityPayload.get(externalIdField));
    if (externalId == null) {
      return Either.left(AuthError.invalidCredentials("UserInfo response missing stable external ID"));
    }
    String displayName = textValue(identityPayload.get("name"));
    String email = textValue(identityPayload.get("email"));

    Map<String, Object> attributes = new HashMap<>(identityPayload);
    putMemberId(attributes, identityPayload);
    return Either.right(ExternalIdentity.of(spec.getKey(), externalId, displayName, email,
        attributes));
  }

  /**
   * 仅在 spec 显式声明成员标识字段时写入。登录身份与审批成员标识是两个作用域（钉钉 unionId 与
   * userId），因此不允许默认复用 {@code externalIdField}。
   */
  private void putMemberId(Map<String, Object> attributes, Map<String, ?> source) {
    if (!hasText(spec.getMemberIdField())) {
      return;
    }
    String memberId = requiredExternalId(source.get(spec.getMemberIdField()));
    if (memberId != null) {
      attributes.put(ExternalIdentityAttributes.MEMBER_ID, memberId);
    }
  }

  private void appendParam(StringBuilder url, String key, String value) {
    if (!hasText(value)) {
      return;
    }
    String encodedValue = value;
    try {
      encodedValue = URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      logger.error("Failed to URL-encode parameter '{}' with value '{}': {}", key, value, e.getMessage());
      // UTF-8 is always supported, this should never happen
    }
    char separator = url.indexOf("?") >= 0 ? '&' : '?';
    url.append(separator).append(key).append('=').append(encodedValue);
  }

  private String buildRedirectUri() {
    String baseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
    return baseUrl + "/api/auth/oauth2/" + spec.getKey() + "/callback";
  }

  private boolean requiresNonce(List<String> scopes) {
    return scopes != null && scopes.stream().anyMatch("openid"::equals);
  }

  private boolean nonceMatches(Claims claims, String expectedNonce) {
    if (!hasText(expectedNonce)) {
      return true;
    }
    return expectedNonce.equals(stringValue(claims.get("nonce")));
  }

  private String requiredExternalId(Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    String result = text.trim();
    return result.isEmpty() ? null : result;
  }

  private String textValue(Object value) {
    return value instanceof String text ? text : null;
  }

  private String normalizePkceMethod(String pkceMethod) {
    if ("plain".equalsIgnoreCase(pkceMethod)) {
      return "plain";
    }
    return "S256";
  }

  private String buildCodeChallenge(String codeVerifier, String pkceMethod) {
    if ("plain".equals(pkceMethod)) {
      return codeVerifier;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(codeVerifier.getBytes("UTF-8"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
      throw new IllegalStateException("Failed to generate PKCE code challenge", e);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

}
