package com.ouroboros.security.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.config.AuthLoginConfig;
import com.ouroboros.security.controller.exception.AuthenticationException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.exception.InvalidTokenException;
import com.ouroboros.security.exception.SessionException;
import com.ouroboros.security.exception.TokenException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserIdResolver;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.session.client.ClientTypeHolder;
import com.ouroboros.security.session.UserSessionManager;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@ControllerAdvice
@RequestMapping("/api/auth")
public class AuthenticationController {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private UserIdResolver userIdResolver;

  @Autowired
  private UserPasswordVerifier userPasswordVerifier;

  @Autowired
  private UserCenter userCenter;

  @Autowired
  private TokenService tokenService;

  @Autowired
  private UserSessionManager<? extends UserSession> userSessionManager;

  @Autowired
  private AuthLoginConfig authLoginConfig;

  @Anonymous
  @Operation(
      summary = "账号密码登录",
      description = "使用本地账号、身份类型和密码完成登录，并返回访问令牌与刷新令牌。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "登录成功并返回访问令牌"),
      @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
      @ApiResponse(responseCode = "403", description = "本地账号密码登录已禁用")
  })
  @PostMapping("/login")
  public WebApiResult<Map<String, Object>> login(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "本地账号密码登录请求", required = true)
      @RequestBody LoginDTO request) {
    if (!isLocalPasswordLoginEnabled()) {
      return WebApiResult.error(40301, "本地账号密码登录已禁用");
    }

    Optional<User> user = userIdResolver
        .getUserId(request.getPrincipalType(), request.getPrincipal())
        .map(userId -> userPasswordVerifier.verify(userId, request.getCredentials()) ? userId : null)
        .flatMap(userCenter::getUser);

    return user
        .map(this::getUserLoginResult)
        .orElse(WebApiResult.error("用户名或密码错误"));
  }

  protected boolean isLocalPasswordLoginEnabled() {
    return authLoginConfig == null || authLoginConfig.isLocalPasswordEnabled();
  }

  private Map<String, Object> getTokenResult(UserSession userSession) {
    Map<String, Object> data = new HashMap<>();
    data.put("accessToken", userSession.getAttribute("accessToken"));
    data.put("refreshToken", userSession.getAttribute("refreshToken"));
    return data;
  }

  private WebApiResult<Map<String, Object>> getUserLoginResult(User user) {
    String clientType = ClientTypeHolder.getClientType();
    return userSessionManager.createSession(user, clientType)
        .map(this::getTokenResult)
        .map(WebApiResult::ok)
        .getOrElseGet(e -> {
          logger.warn("创建登录会话失败", e);
          return WebApiResult.error("无法创建登录会话，请检查会话数量限制或联系管理员");
        });
  }

  @Anonymous
  @Operation(
      summary = "刷新访问令牌",
      description = "使用刷新令牌换取新的访问令牌和刷新令牌。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "刷新成功并返回新的令牌"),
      @ApiResponse(responseCode = "401", description = "刷新令牌无效或已过期")
  })
  @PostMapping("/refresh-token/{refreshToken}")
  public WebApiResult<Map<String, Object>> refreshToken(
      @Parameter(description = "用于换取新令牌的刷新令牌", required = true)
      @PathVariable("refreshToken") String refreshToken) {
    return tokenService.verifyRefreshToken(refreshToken)
        .mapLeft(e -> (Throwable) e)
        .flatMap(tokenInfo -> {
          String sessionId = tokenInfo.getSessionId();
          if (sessionId == null) {
            return io.vavr.control.Either.left(new InvalidTokenException("令牌无效：格式错误"));
          }
          return userSessionManager.refreshSession(sessionId).toEither();
        })
        .map(this::getTokenResult)
        .map(WebApiResult::ok)
        .getOrElseGet(e -> {
          if (e instanceof TokenException || e instanceof SessionException) {
            logger.debug("刷新令牌无效或会话已失效", e);
            return WebApiResult.error(401, "登录已过期，请重新登录");
          }
          throw OuroborosExceptionMapper.toOuroborosException(e);
        });
  }

  @PermitAll
  @Operation(
      summary = "撤销访问令牌",
      description = "根据 Authorization 请求头中的 Bearer access token 销毁对应会话。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "令牌已撤销"),
      @ApiResponse(responseCode = "401", description = "访问令牌无效")
  })
  @PostMapping("/revoke-token")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeToken(
      @Parameter(description = "Bearer 访问令牌，格式为 Bearer <token>", required = true)
      @RequestHeader("Authorization") String accessToken) {
    accessToken = accessToken.replaceAll("^Bearer ", "");
    tokenService.verifyAccessToken(accessToken)
        .peek(tokenInfo -> {
          String sessionId = tokenInfo.getSessionId();
          if (sessionId != null) {
            userSessionManager.destroySession(sessionId);
          }
        });
  }

  @PermitAll
  @Operation(
      summary = "获取当前用户权限列表",
      description = "返回当前认证用户持有的权限集合。",
      tags = {"authentication"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回当前用户权限列表")
  })
  @GetMapping("/authorities")
  public WebApiResult<Map<String, OuroborosGrantedAuthority>> getUserAuthorities() {
    Map<String, OuroborosGrantedAuthority> authorities = new HashMap<>();
    CurrentSecurityContextHolder.findAuthentication().ifPresent(authentication ->
        authentication.getAuthorities().forEach(authority -> authorities.put(authority.getAuthority(), authority))
    );
    return WebApiResult.ok(authorities);
  }

  @ResponseBody
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus()
  public void exceptionHandler(HttpServletResponse response, AuthenticationException e) throws IOException {
    logger.error("身份验证失败: ", e);
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().print(Json.toJsonString(WebApiResult.error("身份验证失败")));
  }

  public static class LoginDTO {
    @Schema(description = "登录身份类型，例如 username、email、mobile")
    @JsonAlias({"type"})
    private String principalType;
    @Schema(description = "登录主体，例如用户名、邮箱或手机号")
    @JsonAlias({"account", "username", "mobile", "email", "user"})
    private String principal;
    @Schema(description = "登录凭据，通常为密码")
    @JsonAlias({"credential", "password"})
    private String credentials;

    public String getPrincipalType() { return principalType; }
    public String getPrincipal() { return principal; }
    public String getCredentials() { return credentials; }
  }
}
