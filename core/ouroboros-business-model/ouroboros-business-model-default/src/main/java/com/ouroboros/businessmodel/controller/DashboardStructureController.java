package com.ouroboros.businessmodel.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.DashboardBusinessModel;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@RestController
public class DashboardStructureController {
  public static final String HANDLER_METHOD_DESCRIPTOR =
      "getDashboard(jakarta.servlet.http.HttpServletRequest)";

  public WebApiResult<DashboardBusinessModelMeta> getDashboard(HttpServletRequest request) {
    return WebApiResult.success(loadDashboard(getDashboardPath(request)).getMeta());
  }

  private DashboardBusinessModel loadDashboard(String dashboardPath) {
    return BusinessModelCenter.getBusinessModelMap().values().stream()
        .filter(DashboardBusinessModel.class::isInstance)
        .map(DashboardBusinessModel.class::cast)
        .filter(model -> Boolean.TRUE.equals(model.isEnabled()))
        .filter(DashboardBusinessModel::isValid)
        .filter(model -> BusinessModelUtil.buildKebabCasePath(model).equals(dashboardPath))
        .findFirst()
        .orElseThrow(() -> new BusinessException(
            "dashboard business model not found: " + dashboardPath));
  }

  private String getDashboardPath(HttpServletRequest request) {
    String requestPath = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
      requestPath = requestPath.substring(contextPath.length());
    }
    if (!requestPath.startsWith("/api/")) {
      throw new BusinessException("dashboard API path is invalid: " + requestPath);
    }
    return requestPath.substring("/api".length());
  }
}
