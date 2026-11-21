package com.ouroboros.ability.auth.oidc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/** Reserved protocol paths always fail closed when deployment identity is absent. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OidcUnavailableSecurityConfiguration {
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 20)
  public SecurityFilterChain oidcUnavailableFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/.well-known/openid-configuration", "/.well-known/oauth-authorization-server", "/oauth2/**", "/connect/**", "/userinfo", "/api/auth/oidc/**")
        .addFilterBefore((request, response, chain) -> {
          var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
          httpResponse.setStatus(503); httpResponse.setContentType("application/json");
          httpResponse.setHeader("Cache-Control", "no-store");
          httpResponse.getWriter().write("{\"error\":\"temporarily_unavailable\"}");
        }, SecurityContextHolderFilter.class)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll()).build();
  }
}
