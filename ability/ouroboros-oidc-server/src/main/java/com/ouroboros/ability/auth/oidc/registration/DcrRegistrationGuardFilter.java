package com.ouroboros.ability.auth.oidc.registration;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** RFC 7592 is deliberately unavailable; unsupported methods must not enter a login redirect or default provider. */
public final class DcrRegistrationGuardFilter extends OncePerRequestFilter {
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    return !DcrRegistrationEndpoint.PATH.equals(request.getRequestURI().substring(request.getContextPath().length())) || "POST".equals(request.getMethod());
  }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setStatus(405); response.setHeader("Allow", "POST"); response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/json"); response.getWriter().write("{\"error\":\"unsupported_method\"}");
  }
}
