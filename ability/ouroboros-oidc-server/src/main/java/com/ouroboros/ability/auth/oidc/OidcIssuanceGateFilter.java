package com.ouroboros.ability.auth.oidc;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.UserCenter;

/** Pause issuance while retaining discovery, verification and cleanup endpoints. */
public final class OidcIssuanceGateFilter extends OncePerRequestFilter {
  private final ConfigurationService configuration;
  private final UserCenter users;
  public OidcIssuanceGateFilter(ConfigurationService configuration, UserCenter users) {
    this.configuration = configuration;
    this.users = users;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !(path.equals("/oauth2/authorize") || path.equals("/oauth2/token") || path.equals("/connect/register") || path.startsWith("/api/auth/oidc/"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    boolean enabled;
    try { enabled = OidcRuntimeSettings.current(configuration).enabled(); }
    catch (OuroborosException error) { enabled = false; }
    if (!enabled || !users.publicSubjectsReady()) {
      response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      response.setHeader("Cache-Control", "no-store");
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"temporarily_unavailable\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
