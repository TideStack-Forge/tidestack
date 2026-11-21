package com.ouroboros.security.authorize.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.Filter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;
import com.ouroboros.security.authorize.AuthorizeFilterSupplier;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.UnauthenticatedException;
import com.ouroboros.util.Paths;

/**
 * 权限校验过滤器
 * <p>
 * 用于校验当前用户是否具有当前请求的权限
 * 并将角色权限信息放入认证信息中
 *
 * @author liansz
 */
@Component
@Order(-1000)
public class AuthorizeCheckFilterSupplier implements AuthorizeFilterSupplier {

  @Autowired
  private ServletContext servletContext;

  @Autowired(required = false)
  private List<AnonymousApiProvider> anonymousApiProviders;

  @Autowired(required = false)
  private List<AnyAuthenticationApiProvider> anyAuthenticationApiProviders;

  private List<RequestMatcher> anonymousApiRequestMatcherList;
  private List<RequestMatcher> anyAuthenticationApiRequestMatcherList;

  @EventListener(OuroborosContextRefreshedEvent.class)
  @Order(100)
  @SuppressWarnings("unused")
  void init() {
    anonymousApiRequestMatcherList = buildAnonymousApiRequestMatchers();
    anyAuthenticationApiRequestMatcherList = buildAnyAuthenticationApiRequestMatchers();
  }

  private List<RequestMatcher> buildAnonymousApiRequestMatchers() {
    if (anonymousApiProviders == null) {
      return Collections.emptyList();
    }
    return anonymousApiProviders.stream()
        .map(AnonymousApiProvider::getAnonymousApis)
        .flatMap(Collection::stream)
        .distinct()
        .map(this::buildApiRequestMatcher)
        .collect(Collectors.toList());
  }

  private List<RequestMatcher> buildAnyAuthenticationApiRequestMatchers() {
    if (anyAuthenticationApiProviders == null) {
      return Collections.emptyList();
    }
    return anyAuthenticationApiProviders.stream()
        .map(AnyAuthenticationApiProvider::getAnyAuthenticationApis)
        .flatMap(Collection::stream)
        .distinct()
        .map(this::buildApiRequestMatcher)
        .collect(Collectors.toList());
  }

  /** Shared by authentication and authorization so public routes have one matching policy. */
  public boolean isAnonymousRequest(HttpServletRequest request) {
    var matchers = anonymousApiRequestMatcherList == null
        ? buildAnonymousApiRequestMatchers() : anonymousApiRequestMatcherList;
    return matchers.stream().anyMatch(matcher -> matcher.matches(request));
  }

  @Override
  public Filter getAuthorizeFilter() {
    return (request, response, chain) -> {
      var httpServletRequest = (HttpServletRequest) request;
      if (httpServletRequest.getDispatcherType() == DispatcherType.ERROR) {
        chain.doFilter(request, response);
        return;
      }
      try {
        AuthorizationDecisionContextHolder.withCurrentRequestThrowing(
            httpServletRequest.getMethod(),
            httpServletRequest.getRequestURI(),
            () -> {
              var requestURI = httpServletRequest.getRequestURI();

              // 检查是否为匿名访问接口
              if (isAnonymousRequest(httpServletRequest)) {
                chain.doFilter(request, response);
                return null;
              }
              var authentication = CurrentSecurityContextHolder.findAuthentication()
                  .orElse(AnonymousAuthentication.getInstance());
              if (!authentication.isAuthenticated()) {
                throw new UnauthenticatedException();
              }

              var identity = authentication.getIdentity();
              if (identity == null) {
                throw new UnauthenticatedException();
              }

              // 检查是否为经过身份验证免授权访问接口
              List<RequestMatcher> anyAuthenticationRequestMatchers =
                  anyAuthenticationApiRequestMatcherList == null ? buildAnyAuthenticationApiRequestMatchers() : anyAuthenticationApiRequestMatcherList;
              if (identity.getCredentialContext().kind()
                  == com.ouroboros.security.identity.CredentialContext.Kind.PLATFORM
                  && anyAuthenticationRequestMatchers.stream().anyMatch(requestMatcher -> requestMatcher.matches(httpServletRequest))) {
                chain.doFilter(request, response);
                return null;
              }

              // 检查当前用户是否具有当前请求的权限
              var names = com.ouroboros.security.authority.AuthorityCenter.getAllAuthority().values().stream()
                  .filter(authority -> authority.getApis().stream().anyMatch(api -> {
                    var method = api.substring(0, api.indexOf(' '));
                    var url = api.substring(api.indexOf(' ') + 1);
                    return httpServletRequest.getMethod().equalsIgnoreCase(method)
                        && new PathPatternParser().parse(url).matches(PathContainer.parsePath(requestURI));
                  }))
                  .map(authority -> authority.getFullName()).toList();
              if (com.ouroboros.security.authorize.AuthorizationEvaluator.standard().permitsRequest(identity,
                  com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API, names)) {
                chain.doFilter(request, response);
                return null;
              }

              throw new AccessDeniedException(String.format("当前请求的接口 %s 未授权", requestURI));

            });
      } catch (IOException | ServletException e) {
        throw e;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new IllegalStateException("checked decision-boundary execution produced unexpected exception", e);
      }
    };
  }

  /**
   * 构建Api请求匹配器
   *
   * @param api
   * @return
   */
  private RequestMatcher buildApiRequestMatcher(String api) {
    int sp = api.indexOf(' ');
    String method = (sp > 0) ? api.substring(0, sp) : "*";
    String pattern = api.substring(sp + 1);

    // PathPattern 不支持中间 ** 或 {*var} 后再跟内容；这类情况走 AntPathMatcher/正则
    boolean needsAntMiddleWildcard =
        pattern.contains("/**/") || (pattern.contains("{*") && !pattern.endsWith("}"));

    if (needsAntMiddleWildcard) {
      AntPathMatcher ant = new AntPathMatcher();
      return request -> methodMatches(request, method)
          && ant.match(pattern, pathWithinApplication(request));
    }

    // 其余情况仍然走 PathPattern（保留你原有的写法）
    String url = Paths.Url.of(servletContext.getContextPath(), pattern).toString();
    var parsed = new PathPatternParser().parse(url);

    return request -> methodMatches(request, method)
        && parsed.matches(PathContainer.parsePath(request.getRequestURI()));
  }

  private boolean methodMatches(HttpServletRequest request, String method) {
    return "*".equals(method) || request.getMethod().equalsIgnoreCase(method);
  }

  private String pathWithinApplication(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String ctx = request.getContextPath();
    return (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
  }

}
