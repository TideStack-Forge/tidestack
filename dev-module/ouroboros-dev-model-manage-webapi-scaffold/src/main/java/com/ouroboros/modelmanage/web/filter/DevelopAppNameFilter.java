package com.ouroboros.modelmanage.web.filter;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import com.ouroboros.dev.DevAppHolder;

/**
 * @author liansz
 */
@WebFilter(urlPatterns = {"/api/dev/*", "/api/app/*"}, asyncSupported = true)
public class DevelopAppNameFilter implements Filter {
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    Filter.super.init(filterConfig);
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
    String requestURI = httpServletRequest.getRequestURI();

    boolean idDevelop = requestURI.matches("/api/(dev|app)/.+?/.+");

    try {
      if (idDevelop) {
        String appName = requestURI.split("/")[3];
        DevAppHolder.setAppName(appName);
      }

      filterChain.doFilter(servletRequest, servletResponse);
    } finally {
      if (idDevelop) {
        DevAppHolder.removeAppName();
      }
    }
  }

  @Override
  public void destroy() {
    Filter.super.destroy();
  }
}
