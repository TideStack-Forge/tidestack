package com.ouroboros.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

import com.ouroboros.security.authentication.AuthenticationFilterSupplier;
import com.ouroboros.security.authorize.AuthorizeFilterSupplier;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;

/**
 * @author liansz
 */
@Configuration
@EnableWebSecurity
@ComponentScan
public class OuroborosSecurityAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(OuroborosSecurityAutoConfiguration.class);

  @Autowired
  private AccessDeniedHandler accessDeniedHandler;

  @Autowired
  private AuthenticationEntryPoint authenticationEntryPoint;

  @Autowired(required = false)
  private List<AuthorizeFilterSupplier> authorizeFilterSuppliers;

  @Autowired(required = false)
  private List<AuthenticationFilterSupplier> authenticationFilterSuppliers;

  @PostConstruct
  public void init() {
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    logger.info("Ouroboros Security Auto Configuration loaded");
  }

  @Bean
  public SecurityFilterChain getSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        // 禁用默认 headers
        .headers().disable()

        // 禁用默认 csrf
        .csrf().disable()

        // 禁用默认 formLogin
        .formLogin().disable()

        // 禁用默认 logout
        .logout().disable()

        // 禁用默认 requestCache
        .requestCache().disable()

        // 禁用默认 anonymous
        .anonymous().disable()

        // 禁用默认 sessionManagement
//        .sessionManagement().disable()
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()

        // 禁用默认 SecurityContextHolderAwareRequestFilter
        .servletApi().disable()

        // 异常处理
        .exceptionHandling()
        .accessDeniedHandler(accessDeniedHandler)
        .authenticationEntryPoint(authenticationEntryPoint);
    var lastFilter = new AtomicReference<Class<? extends Filter>>(WebAsyncManagerIntegrationFilter.class);
    authenticationFilterSuppliers.stream()
        .map(AuthenticationFilterSupplier::getAuthenticationFilter)
        .forEach(f -> {
          http.addFilterAfter(f, LogoutFilter.class);
          lastFilter.set(f.getClass());
        });

    authorizeFilterSuppliers.stream()
        .map(AuthorizeFilterSupplier::getAuthorizeFilter)
        .forEach(f -> {
          http.addFilterAfter(f, ExceptionTranslationFilter.class);
          lastFilter.set(f.getClass());
        });

    return http.build();
  }
}
