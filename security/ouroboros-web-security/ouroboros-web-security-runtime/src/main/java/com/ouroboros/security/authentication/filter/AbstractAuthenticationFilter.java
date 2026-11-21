package com.ouroboros.security.authentication.filter;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.exception.TokenException;

/**
 * @author liansz
 */
public abstract class AbstractAuthenticationFilter implements AuthenticationFilter {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * 请求匹配器
   */
  private final RequestMatcher requestMatcher;

  protected AbstractAuthenticationFilter(RequestMatcher requestMatcher) {
    this.requestMatcher = requestMatcher;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    if (httpServletRequest.getDispatcherType() == DispatcherType.ERROR) {
      chain.doFilter(request, response);
      return;
    }

    try {
      AuthorizationDecisionContextHolder.withCurrentRequest(
          httpServletRequest.getMethod(),
          httpServletRequest.getRequestURI(),
          () -> {
            // 是否处理
            if (isHandle(httpServletRequest)) {
              // TODO: 此处暂时为宽松模式，一个请求可能存在多种认证方式，那么只要有一个认证成功，就会认为是认证成功的

              try {
                // 身份验证
                var verified = authentication(httpServletRequest);
                if (verified.isEmpty() && rejectInvalidAuthentication()) {
                  SecurityContextHolder.clearContext();
                  if (requiresValidAuthentication(httpServletRequest)) {
                    ((jakarta.servlet.http.HttpServletResponse) response).setStatus(401);
                    return null;
                  }
                }
                verified.ifPresent(authentication -> {
                  // 设置认证信息
                  SecurityContextHolder.getContext().setAuthentication(OuroborosAuthenticationHolder.toSpringAuthentication(authentication));
                });
              } catch (TokenException e) {
                if (rejectInvalidAuthentication()) {
                  SecurityContextHolder.clearContext();
                  if (requiresValidAuthentication(httpServletRequest)) {
                    ((jakarta.servlet.http.HttpServletResponse) response).setStatus(401);
                    return null;
                  }
                }
                logger.debug("身份认证未通过，继续尝试其它认证方式", e);
              }
            }

            try {
              chain.doFilter(request, response);
            } catch (IOException e) {
              throw new WrappedIOException(e);
            } catch (ServletException e) {
              throw new WrappedServletException(e);
            }
            return null;
          });
    } catch (WrappedIOException e) {
      throw e.getCause();
    } catch (WrappedServletException e) {
      throw e.getCause();
    }
  }

  private static final class WrappedIOException extends RuntimeException {
    private WrappedIOException(IOException cause) {
      super(cause);
    }

    @Override
    public synchronized IOException getCause() {
      return (IOException) super.getCause();
    }
  }

  private static final class WrappedServletException extends RuntimeException {
    private WrappedServletException(ServletException cause) {
      super(cause);
    }

    @Override
    public synchronized ServletException getCause() {
      return (ServletException) super.getCause();
    }
  }

  /**
   * 是否处理
   *
   * @param request
   * @return
   */
  protected boolean isHandle(HttpServletRequest request) {
    if (rejectInvalidAuthentication()) { return requestMatcher.matches(request); }
    // 获取认证信息
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return requestMatcher.matches(request);
    }

    return OuroborosAuthenticationHolder.adapt(authentication) instanceof AnonymousAuthentication
        && requestMatcher.matches(request);
  }


  /**
   * 身份验证
   *
   * @param request
   * @return
   */
  protected abstract Optional<OuroborosAuthentication> authentication(HttpServletRequest request) throws SecurityException;

  protected boolean rejectInvalidAuthentication() { return false; }

  /** Optional authentication still discards invalid credentials but permits anonymous access. */
  protected boolean requiresValidAuthentication(HttpServletRequest request) { return true; }
}
