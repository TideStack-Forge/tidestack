package com.ouroboros.organizationstructure.context;

import java.io.IOException;

import jakarta.servlet.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

/**
 * 组织架构上下文初始化过滤器提供者
 * <p>
 * 用于初始化组织架构上下文（员工、部门、公司）
 *
 * @author liansz
 */
@Component
@Order(100)
public class OrganizationContextInitializerFilter implements Filter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    try {
      CurrentSecurityContextHolder.findIdentity()
          .map(identity -> identity.getUserIdentity())
          .map(userIdentity -> new LazyLoadOrganizationContext(
              userIdentity,
              () -> employeeAggregator.getByUser(userIdentity).orElse(null)
          )).ifPresent(OrganizationContextHolder::setContext);
    } catch (Exception e) {
      logger.error("Build organization context exception : {}", e.getMessage());
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      OrganizationContextHolder.clear();
    }
  }
}
