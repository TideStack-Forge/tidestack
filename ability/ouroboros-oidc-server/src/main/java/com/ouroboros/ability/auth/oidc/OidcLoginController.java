package com.ouroboros.ability.auth.oidc;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.security.session.UserSessionManager;
import com.ouroboros.web.WebApiResult;

@RestController
@org.springframework.context.annotation.Conditional(OidcDeploymentCondition.class)
public class OidcLoginController {
  private final TokenService tokens;
  private final UserCenter users;
  private final UserSessionManager<?> sessions;
  private final OidcRequestCache requestCache = new OidcRequestCache();

  public OidcLoginController(TokenService tokens, UserCenter users, UserSessionManager<?> sessions) {
    this.tokens = tokens;
    this.users = users;
    this.sessions = sessions;
  }

  @GetMapping("/api/auth/oidc/login-context")
  public WebApiResult<Map<String, String>> loginContext(HttpServletRequest request, HttpServletResponse response) {
    requireSavedAuthorization(request, response);
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    return WebApiResult.ok(Map.of("csrfToken", csrf.getToken(), "csrfHeader", csrf.getHeaderName()));
  }

  @PostMapping("/api/auth/oidc/session")
  public WebApiResult<Map<String, String>> login(@RequestHeader("Authorization") String bearer,
      HttpServletRequest request, HttpServletResponse response) {
    String redirectUrl = requireSavedAuthorization(request, response);
    if (!bearer.startsWith("Bearer ")) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    var token = tokens.verifyAccessToken(bearer.substring(7)).toJavaOptional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    User user = users.getUser(token.getUserIdentity()).filter(User::isEnabled)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    String sessionId = token.getSessionId();
    if (sessionId == null || sessions.getSession(sessionId)
        .filter(session -> session.getUser().equals(user.getUserIdentity()))
        .filter(session -> session.getExpiresAt() != null
            && session.getExpiresAt().isAfter(java.time.LocalDateTime.now())).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    request.changeSessionId();
    var authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUserIdentity(), null, List.of());
    authentication.setDetails(sessionId);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    request.getSession().setAttribute(OidcPlatformSessionFilter.PLATFORM_SESSION, sessionId);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    requestCache.removeRequest(request, response);
    return WebApiResult.ok(Map.of("redirectUrl", redirectUrl));
  }

  private String requireSavedAuthorization(HttpServletRequest request, HttpServletResponse response) {
    var saved = requestCache.getRequest(request, response);
    if (!(saved instanceof org.springframework.security.web.savedrequest.DefaultSavedRequest authorization)
        || !authorization.getMethod().equals("GET")
        || !authorization.getRequestURI().equals(request.getContextPath() + "/oauth2/authorize")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有待完成的 OIDC 授权请求");
    }
    // Return only the path/query from our own saved request, never a caller-supplied redirect target.
    return authorization.getRequestURI() + (authorization.getQueryString() == null ? "" : "?" + authorization.getQueryString());
  }
}
