package com.ouroboros.security.authentication.filter;


import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.authentication.AuthenticationFilterSupplier;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;

/**
 * 匿名身份认证过滤器
 * <p>
 * 该过滤器会在所有的过滤器之前执行，用于匿名身份的认证
 * 该过滤器会将所有的请求都认证为匿名身份
 * 后续的过滤器可以通过比安全上下文获取大到身份验证对象对比类型，识别是否为匿名身份
 * 后续的过滤器监测到匿名身份验证可以进行覆盖
 *
 * @author liansz
 */
@Component
@Order(-1000)
public class AnonymousAuthenticationFilterSupplier implements AuthenticationFilterSupplier {

  @Override
  public AuthenticationFilter getAuthenticationFilter() {
    return new AuthenticationFilter() {
      @Override
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        try {
          AuthorizationDecisionContextHolder.withCurrentRequestThrowing(
              httpServletRequest.getMethod(),
              httpServletRequest.getRequestURI(),
              () -> {
                // 设置匿名身份
                SecurityContextHolder.getContext().setAuthentication(
                    OuroborosAuthenticationHolder.toSpringAuthentication(AnonymousAuthentication.getInstance()));
                filterChain.doFilter(servletRequest, servletResponse);
                return null;
              });
        } catch (IOException | ServletException e) {
          throw e;
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new IllegalStateException("checked anonymous decision-boundary execution produced unexpected exception", e);
        }
      }
    };
  }
}
