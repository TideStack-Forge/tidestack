package com.ouroboros.security.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.control.Either;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthResult;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExchangeParams;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuth2AuthController {

  private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthController.class);

  private static final String OAUTH2_STATE_CACHE = "ouroboros:auth:oauth2-state";
  private static final long STATE_TTL_MS = 300_000L;

  @Autowired
  private ExternalAuthProviderFactory providerFactory;

  @Autowired
  private ExternalAuthService externalAuthService;

  @Autowired
  private ExternalAuthConfiguration externalAuthProperties;

  @Autowired
  private CacheManager cacheManager;

  @Anonymous
  @Operation(
      summary = "发起外部授权",
      description = "为指定的重定向认证提供方生成授权地址，并重定向浏览器到外部授权页。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "302", description = "成功跳转到外部授权页"),
      @ApiResponse(responseCode = "502", description = "外部认证提供方不存在或不可用")
  })
  @GetMapping("/{provider}/authorize")
  public void authorize(@Parameter(description = "外部认证提供方标识", required = true) @PathVariable("provider") String provider,
                        @Parameter(description = "授权完成后的前端返回地址", required = true) @RequestParam("returnUrl") String returnUrl,
                        HttpServletResponse response) throws IOException {
    String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();

    Optional<RedirectAuthProvider> redirectProvider = providerFactory.getOAuth2FlowProvider(provider);
    if (!redirectProvider.isPresent()) {
      sendLoginErrorRedirect(response, AuthError.providerUnavailable("Provider not found or unavailable"));
      return;
    }

    String normalizedReturnUrl = normalizeReturnUrl(returnUrl);
    if (!normalizedReturnUrl.equals(returnUrl)) {
      logger.warn("[cid={}] External auth returnUrl invalid, fallback to '/': provider={}", correlationId, provider);
    }

    logger.info("[cid={}] External auth initiated: provider={}", correlationId, provider);
    AuthorizeUrlResult authorizeResult = redirectProvider.get().buildAuthorizeUrl(normalizedReturnUrl);

    Map<String, Object> cacheEntry = new HashMap<String, Object>(authorizeResult.getCacheEntry());
    cacheEntry.put("correlationId", correlationId);
    cacheEntry.put("providerKey", provider);
    cacheEntry.put("providerRevision", redirectProvider.get().getSpec().getConfigurationRevision());
    if (isConnectionScoped(redirectProvider.get())) {
      cacheEntry.put("connectionId", provider);
      cacheEntry.put("connectionProfileRevision", connectionProfileRevision(redirectProvider.get()));
    }
    cacheManager.put(OAUTH2_STATE_CACHE, authorizeResult.getState(), cacheEntry, STATE_TTL_MS);

    response.sendRedirect(authorizeResult.getAuthorizeUrl());
  }

  @Anonymous
  @Operation(
      summary = "处理外部认证回调",
      description = "处理重定向认证回调，完成 code/state 校验并重定向到登录结果页面。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "302", description = "成功重定向到登录成功或绑定页面"),
      @ApiResponse(responseCode = "400", description = "外部认证回调缺少必要参数"),
      @ApiResponse(responseCode = "401", description = "OAuth2 state 无效或认证失败"),
      @ApiResponse(responseCode = "502", description = "外部认证提供方不存在或不可用")
  })
  @GetMapping("/{provider}/callback")
  public void callback(@Parameter(description = "外部认证提供方标识", required = true) @PathVariable("provider") String provider,
                       @Parameter(description = "外部认证提供方返回的回调参数", required = true)
                       @RequestParam MultiValueMap<String, String> callbackParameters,
                       HttpServletResponse response) throws IOException {
    // 参数归一化统一由 CallbackParams 承担：state 必须恰好出现一次，重复即按歧义拒绝。
    CallbackParams callbackParams = CallbackParams.fromRedirectCallback(
        null, callbackParameters, null, null);
    String state = callbackParams.getSingleCallbackParameter("state");
    if (!hasText(state)) {
      sendLoginErrorRedirect(response, AuthError.invalidState("External auth state is required exactly once"));
      return;
    }
    Optional<Object> cachedRaw = cacheManager.getAndEvict(OAUTH2_STATE_CACHE, state);
    if (!cachedRaw.isPresent()) {
      sendLoginErrorRedirect(response, AuthError.invalidState("Invalid or expired state"));
      return;
    }

    Map<String, Object> cachedState = safeCastToMap(cachedRaw.get());
    if (cachedState == null) {
      sendLoginErrorRedirect(response, AuthError.invalidState("Invalid state format"));
      return;
    }
    if (!provider.equals(stringValue(cachedState.get("providerKey")))) {
      sendLoginErrorRedirect(response, AuthError.invalidState("State was issued for another provider"));
      return;
    }

    Optional<RedirectAuthProvider> redirectProvider = providerFactory.getOAuth2FlowProvider(provider);
    if (!redirectProvider.isPresent()) {
      sendLoginErrorRedirect(response, AuthError.providerUnavailable("Provider not found or unavailable"));
      return;
    }
    if (!java.util.Objects.equals(redirectProvider.get().getSpec().getConfigurationRevision(),
        cachedState.get("providerRevision"))) {
      sendLoginErrorRedirect(response, AuthError.invalidState("Provider configuration changed during authorization"));
      return;
    }
    if (isConnectionScoped(redirectProvider.get())) {
      if (!provider.equals(stringValue(cachedState.get("connectionId")))) {
        sendLoginErrorRedirect(response, AuthError.invalidState("State was issued for another connection"));
        return;
      }
      if (!connectionProfileRevision(redirectProvider.get())
          .equals(stringValue(cachedState.get("connectionProfileRevision")))) {
        sendLoginErrorRedirect(response, AuthError.invalidState("Connection profile changed during authorization"));
        return;
      }
    }
    String returnUrl = normalizeReturnUrl((String) cachedState.get("returnUrl"));
    String correlationId = (String) cachedState.get("correlationId");
    if (!hasText(correlationId)) {
      correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
    }
    logger.info("[cid={}] External auth callback received: provider={}", correlationId, provider);

    CallbackParams providerParams = CallbackParams.fromRedirectCallback(
        state, callbackParameters, cachedState, correlationId);
    Either<AuthError, ExternalIdentity> authResult = redirectProvider.get().handleCallback(providerParams);
    if (authResult.isLeft()) {
      AuthError error = authResult.getLeft();
      logger.warn("[cid={}] External auth callback failed: provider={}, error={}",
          correlationId, provider, error.getMessage());
      sendLoginErrorRedirect(response, error);
      return;
    }

    ExternalIdentity identity = authResult.get();
    logger.info("[cid={}] External auth identity resolved: provider={}, externalId={}",
        correlationId, provider, identity.getExternalId());
    Either<AuthError, AuthResult> processResult = isConnectionScoped(redirectProvider.get())
        ? externalAuthService.processExternalIdentity(identity, correlationId, connectionProfileRevision(redirectProvider.get()))
        : externalAuthService.processExternalIdentity(identity, correlationId);
    if (processResult.isLeft()) {
      sendLoginErrorRedirect(response, processResult.getLeft());
      return;
    }

    AuthResult result = processResult.get();
    String publicBaseUrl = getPublicBaseUrl();
    if (result.getType() == AuthResult.Type.LOGGED_IN) {
      String loginCode = externalAuthService.generateLoginCode(result.getUserIdentity(), returnUrl);
      String callbackUrl = publicBaseUrl + "/login/callback";
      callbackUrl = appendParam(callbackUrl, "code", loginCode);
      callbackUrl = appendParam(callbackUrl, "returnUrl", returnUrl);
      response.sendRedirect(callbackUrl);
    } else {
      String bindUrl = publicBaseUrl + "/login/bind";
      bindUrl = appendParam(bindUrl, "token", result.getBindingToken());
      bindUrl = appendParam(bindUrl, "returnUrl", returnUrl);
      response.sendRedirect(bindUrl);
    }
  }


  @Anonymous
  @Operation(
      summary = "交换外部认证登录结果",
      description = "使用授权码或 idToken 换取平台登录结果，返回平台令牌或绑定令牌。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回登录结果"),
      @ApiResponse(responseCode = "400", description = "缺少 code 或 idToken"),
      @ApiResponse(responseCode = "409", description = "外部身份尚未绑定，需要先完成绑定"),
      @ApiResponse(responseCode = "502", description = "外部认证提供方不存在或认证失败")
  })
  @PostMapping("/{provider}/exchange")
  public WebApiResult<?> exchange(
      @Parameter(description = "外部认证提供方标识", required = true)
      @PathVariable("provider") String provider,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "外部认证登录结果兑换请求", required = true)
      @RequestBody OAuth2ExchangeRequest request) {
    String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
    logger.info("[cid={}] External auth exchange initiated: provider={}", correlationId, provider);

    Optional<RedirectAuthProvider> redirectProvider = providerFactory.getRedirectProvider(provider, "oauth2");
    if (!redirectProvider.isPresent()) {
      AuthError error = AuthError.providerUnavailable("Provider not found or unavailable");
      return WebApiResult.error(error.getCode(), error.getMessage());
    }
    if (isConnectionScoped(redirectProvider.get())) {
      AuthError error = AuthError.invalidCredentials(
          "Connection-scoped provider must use the authorize/callback flow");
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    ExchangeParams exchangeParams = buildExchangeParams(request, correlationId);
    if (exchangeParams == null) {
      AuthError error = AuthError.invalidCredentials("Either code or idToken is required");
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    Either<AuthError, ExternalIdentity> authResult = redirectProvider.get().handleExchange(exchangeParams);
    if (authResult.isLeft()) {
      AuthError error = authResult.getLeft();
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    ExternalIdentity identity = authResult.get();
    logger.info("[cid={}] External auth identity resolved: provider={}, externalId={}",
        correlationId, provider, identity.getExternalId());
    Either<AuthError, AuthResult> processResult = externalAuthService.processExternalIdentity(identity, correlationId);
    if (processResult.isLeft()) {
      AuthError error = processResult.getLeft();
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    AuthResult result = processResult.get();
    if (result.getType() == AuthResult.Type.LOGGED_IN) {
      return WebApiResult.ok(getTokenResult(result.getSession()));
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("bindingToken", result.getBindingToken());
    AuthError error = AuthError.bindingRequired("Binding required");
    return WebApiResult.build(error.getCode(), error.getMessage(), data);
  }

  private ExchangeParams buildExchangeParams(OAuth2ExchangeRequest request, String correlationId) {
    if (request == null) {
      return null;
    }
    if (hasText(request.getCode())) {
      return ExchangeParams.ofCode(
          request.getCode(),
          request.getRedirectUri(),
          request.getCodeVerifier(),
          correlationId
      );
    }
    if (hasText(request.getIdToken())) {
      return ExchangeParams.ofIdToken(request.getIdToken(), correlationId);
    }
    return null;
  }

  private boolean isValidReturnUrl(String returnUrl) {
    if (!hasText(returnUrl)) {
      return false;
    }
    if (returnUrl.startsWith("//")) {
      return false;
    }
    if (returnUrl.startsWith("/")) {
      return true;
    }
    try {
      URI uri = new URI(returnUrl);
      return !uri.isAbsolute();
    } catch (Exception e) {
      return false;
    }
  }

  private String normalizeReturnUrl(String returnUrl) {
    return isValidReturnUrl(returnUrl) ? returnUrl : "/";
  }

  private void sendLoginErrorRedirect(HttpServletResponse response, AuthError error) throws IOException {
    String loginUrl = getPublicBaseUrl() + "/login";
    response.sendRedirect(appendParam(loginUrl, "error", String.valueOf(error.getCode())));
  }

  private String appendParam(String url, String key, String value) {
    if (value == null) {
      return url;
    }
    String encodedValue;
    try {
      encodedValue = URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      encodedValue = value;
    }
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + key + "=" + encodedValue;
  }

  private String getPublicBaseUrl() {
    String publicBaseUrl = externalAuthProperties.get().getPublicBaseUrl();
    return publicBaseUrl == null ? "" : publicBaseUrl;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private boolean isConnectionScoped(RedirectAuthProvider provider) {
    return provider.getSpec().isConnectionScoped();
  }

  private String connectionProfileRevision(RedirectAuthProvider provider) {
    ProviderSpec spec = provider.getSpec();
    return hasText(spec.getConnectionProfileRevision()) ? spec.getConnectionProfileRevision() : "";
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }


  private Map<String, Object> getTokenResult(UserSession session) {
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("accessToken", session.getAttribute("accessToken"));
    data.put("refreshToken", session.getAttribute("refreshToken"));
    return data;
  }

  public static class OAuth2ExchangeRequest {
    @Schema(description = "OAuth2 授权码")
    private String code;
    @Schema(description = "授权码对应的回调地址")
    private String redirectUri;
    @Schema(description = "PKCE code verifier")
    private String codeVerifier;
    @Schema(description = "OpenID Connect idToken")
    private String idToken;

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getRedirectUri() {
      return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
      this.redirectUri = redirectUri;
    }

    public String getCodeVerifier() {
      return codeVerifier;
    }

    public void setCodeVerifier(String codeVerifier) {
      this.codeVerifier = codeVerifier;
    }

    public String getIdToken() {
      return idToken;
    }

    public void setIdToken(String idToken) {
      this.idToken = idToken;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> safeCastToMap(Object obj) {
    if (!(obj instanceof Map)) {
      return null;
    }
    Map<?, ?> rawMap = (Map<?, ?>) obj;
    for (Object key : rawMap.keySet()) {
      if (!(key instanceof String)) {
        return null;
      }
    }
    return (Map<String, Object>) rawMap;
  }
}
