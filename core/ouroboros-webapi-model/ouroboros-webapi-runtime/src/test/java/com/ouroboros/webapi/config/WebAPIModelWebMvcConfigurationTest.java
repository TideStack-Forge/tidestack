package com.ouroboros.webapi.config;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class WebAPIModelWebMvcConfigurationTest {

  private final WebAPIModelWebMvcConfiguration configuration = new WebAPIModelWebMvcConfiguration(null);

  @Test
  void apiTrailingSlashFilterRunsBeforeSecurityAndOnlyForApiPaths() {
    var registration = configuration.apiTrailingSlashUrlHandlerFilter();

    assertEquals("ouroborosApiTrailingSlashUrlHandlerFilter", registration.getFilterName());
    assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, registration.getOrder());
    assertTrue(registration.getUrlPatterns().contains("/api/*"));
  }

  @Test
  void apiTrailingSlashFilterWrapsRequestWithCanonicalPath() throws Exception {
    var downstreamRequest = filterRequest("/api/app/");

    assertNotNull(downstreamRequest);
    assertEquals("/api/app", downstreamRequest.getRequestURI());
    assertEquals("/api/app", downstreamRequest.getServletPath());
  }

  @Test
  void apiTrailingSlashFilterLeavesCanonicalPathUnchanged() throws Exception {
    var downstreamRequest = filterRequest("/api/app");

    assertNotNull(downstreamRequest);
    assertEquals("/api/app", downstreamRequest.getRequestURI());
    assertEquals("/api/app", downstreamRequest.getServletPath());
  }

  private HttpServletRequest filterRequest(String path) throws Exception {
    var request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    var response = new MockHttpServletResponse();
    var downstreamRequest = new AtomicReference<HttpServletRequest>();

    configuration.apiTrailingSlashUrlHandlerFilter().getFilter().doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> downstreamRequest.set((HttpServletRequest) servletRequest)
    );

    assertEquals(200, response.getStatus());
    return downstreamRequest.get();
  }
}
