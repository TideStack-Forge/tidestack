package com.ouroboros.ability.auth.oidc;

import com.ouroboros.ability.auth.oidc.service.CacheOAuth2AuthorizationService;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ouroboros.lock.DistributedLockManager;

/** Serializes redemption of the same code or refresh token across platform instances. */
final class OidcTokenExchangeLockFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = request.getParameter("code");
    if (token == null) { token = request.getParameter("refresh_token"); }
    if (!"POST".equals(request.getMethod()) || !request.getRequestURI().equals(request.getContextPath() + "/oauth2/token") || token == null) {
      chain.doFilter(request, response);
      return;
    }
    var lock = DistributedLockManager.getLockManager().getLock("oidc:redeem:" + CacheOAuth2AuthorizationService.tokenKey(token));
    lock.lock();
    try {
      chain.doFilter(request, response);
    } finally {
      lock.unlock();
    }
  }
}
