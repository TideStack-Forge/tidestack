package com.ouroboros.app.error;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.ouroboros.exception.InternalException;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authentication.filter.AbstractAuthenticationFilter;
import com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.*;

class ServletErrorDispatchTest {
  @TempDir
  Path directory;

  @Test
  void filterFailureUsesRealContainerErrorDispatchAndNextRequestRecovers() throws Exception {
    var tomcat = new Tomcat();
    tomcat.setBaseDir(directory.toString());
    tomcat.setPort(0);
    tomcat.getConnector();
    var context = tomcat.addContext("", directory.toString());
    var webContext = new AnnotationConfigWebApplicationContext();
    webContext.register(WebConfig.class);
    Tomcat.addServlet(context, "mvc", new DispatcherServlet(webContext)).setLoadOnStartup(1);
    context.addServletMappingDecoded("/", "mvc");
    var errorPage = new ErrorPage();
    errorPage.setErrorCode(500);
    errorPage.setLocation("/error");
    context.addErrorPage(errorPage);
    var authenticationCalls = new AtomicInteger();
    var filter = new AbstractAuthenticationFilter(request -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        if (authenticationCalls.incrementAndGet() == 1) {
          throw new InternalException("PRIVATE_SQL_DIAGNOSTIC", new IllegalStateException("read timed out"));
        }
        return Optional.empty();
      }
    };
    var filterDef = new FilterDef();
    filterDef.setFilterName("authentication");
    filterDef.setFilter(filter);
    context.addFilterDef(filterDef);
    var filterMap = new FilterMap();
    filterMap.setFilterName("authentication");
    filterMap.addURLPattern("/*");
    filterMap.setDispatcher(DispatcherType.REQUEST.name());
    filterMap.setDispatcher(DispatcherType.ERROR.name());
    context.addFilterMap(filterMap);
    // Exercise the real authorization filter on ERROR, where no RBAC lookup may occur.
    var authorizeDef = new FilterDef();
    authorizeDef.setFilterName("authorization");
    authorizeDef.setFilter(new AuthorizeCheckFilterSupplier().getAuthorizeFilter());
    context.addFilterDef(authorizeDef);
    var authorizeMap = new FilterMap();
    authorizeMap.setFilterName("authorization");
    authorizeMap.addURLPattern("/*");
    authorizeMap.setDispatcher(DispatcherType.ERROR.name());
    context.addFilterMap(authorizeMap);
    try {
      tomcat.start();
      var client = HttpClient.newHttpClient();
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
          + tomcat.getConnector().getLocalPort() + "/api/probe")).GET().build();
      var failed = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(500, failed.statusCode());
      assertTrue(failed.body().contains("50000"));
      assertTrue(failed.body().contains("服务暂时不可用，请稍后重试"));
      assertFalse(failed.body().contains("PRIVATE_SQL_DIAGNOSTIC"));
      assertFalse(failed.body().contains("startupState"));
      assertEquals(1, authenticationCalls.get());
      var recovered = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, recovered.statusCode());
      assertTrue(recovered.body().contains("recovered"));
      assertEquals(2, authenticationCalls.get());
    } finally {
      tomcat.stop();
      tomcat.destroy();
      webContext.close();
    }
  }

  @Configuration
  @EnableWebMvc
  static class WebConfig {
    @Bean
    OuroborosErrorController errorController() {
      return new OuroborosErrorController();
    }

    @Bean
    ProbeController probeController() {
      return new ProbeController();
    }
  }

  @RestController
  static class ProbeController {
    @GetMapping("/api/probe")
    WebApiResult<String> probe() {
      return WebApiResult.ok("recovered");
    }
  }
}
