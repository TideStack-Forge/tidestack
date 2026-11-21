package com.ouroboros.dev.app.security;

import java.util.Collections;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.service.runtime.DebugProfileAuthorizationService;
import com.ouroboros.dev.app.service.runtime.DevKeyStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.authentication.AuthenticationFilterSupplier;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.rbac.UserCenter;

/** Authenticates development keys only for editor APIs; the normal authorization chain still applies. */
@Component
@Order(-700)
public final class DevKeyEditorAuthenticationFilterSupplier implements AuthenticationFilterSupplier {
  public static final String HEADER = "X-TideStack-Dev-Key";
  private static final Pattern ROUTE = Pattern.compile("/api/(dev|app)/([A-Za-z0-9_.-]+)/(.*)");
  private static final Pattern UNSAFE_PATH = Pattern.compile("[\\\\#;]|%2e|%2f|%5c|%25|(?:^|/)\\.{1,2}(?:/|$)", Pattern.CASE_INSENSITIVE);
  private final DebugProfileAuthorizationService authorization;
  private final DevKeyStore keys;
  private final UserCenter users;

  public DevKeyEditorAuthenticationFilterSupplier(DebugProfileAuthorizationService authorization, DevKeyStore keys, UserCenter users) {
    this.authorization = authorization;
    this.keys = keys;
    this.users = users;
  }

  @Override
  public AuthenticationFilter getAuthenticationFilter() {
    return (request, response, chain) -> {
      var http = (HttpServletRequest) request;
      if (http.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR) {
        chain.doFilter(request, response);
        return;
      }
      var headers = Collections.list(http.getHeaders(HEADER));
      if (headers.isEmpty()) {
        chain.doFilter(request, response);
        return;
      }
      var output = (HttpServletResponse) response;
      if (headers.size() != 1 || headers.getFirst().isBlank() || http.getHeader("Authorization") != null) {
        output.setStatus(401);
        return;
      }
      var path = http.getRequestURI().substring(http.getContextPath().length());
      var route = ROUTE.matcher(path);
      if (UNSAFE_PATH.matcher(path).find() || !route.matches() || !allows(route.group(1), route.group(3), http.getMethod())) {
        output.setStatus(403);
        return;
      }
      var appName = route.group(2);
      var key = headers.getFirst().trim();
      var result = authorization.authorize(appName, key);
      if (result.isFailure()) {
        if (result.getCause() instanceof BusinessException) {
          output.setStatus(401);
          return;
        }
        result.get(); // Storage and infrastructure failures must propagate.
      }
      if (!Boolean.TRUE.equals(result.get().getAuthorized())) {
        output.setStatus(401);
        return;
      }
      var material = keys.findByDevKey(appName, key);
      var user = material == null ? java.util.Optional.<com.ouroboros.security.rbac.User>empty()
          : users.getUser(material.getUserIdentity()).filter(com.ouroboros.security.rbac.User::isEnabled);
      if (user.isEmpty()) {
        output.setStatus(401);
        return;
      }
      var previous = SecurityContextHolder.getContext();
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(OuroborosAuthenticationHolder.toSpringAuthentication(new DefaultAuthentication(user.get())));
      try {
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
      } finally {
        SecurityContextHolder.setContext(previous);
      }
    };
  }

  private static boolean allows(String namespace, String route, String method) {
    if (namespace.equals("app")) {
      return method.equals("GET") && route.matches("field-types/(primary|normal|[^/]+/ui-schema)");
    }
    return switch (method) {
      case "GET" -> route.matches("code-intelligence/(environment|sessions/[A-Za-z0-9-]+/notifications)|logicflow/node-define|approval-workflow/(candidate(/[^/]+/(items|category))?|form-fields)");
      case "POST" -> route.matches("code-intelligence/(context|sessions|sessions/[A-Za-z0-9-]+/(messages|context))");
      case "DELETE" -> route.matches("code-intelligence/sessions/[A-Za-z0-9-]+");
      default -> false;
    };
  }
}
