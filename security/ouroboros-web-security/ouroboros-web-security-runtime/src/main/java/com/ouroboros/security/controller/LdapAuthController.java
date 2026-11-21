package com.ouroboros.security.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import io.vavr.control.Either;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthResult;
import com.ouroboros.security.externalauth.DirectAuthProvider;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.web.WebApiResult;

/**
 * LDAP authentication endpoint.
 */
@RestController
@RequestMapping("/api/auth/ldap")
public class LdapAuthController {

  private static final Logger logger = LoggerFactory.getLogger(LdapAuthController.class);

  @Autowired
  private ExternalAuthProviderFactory providerFactory;

  @Autowired
  private ExternalAuthService externalAuthService;

  @Anonymous
  @Operation(
      summary = "LDAP 登录",
      description = "调用指定 LDAP 提供方校验用户名密码，并返回平台令牌或绑定令牌。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "登录成功并返回平台令牌"),
      @ApiResponse(responseCode = "409", description = "LDAP 身份尚未绑定，需要先完成绑定"),
      @ApiResponse(responseCode = "502", description = "LDAP 提供方不存在或认证失败")
  })
  @PostMapping("/{provider}/login")
  public WebApiResult<?> login(
      @Parameter(description = "LDAP 提供方标识", required = true)
      @PathVariable("provider") String provider,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "LDAP 登录请求", required = true)
      @RequestBody LdapLoginRequest request) {
    String correlationId = com.ouroboros.security.externalauth.SecurityTokens.generateCorrelationId();
    logger.info("[cid={}] LDAP auth initiated: provider={}", correlationId, provider);

    // 1. Resolve provider
    Optional<DirectAuthProvider> directProvider = providerFactory.getDirectProvider(provider);
    if (!directProvider.isPresent()) {
      return WebApiResult.error(50201, "Provider not found or unavailable");
    }

    // 2. Authenticate against LDAP
    Either<AuthError, ExternalIdentity> authResult = directProvider.get().authenticate(request.getUsername(), request.getPassword());
    if (authResult.isLeft()) {
      AuthError error = authResult.getLeft();
      logger.warn("[cid={}] LDAP auth failed: provider={}, code={}", correlationId, provider, error.getCode());
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    // 3. Process external identity (binding lookup + token issuance)
    ExternalIdentity identity = authResult.get();
    logger.info("[cid={}] LDAP identity resolved: provider={}, externalId={}", correlationId, provider, identity.getExternalId());
    Either<AuthError, AuthResult> processResult = externalAuthService.processExternalIdentity(identity, correlationId);
    if (processResult.isLeft()) {
      AuthError error = processResult.getLeft();
      return WebApiResult.error(error.getCode(), error.getMessage());
    }

    // 4. Map result
    AuthResult result = processResult.get();
    if (result.getType() == AuthResult.Type.LOGGED_IN) {
      return WebApiResult.ok(getTokenResult(result.getSession()));
    } else {
      HashMap<String, Object> data = new HashMap<String, Object>();
      data.put("bindingToken", result.getBindingToken());
      return WebApiResult.build(40901, "Binding required", data);
    }
  }

  private Map<String, Object> getTokenResult(UserSession session) {
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("accessToken", session.getAttribute("accessToken"));
    data.put("refreshToken", session.getAttribute("refreshToken"));
    return data;
  }

  public static class LdapLoginRequest {
    @Schema(description = "LDAP 用户名")
    private String username;
    @Schema(description = "LDAP 用户密码")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
  }
}
