package com.ouroboros.security.authentication.filter;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.authentication.AuthenticationFilterSupplier;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;

@Component
@Order(-800)
public class TokenAuthenticationFilterSupplier implements AuthenticationFilterSupplier {

  @Autowired
  private TokenService tokenService;

  @Autowired
  private UserCenter userCenter;

  @Autowired
  private AuthorizeCheckFilterSupplier authorizeCheckFilterSupplier;

  @Autowired(required = false)
  private java.util.List<com.ouroboros.security.token.TokenIdentityResolver> tokenIdentityResolvers = java.util.List.of();

  @Override
  public AuthenticationFilter getAuthenticationFilter() {
    RequestMatcher requestMatcher = new RequestHeaderRequestMatcher("Authorization");
    return new AbstractAuthenticationFilter(requestMatcher) {
      @Override
      protected boolean rejectInvalidAuthentication() { return true; }

      @Override
      protected boolean requiresValidAuthentication(HttpServletRequest request) {
        return !authorizeCheckFilterSupplier.isAnonymousRequest(request);
      }

      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!header.startsWith("Bearer ") || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) {
          return Optional.empty();
        }
        String token = header.substring(7);
        Optional<OuroborosAuthentication> platform = tokenService.verifyAccessToken(token)
            .toJavaOptional()
            .flatMap(tokenInfo -> userCenter.getUser(tokenInfo.getUserIdentity())
                .filter(com.ouroboros.security.rbac.User::isEnabled)
                .map(user -> DefaultAuthentication.platformToken(user, tokenInfo.getSessionId())));
        if (platform.isPresent()) { return platform; }
        return tokenIdentityResolvers.stream().map(resolver -> resolver.resolve(token)).flatMap(Optional::stream)
            .findFirst().map(DefaultAuthentication::new);
      }
    };
  }
}
