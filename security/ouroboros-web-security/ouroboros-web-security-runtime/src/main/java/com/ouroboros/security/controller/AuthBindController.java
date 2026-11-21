package com.ouroboros.security.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.externalauth.BindingRateLimiter;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthResult;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.rbac.UserIdResolver;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.exception.BusinessException;

import io.vavr.control.Either;

/**
 * Binding and loginCode exchange endpoints for external auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthBindController {

    private static final Logger logger = LoggerFactory.getLogger(AuthBindController.class);

    @Autowired
    private ExternalAuthService externalAuthService;

    @Autowired
    private BindingRateLimiter bindingRateLimiter;

    @Autowired
    private UserIdResolver userIdResolver;

    @Autowired
    private UserPasswordVerifier userPasswordVerifier;

    @Autowired
    private UserSsoService userSsoService;

    /**
     * Bind an external identity to a platform user.
     * Accepts bindingToken + platform username/password.
     */
    @Anonymous
    @Operation(
        summary = "绑定外部认证账号",
        description = "校验 bindingToken 和平台账号密码后，将外部身份绑定到当前平台用户，并直接签发平台令牌。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "绑定成功并返回访问令牌"),
        @ApiResponse(responseCode = "401", description = "绑定令牌无效或平台账号凭据错误"),
        @ApiResponse(responseCode = "409", description = "外部身份已绑定到其他用户"),
        @ApiResponse(responseCode = "429", description = "触发绑定限流")
    })
    @PostMapping("/bind")
    public WebApiResult<?> bind(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "外部身份绑定请求", required = true)
        @RequestBody BindRequest request,
        HttpServletRequest httpRequest) {
        String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
        logger.info("[cid={}] Bind initiated", correlationId);

        // 0a. Per-token dedup rate limit
        Optional<String> dedupResult = bindingRateLimiter.checkTokenDedup(request.getBindingToken());
        if (dedupResult.isPresent()) {
            return WebApiResult.error(42901, dedupResult.get());
        }

        // 0b. Extract source IP
        String sourceIp = httpRequest.getHeader("X-Forwarded-For");
        if (sourceIp != null && sourceIp.contains(",")) {
            sourceIp = sourceIp.split(",")[0].trim();
        }
        if (sourceIp == null) {
            sourceIp = httpRequest.getRemoteAddr();
        }

        // 1. Validate bindingToken
        Optional<ExternalIdentity> identityOpt = externalAuthService.validateBindingToken(request.getBindingToken());
        if (!identityOpt.isPresent()) {
            return WebApiResult.error(40103, "Invalid or expired bindingToken");
        }
        ExternalIdentity identity = identityOpt.get();
        logger.info("[cid={}] Bind: provider={}, externalId={}", correlationId,
            identity.getProviderKey(), identity.getExternalId());

        // 2. Cross-token rate limits (externalId, username, sourceIp)
        Optional<String> rateLimitResult = bindingRateLimiter.checkCrossTokenLimits(
            identity.getProviderKey(), identity.getExternalId(), request.getUsername(), sourceIp);
        if (rateLimitResult.isPresent()) {
            return WebApiResult.error(42901, rateLimitResult.get());
        }

        // 3. Resolve platform user identity
        Optional<String> userIdentityOpt = userIdResolver.getUserId("username", request.getUsername());
        if (!userIdentityOpt.isPresent()) {
            externalAuthService.decrementBindingToken(request.getBindingToken());
            logger.warn("[cid={}] Bind failed: user not found for username", correlationId);
            return WebApiResult.error(40104, "Invalid platform credentials");
        }
        String userIdentity = userIdentityOpt.get();

        // 4. Verify password
        boolean passwordValid = userPasswordVerifier.verify(userIdentity, request.getPassword());
        if (!passwordValid) {
            externalAuthService.decrementBindingToken(request.getBindingToken());
            logger.warn("[cid={}] Bind failed: invalid password for userIdentity={}", correlationId, userIdentity);
            return WebApiResult.error(40104, "Invalid platform credentials");
        }
        logger.info("[cid={}] Bind: password verified for userIdentity={}", correlationId, userIdentity);

        // Password verification may take time; recheck the connection proof immediately before binding.
        if (externalAuthService.validateBindingToken(request.getBindingToken()).isEmpty()) {
            return WebApiResult.error(40103, "Invalid or expired bindingToken");
        }

        // 5. Create binding
        try {
            userSsoService.createBinding(userIdentity, identity.getProviderKey(), identity.getExternalId(),
                identity.getDisplayName(), identity.getEmail(), identity.getAttributes());
            logger.info("[cid={}] Binding created: provider={}, userIdentity={}", correlationId,
                identity.getProviderKey(), userIdentity);
        } catch (BusinessException e) {
            if (!Integer.valueOf(40902).equals(e.getErrorCode())) {
                throw e;
            }
            logger.warn("[cid={}] Concurrent binding conflict: providerKey={}, externalId={}",
                correlationId, identity.getProviderKey(), identity.getExternalId());
            return WebApiResult.error(40902, "External identity already bound to another user");
        }

        // 6. Consume bindingToken
        externalAuthService.consumeBindingToken(request.getBindingToken());

        // 7. Issue platform token (bind = login) via ExternalAuthService
        Either<AuthError, AuthResult> authResult = externalAuthService.issueSessionForUserIdentity(userIdentity, correlationId);
        if (authResult.isLeft()) {
            AuthError error = authResult.getLeft();
            return WebApiResult.error(error.getCode(), error.getMessage());
        }
        return WebApiResult.ok(getTokenResult(authResult.get().getSession()));
    }

    /**
     * Exchange a one-time loginCode for platform tokens.
     */
    @Anonymous
    @Operation(
        summary = "兑换登录码",
        description = "将一次性 loginCode 兑换为平台访问令牌和刷新令牌。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "兑换成功并返回访问令牌"),
        @ApiResponse(responseCode = "401", description = "loginCode 无效或已过期")
    })
    @PostMapping("/login-code/exchange")
    public WebApiResult<?> exchangeLoginCode(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "登录码兑换请求", required = true)
        @RequestBody LoginCodeExchangeRequest request) {
        String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
        logger.info("[cid={}] LoginCode exchange initiated", correlationId);

        // 1. Consume loginCode atomically
        Optional<Map<String, String>> codeEntryOpt = externalAuthService.consumeLoginCode(request.getLoginCode());
        if (!codeEntryOpt.isPresent()) {
            return WebApiResult.error(40105, "Invalid or expired loginCode");
        }
        Map<String, String> codeEntry = codeEntryOpt.get();
        String userIdentity = codeEntry.get("userIdentity");
        logger.info("[cid={}] LoginCode consumed: userIdentity={}", correlationId, userIdentity);

        // 2. Load user and issue session via ExternalAuthService
        Either<AuthError, AuthResult> authResult = externalAuthService.issueSessionForUserIdentity(userIdentity, correlationId);
        if (authResult.isLeft()) {
            AuthError error = authResult.getLeft();
            return WebApiResult.error(error.getCode(), error.getMessage());
        }
        logger.info("[cid={}] LoginCode exchange: session issued for userIdentity={}", correlationId, userIdentity);
        return WebApiResult.ok(getTokenResult(authResult.get().getSession()));
    }

    private Map<String, Object> getTokenResult(UserSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", session.getAttribute("accessToken"));
        data.put("refreshToken", session.getAttribute("refreshToken"));
        return data;
    }

    public static class BindRequest {
        @Schema(description = "待绑定外部身份对应的 bindingToken")
        private String bindingToken;
        @Schema(description = "平台用户名")
        private String username;
        @Schema(description = "平台用户密码")
        private String password;

        public String getBindingToken() { return bindingToken; }
        public void setBindingToken(String bindingToken) { this.bindingToken = bindingToken; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginCodeExchangeRequest {
        @Schema(description = "一次性登录码")
        private String loginCode;

        public String getLoginCode() { return loginCode; }
        public void setLoginCode(String loginCode) { this.loginCode = loginCode; }
    }

    // --- Self-service binding management (requires login) ---

    @Operation(
        summary = "查询当前用户的外部认证绑定",
        description = "返回当前登录用户已绑定的外部认证账号列表。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回当前用户的绑定列表")
    })
    @GetMapping("/bindings")
    public WebApiResult<?> listMyBindings() {
        String userIdentity = CurrentSecurityContextHolder.findIdentity()
            .map(identity -> identity.getUserIdentity())
            .orElse(AnonymousAuthentication.getInstance().getPrincipal());
        List<UserSsoEntity> bindings = userSsoService.findByUserIdentity(userIdentity);
        List<BindingInfo> result = new ArrayList<>();
        for (UserSsoEntity entity : bindings) {
            result.add(toBindingInfo(entity));
        }
        return WebApiResult.ok(result);
    }

    @Operation(
        summary = "删除当前用户的外部认证绑定",
        description = "删除当前登录用户名下指定的外部认证绑定记录。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "绑定删除成功"),
        @ApiResponse(responseCode = "404", description = "当前用户下不存在该绑定记录")
    })
    @DeleteMapping("/bindings/{id}")
    public WebApiResult<?> deleteMyBinding(
        @Parameter(description = "绑定记录 ID", required = true)
        @PathVariable String id) {
        String userIdentity = CurrentSecurityContextHolder.findIdentity()
            .map(identity -> identity.getUserIdentity())
            .orElse(AnonymousAuthentication.getInstance().getPrincipal());
        List<UserSsoEntity> bindings = userSsoService.findByUserIdentity(userIdentity);
        boolean owned = false;
        for (UserSsoEntity entity : bindings) {
            if (id.equals(entity.getId())) {
                owned = true;
                break;
            }
        }
        if (!owned) {
            return WebApiResult.error(40401, "Binding not found");
        }
        userSsoService.deleteBinding(id);
        return WebApiResult.ok(null);
    }

    static BindingInfo toBindingInfo(UserSsoEntity entity) {
        BindingInfo info = new BindingInfo();
        info.setId(entity.getId());
        info.setProviderKey(entity.getSsoChannel());
        info.setExternalId(entity.getSsoUserIdentity());
        info.setDisplayName(entity.getExternalDisplayName());
        info.setEmail(entity.getExternalEmail());
        info.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return info;
    }

    public static class BindingInfo {
        @Schema(description = "绑定记录 ID")
        private String id;
        @Schema(description = "外部认证提供方标识")
        private String providerKey;
        @Schema(description = "外部身份唯一标识")
        private String externalId;
        @Schema(description = "外部账号展示名称")
        private String displayName;
        @Schema(description = "外部账号邮箱")
        private String email;
        @Schema(description = "绑定创建时间")
        private String createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProviderKey() { return providerKey; }
        public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
