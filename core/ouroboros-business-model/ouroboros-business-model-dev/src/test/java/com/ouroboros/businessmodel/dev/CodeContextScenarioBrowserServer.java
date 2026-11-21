package com.ouroboros.businessmodel.dev;

import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ouroboros.businesslog.dev.BusinessLogCodeContextProvider;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.codeintelligence.service.CodeContextService;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.logicflow.dev.scriptintelligence.*;
import com.ouroboros.rpc.dev.RpcCodeContextProvider;
import com.ouroboros.util.Json;

/** Isolated HTTP acceptance fixture: production providers/controller, authored drafts and a fixed application catalog. */
public final class CodeContextScenarioBrowserServer {
  public static void main(String[] args) {
    var application = new SpringApplication(ScenarioConfiguration.class, ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class, JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class);
    application.setWebApplicationType(WebApplicationType.SERVLET);
    application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "43184", "spring.main.banner-mode", "off"));
    application.run(args);
  }

  @Configuration
  @Import({CodeContextController.class, Health.class})
  public static class ScenarioConfiguration {
    @Bean CodeContextService codeContextService() {
      var business = new BusinessModelCodeContextProvider(new BusinessModelDevService() {
        @Override public List<com.ouroboros.businessmodel.meta.BusinessModelMeta> getAll() { return List.of(); }
      });
      var providers = List.<CodeContextProvider>of(new LogicFlowCodeContextProvider(), business, new BusinessLogCodeContextProvider(),
          new RpcCodeContextProvider(List.of(new LogicFlowInvocationInputProvider())));
      ApplicationMetadataSnapshotProvider snapshots = appName -> ApplicationMetadataSnapshot.current(Map.of(
          "configuration", List.of(entry("rpc.endpoint", "{\"label\":\"RPC URL\",\"valueType\":\"STRING\"}"), entry("rpc.secret", "{\"label\":\"凭据配置\",\"valueType\":\"STRING\"}")),
          "rpc-model", List.of(entry("token", "{\"outputSchema\":{\"type\":\"object\",\"properties\":{\"data\":{\"type\":\"object\",\"properties\":{\"accessToken\":{\"type\":\"string\"},\"expiresIn\":{\"type\":\"integer\"}}}}}}")),
          "data-model", List.of(entry("Order", "{\"recordSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"customer\":{\"type\":\"string\"}}}}"))));
      return new CodeContextService(providers, List.of(snapshots));
    }
    private static Metadata entry(String name, String details) {
      var entry = new Metadata(); entry.setName(name); entry.setDetails(Json.toMap(details)); return entry;
    }
  }

  @RestController public static class Health {
    @GetMapping("/health") public String health() { return "code-context-scenarios"; }
  }
}
