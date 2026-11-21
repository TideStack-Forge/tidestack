package com.ouroboros.security.authorize;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@Component
public class AuthorizeExceptionHandlerImpl implements AccessDeniedHandler, AuthenticationEntryPoint {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * 在抛出AuthenticationException异常时，会调用该方法
   * 认证失败后请求该类
   *
   * @param request       that resulted in an <code>AuthenticationException</code>
   * @param response      so that the user agent can begin authentication
   * @param authException that caused the invocation
   * @throws IOException
   */
  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
    logger.warn("认证失败：{}", authException.getMessage());

    writeResponse(response, HttpStatus.UNAUTHORIZED.value(), "无法访问（未认证）");
  }

  /**
   * 在抛出AccessDeniedException异常时，会调用该方法
   * 认证成功了，但是无权访问接口，调用该类
   *
   * @param request               that resulted in an <code>AccessDeniedException</code>
   * @param response              so that the user agent can be advised of the failure
   * @param accessDeniedException that caused the invocation
   * @throws IOException
   * @throws ServletException
   */
  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
    logger.warn("未授权访问：{}", accessDeniedException.getMessage());

    writeResponse(response, HttpStatus.FORBIDDEN.value(), "无法访问（未授权）");
  }

  private void writeResponse(HttpServletResponse response, int code, String message) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json;charset=UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().print(Json.toJsonString(WebApiResult.error(message)));
  }
}
