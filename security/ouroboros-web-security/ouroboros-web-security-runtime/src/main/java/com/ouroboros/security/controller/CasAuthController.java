package com.ouroboros.security.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.cache.CacheManager;
import io.vavr.control.Either;

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
import com.ouroboros.security.externalauth.cas.CasProvider;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.web.WebApiResult;

/**
 * CAS authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth/cas")
public class CasAuthController {

    private static final Logger logger = LoggerFactory.getLogger(CasAuthController.class);

    private static final String CAS_STATE_CACHE = "ouroboros:auth:cas-state";
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
        summary = "发起 CAS 登录",
        description = "为指定 CAS 提供方生成授权跳转地址，并将浏览器重定向到 CAS 登录页。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "成功跳转到 CAS 登录页"),
        @ApiResponse(responseCode = "502", description = "CAS 提供方不存在或不可用")
    })
    @GetMapping("/{provider}/login")
    public void login(@Parameter(description = "CAS 提供方标识", required = true) @PathVariable("provider") String provider,
                      @Parameter(description = "登录完成后的前端返回地址", required = true) @RequestParam("returnUrl") String returnUrl,
                      HttpServletResponse response) throws IOException {
        String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();

        // 1. Resolve provider
        Optional<RedirectAuthProvider> redirectProvider = providerFactory.getRedirectProvider(provider, "cas");
        if (!redirectProvider.isPresent()) {
            sendLoginErrorRedirect(response, AuthError.providerUnavailable("Provider not found or unavailable"));
            return;
        }

        // 2. Validate returnUrl: must be relative path or same origin
        String normalizedReturnUrl = returnUrl;
        if (!isValidReturnUrl(normalizedReturnUrl)) {
            logger.warn("[cid={}] CAS auth returnUrl invalid, fallback to '/': provider={}", correlationId, provider);
            normalizedReturnUrl = "/";
        }

        // 3. Build authorize URL (generates state, constructs CAS login URL)
        logger.info("[cid={}] CAS auth initiated: provider={}", correlationId, provider);
        AuthorizeUrlResult authorizeResult = redirectProvider.get().buildAuthorizeUrl(normalizedReturnUrl);

        // 4. Cache state entry (returnUrl + serviceUrl + correlationId)
        Map<String, Object> cacheEntry = new HashMap<>(authorizeResult.getCacheEntry());
        cacheEntry.put("correlationId", correlationId);
        cacheEntry.put("providerRevision", redirectProvider.get().getSpec().getConfigurationRevision());
        cacheManager.put(CAS_STATE_CACHE, authorizeResult.getState(), cacheEntry, STATE_TTL_MS);

        // 5. 302 redirect to CAS login
        response.sendRedirect(authorizeResult.getAuthorizeUrl());
    }

    @Anonymous
    @Operation(
        summary = "处理 CAS 回调",
        description = "接收 CAS 回调参数，完成票据校验、身份解析并重定向到登录结果页面。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "成功重定向到登录成功或绑定页面"),
        @ApiResponse(responseCode = "401", description = "CAS state 或 ticket 无效"),
        @ApiResponse(responseCode = "502", description = "CAS 提供方不存在或不可用")
    })
    @GetMapping("/{provider}/callback")
    public void callback(@Parameter(description = "CAS 提供方标识", required = true) @PathVariable("provider") String provider,
                         @Parameter(description = "CAS state 参数", required = true) @RequestParam("state") String state,
                         @Parameter(description = "CAS 回调票据 ticket", required = true) @RequestParam("ticket") String ticket,
                         HttpServletResponse response) throws IOException {
        // 1. Resolve provider
        Optional<RedirectAuthProvider> redirectProvider = providerFactory.getRedirectProvider(provider, "cas");
        if (!redirectProvider.isPresent()) {
            sendLoginErrorRedirect(response, AuthError.providerUnavailable("Provider not found or unavailable"));
            return;
        }

        // 2. Consume state from cache (atomic read + delete)
        Optional<Object> cachedRaw = cacheManager.getAndEvict(CAS_STATE_CACHE, state);
        if (!cachedRaw.isPresent()) {
            sendLoginErrorRedirect(response, AuthError.invalidState("Invalid or expired state"));
            return;
        }
        Map<String, Object> cachedState = safeCastToMap(cachedRaw.get());
        if (cachedState == null) {
            sendLoginErrorRedirect(response, AuthError.invalidState("Invalid state format"));
            return;
        }
        if (!java.util.Objects.equals(redirectProvider.get().getSpec().getConfigurationRevision(),
            cachedState.get("providerRevision"))) {
            sendLoginErrorRedirect(response, AuthError.invalidState("Provider configuration changed during authorization"));
            return;
        }
        String returnUrl = normalizeReturnUrl((String) cachedState.get("returnUrl"));
        String correlationId = (String) cachedState.get("correlationId");
        if (correlationId == null) {
            correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
        }
        logger.info("[cid={}] CAS callback received: provider={}", correlationId, provider);

        // 3. Handle callback via provider (ticket validation with exact service URL)
        CallbackParams callbackParams = CallbackParams.fromRedirectCallback(
            state, Map.of("ticket", List.of(ticket)), cachedState, correlationId);
        Either<AuthError, ExternalIdentity> authResult = redirectProvider.get().handleCallback(callbackParams);
        if (authResult.isLeft()) {
            AuthError error = authResult.getLeft();
            logger.warn("[cid={}] CAS callback failed: provider={}, error={}", correlationId, provider, error.getMessage());
            sendLoginErrorRedirect(response, error);
            return;
        }

        // 4. Process external identity
        ExternalIdentity identity = authResult.get();
        logger.info("[cid={}] CAS identity resolved: provider={}, externalId={}", correlationId, provider, identity.getExternalId());
        Either<AuthError, AuthResult> processResult = externalAuthService.processExternalIdentity(identity, correlationId);
        if (processResult.isLeft()) {
            AuthError error = processResult.getLeft();
            sendLoginErrorRedirect(response, error);
            return;
        }

        // 5. Redirect based on result
        AuthResult result = processResult.get();
        String publicBaseUrl = externalAuthProperties.get().getPublicBaseUrl();
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
        summary = "交换 CAS 登录结果",
        description = "使用 CAS ticket 完成最终登录结果兑换，返回平台令牌或绑定令牌。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回登录结果"),
        @ApiResponse(responseCode = "409", description = "外部身份尚未绑定，需要先完成绑定"),
        @ApiResponse(responseCode = "502", description = "CAS 提供方不存在或 CAS 验证失败")
    })
    @PostMapping("/{provider}/exchange")
    public WebApiResult<?> exchange(
        @Parameter(description = "CAS 提供方标识", required = true)
        @PathVariable("provider") String provider,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "CAS 票据兑换请求", required = true)
        @RequestBody CasExchangeRequest request) {
        String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
        logger.info("[cid={}] CAS exchange initiated: provider={}", correlationId, provider);

        // 1. Resolve provider
        Optional<RedirectAuthProvider> redirectProvider = providerFactory.getRedirectProvider(provider, "cas");
        if (!redirectProvider.isPresent()) {
            AuthError error = AuthError.providerUnavailable("Provider not found or unavailable");
            return WebApiResult.error(error.getCode(), error.getMessage());
        }
        String ticket = request != null ? request.getTicket() : null;
        RedirectAuthProvider resolvedProvider = redirectProvider.get();
        CasProvider casProvider = resolvedProvider instanceof CasProvider ? (CasProvider) resolvedProvider : null;
        if (casProvider != null && ticket != null && !ticket.isEmpty()) {
            Optional<CasProvider.CasTicketFinalResult> cachedResult = casProvider.consumeFinalResult(ticket);
            if (cachedResult.isPresent()) {
                return toWebResult(cachedResult.get());
            }
        }

        // 2. Validate ticket via CAS
        Either<AuthError, ExternalIdentity> authResult = resolvedProvider
                .handleExchange(ExchangeParams.ofTicket(ticket, correlationId));
        if (authResult.isLeft()) {
            AuthError error = authResult.getLeft();
            return WebApiResult.error(error.getCode(), error.getMessage());
        }

        // 3. Process external identity (binding lookup + token issuance)
        ExternalIdentity identity = authResult.get();
        logger.info("[cid={}] CAS identity resolved: provider={}, externalId={}", correlationId, provider, identity.getExternalId());
        Either<AuthError, AuthResult> processResult = externalAuthService.processExternalIdentity(identity, correlationId);
        if (processResult.isLeft()) {
            AuthError error = processResult.getLeft();
            return WebApiResult.error(error.getCode(), error.getMessage());
        }

        // 4. Map result
        AuthResult result = processResult.get();
        if (result.getType() == AuthResult.Type.LOGGED_IN) {
            Map<String, Object> tokenResult = getTokenResult(result.getSession());
            if (casProvider != null && ticket != null && !ticket.isEmpty()) {
                casProvider.cacheFinalResult(ticket, CasProvider.CasTicketFinalResult.loggedIn(
                    (String) tokenResult.get("accessToken"),
                    (String) tokenResult.get("refreshToken")
                ), correlationId);
            }
            return WebApiResult.ok(tokenResult);
        } else {
            HashMap<String, Object> data = new HashMap<String, Object>();
            data.put("bindingToken", result.getBindingToken());
            if (casProvider != null && ticket != null && !ticket.isEmpty()) {
                casProvider.cacheFinalResult(ticket,
                    CasProvider.CasTicketFinalResult.bindingRequired(result.getBindingToken()), correlationId);
            }
            AuthError error = AuthError.bindingRequired("Binding required");
            return WebApiResult.build(error.getCode(), error.getMessage(), data);
        }
    }

    private WebApiResult<?> toWebResult(CasProvider.CasTicketFinalResult result) {
        if (result.isLoggedIn()) {
            Map<String, Object> tokenResult = new HashMap<String, Object>();
            tokenResult.put("accessToken", result.getAccessToken());
            tokenResult.put("refreshToken", result.getRefreshToken());
            return WebApiResult.ok(tokenResult);
        }
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("bindingToken", result.getBindingToken());
        AuthError error = AuthError.bindingRequired("Binding required");
        return WebApiResult.build(error.getCode(), error.getMessage(), data);
    }

    private boolean isValidReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isEmpty()) {
            return false;
        }
        if (returnUrl.startsWith("//")) {
            return false;
        }
        // Must be relative path (starts with /)
        if (returnUrl.startsWith("/")) {
            return true;
        }
        // Reject absolute URLs to prevent open redirect
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
        String publicBaseUrl = externalAuthProperties.get().getPublicBaseUrl();
        if (publicBaseUrl == null) {
            publicBaseUrl = "";
        }
        String loginUrl = publicBaseUrl + "/login";
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

    private Map<String, Object> getTokenResult(UserSession session) {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("accessToken", session.getAttribute("accessToken"));
        data.put("refreshToken", session.getAttribute("refreshToken"));
        return data;
    }

    public static class CasExchangeRequest {
        @Schema(description = "CAS ticket")
        private String ticket;

        public String getTicket() { return ticket; }
        public void setTicket(String ticket) { this.ticket = ticket; }
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
