package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ouroboros.dev.codeintelligence.CodeContextProvider;
import com.ouroboros.dev.codeintelligence.CodeContextController;
import com.ouroboros.dev.codeintelligence.service.CodeContextService;

/** Browser integration fixture: the production HTTP endpoint and real SPI inference, without external infrastructure. */
public final class CodeIntelligenceBrowserServer {
  public static void main(String[] args) {
    var application = new SpringApplication(CodeContextController.class,
        ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
        JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class);
    application.setWebApplicationType(WebApplicationType.SERVLET);
    application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "43184",
        "spring.main.banner-mode", "off"));
    application.addInitializers(context -> {
      var beans = (GenericApplicationContext) context;
      beans.registerBean(CodeContextProvider.class, LogicFlowCodeContextProvider::new);
      beans.registerBean(CodeContextService.class, () -> new CodeContextService(java.util.List.of(new LogicFlowCodeContextProvider()), java.util.List.of()));
      beans.registerBean(Health.class, Health::new);
    });
    application.run(args);
  }

  @RestController
  public static final class Health {
    @GetMapping("/health")
    public String health() { return "real-code-context-backend"; }
  }
}
