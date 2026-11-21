package com.ouroboros.ability.auth.oidc;

import java.io.IOException;
import java.time.LocalDateTime;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.session.UserSessionManager;

final class OidcPlatformSessionFilter extends OncePerRequestFilter {
  static final String PLATFORM_SESSION = "ouroboros.oidc.platformSession";
  private final UserSessionManager<?> sessions;
  private final UserCenter users;

  OidcPlatformSessionFilter(UserSessionManager<?> sessions, UserCenter users) {
    this.sessions = sessions;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var httpSession = request.getSession(false);
    if (httpSession != null && httpSession.getAttribute(PLATFORM_SESSION) instanceof String sessionId) {
      var principal = SecurityContextHolder.getContext().getAuthentication();
      boolean valid = principal != null && sessions.getSession(sessionId)
          .filter(session -> session.getUser().equals(principal.getName()))
          .filter(session -> session.getExpiresAt() != null && session.getExpiresAt().isAfter(LocalDateTime.now())).isPresent()
          && users.getUser(principal.getName()).filter(User::isEnabled).isPresent();
      if (!valid) {
        SecurityContextHolder.clearContext();
        httpSession.invalidate();
      }
    }
    chain.doFilter(request, response);
  }
}
