package com.ouroboros.businessmodel.controller;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.DashboardBusinessModel;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.RequestMappingIntegrationCenter;
import com.ouroboros.webapi.WebAPIModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardStructureControllerTest {

  @Test
  void shouldReturnOnlyDashboardBusinessModelStructureAtProjectedPath() throws Exception {
    DashboardBusinessModel dashboard = dashboard();
    Field businessModelMap = BusinessModelCenter.class.getDeclaredField("businessModelMap");
    businessModelMap.setAccessible(true);
    Object previous = businessModelMap.get(null);
    try {
      businessModelMap.set(null, Collections.<String, BusinessModel>singletonMap(
          dashboard.getFullName(), dashboard));
      MockHttpServletRequest request = new MockHttpServletRequest(
          "GET", "/ouroboros/api/demo/sales-dashboard");
      request.setContextPath("/ouroboros");

      WebApiResult<DashboardBusinessModelMeta> response =
          new DashboardStructureController().getDashboard(request);

      assertEquals(0, response.getCode());
      assertEquals("demo.salesDashboard", response.getData().getFullName());
      assertEquals("demo.salesChart", response.getData().getWidgets().get(0).getChartName());
      com.fasterxml.jackson.databind.JsonNode serialized =
          new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response.getData());
      assertFalse(serialized.path("widgets").get(0).has("data"));
    } finally {
      businessModelMap.set(null, previous);
    }
  }

  @Test
  void shouldRegisterOnlyTheBusinessModelProjectedApiPath() throws Exception {
    DashboardBusinessModel dashboard = dashboard();
    WebAPIModel webAPI = dashboard.buildModelBundle()
        .getModel(WebAPIModel.class, "GET /api/demo/sales-dashboard")
        .orElseThrow();

    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestConfiguration.class);
    try {
      context.refresh();
      RequestMappingIntegrationCenter integrationCenter =
          context.getBean(RequestMappingIntegrationCenter.class);
      integrationCenter.register(Collections.singletonList(webAPI));

      MockHttpServletRequest dashboardRequest = new MockHttpServletRequest(
          context.getServletContext(), "GET", "/api/demo/sales-dashboard");
      var handlerChain = integrationCenter.getHandler(dashboardRequest);
      assertNotNull(handlerChain);
      Object handler = handlerChain.getHandler();

      assertInstanceOf(HandlerMethod.class, handler);
      assertEquals(
          DashboardStructureController.class,
          ((HandlerMethod) handler).getBeanType());
      MockHttpServletRequest unrelatedRequest = new MockHttpServletRequest(
          context.getServletContext(), "GET", "/api/unrelated");
      assertNull(integrationCenter.getHandler(unrelatedRequest));
    } finally {
      context.close();
    }
  }

  @Test
  void shouldRaiseBusinessExceptionWhenProjectedDashboardIsUnavailable() throws Exception {
    Field businessModelMap = BusinessModelCenter.class.getDeclaredField("businessModelMap");
    businessModelMap.setAccessible(true);
    Object previous = businessModelMap.get(null);
    try {
      businessModelMap.set(null, Collections.emptyMap());
      MockHttpServletRequest request = new MockHttpServletRequest(
          "GET", "/api/demo/missing-dashboard");

      BusinessException error = assertThrows(
          BusinessException.class,
          () -> new DashboardStructureController().getDashboard(request));

      assertEquals(
          "dashboard business model not found: /demo/missing-dashboard",
          error.getMessage());
    } finally {
      businessModelMap.set(null, previous);
    }
  }

  private DashboardBusinessModel dashboard() {
    DashboardBusinessModelMeta meta = new DashboardBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName("salesDashboard");
    meta.setTitle("Sales Dashboard");
    meta.setIsEnabled(Boolean.TRUE);
    DashboardBusinessModelMeta.WidgetDef widget = new DashboardBusinessModelMeta.WidgetDef();
    widget.setId("sales-chart");
    widget.setWidgetName("chart");
    widget.setChartName("demo.salesChart");
    meta.setWidgets(Collections.singletonList(widget));
    return new DashboardBusinessModel(meta, BusinessModelCenter.buildInProvider);
  }

  @Configuration
  static class TestConfiguration {
    @Bean
    RequestMappingIntegrationCenter requestMappingIntegrationCenter() {
      return new RequestMappingIntegrationCenter();
    }

    @Bean
    DashboardStructureController dashboardStructureController() {
      return new DashboardStructureController();
    }
  }
}
