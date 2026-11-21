package com.ouroboros.testing.groovy;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.devhelper.HelperCommandDispatcher;
import com.ouroboros.devhelper.command.*;
import com.ouroboros.devhelper.codeenvironment.ApplicationCodeEnvironment;
import com.ouroboros.devhelper.dev.DefaultDevHelperCommandService;
import com.ouroboros.devhelper.metadata.collector.ScriptIntelligenceMetadataCollector;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider;
import com.ouroboros.mq.StreamMQBridge;
import com.ouroboros.mq.DefaultProducerCreator;
import com.ouroboros.mq.binding.MQBindingServicePropertiesConfigurer;
import com.ouroboros.mq.binder.EmptyMQBinderReadyChecker;
import com.ouroboros.mq.rabbit.OuroborosRabbitMQAutoConfiguration;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.script.intelligence.RuntimeScriptIntelligenceSnapshotFactory;
import com.ouroboros.script.intelligence.impl.ClassGraphJavaTypeMetadataIndex;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.*;

/** Two independent JVM roles. Only the helper loads the fixture extension; only the platform owns LSP. */
public final class GroovyChainServer {
  private static volatile boolean ready;
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({StreamMQBridge.class, DefaultProducerCreator.class, MQBindingServicePropertiesConfigurer.class,
      EmptyMQBinderReadyChecker.class, OuroborosRabbitMQAutoConfiguration.class,
      CodeIntelligenceAutoConfiguration.class, DefaultDevHelperCommandService.class, Health.class})
  public static class PlatformConfiguration {}

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({StreamMQBridge.class, DefaultProducerCreator.class, MQBindingServicePropertiesConfigurer.class,
      EmptyMQBinderReadyChecker.class, OuroborosRabbitMQAutoConfiguration.class,
      HelperCommandDispatcher.class, CodeEnvironmentCommand.class,
      com.ouroboros.app.scriptcontext.SpringBeanFactoryScriptContextContributor.class})
  public static class HelperConfiguration {}

  public static void main(String[] args) throws Exception {
    boolean helper = args[3].equals("helper");
    var loader = Thread.currentThread().getContextClassLoader();
    var excluded = new java.util.ArrayList<String>();
    for (String name : ImportCandidates.load(AutoConfiguration.class, loader)) {
      if (name.startsWith("com.ouroboros.") || name.contains("DataSource") || name.contains("HibernateJpa")
          || name.contains("SecurityAutoConfiguration") || name.contains("UserDetailsServiceAutoConfiguration")
          || name.contains("Druid") || name.contains("Flyway") || name.contains("Liquibase")) excluded.add(name);
    }
    var application = new SpringApplication(helper ? HelperConfiguration.class : PlatformConfiguration.class);
    application.setWebApplicationType(helper ? WebApplicationType.NONE : WebApplicationType.SERVLET);
    application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "43184",
        "spring.application.name", "example", "spring.main.banner-mode", "off",
        "spring.autoconfigure.exclude", String.join(",", excluded), "spring.cloud.stream.default-binder", "rabbit",
        "ouroboros.code-intelligence.groovy.distribution", args[1],
        "ouroboros.code-intelligence.language.storage", args[2]));
    var snapshot = new AtomicReference<>(ApplicationMetadataSnapshot.unavailable());
    application.addInitializers(context -> {
      var beans = (GenericApplicationContext) context;
      if (helper) {
        try {
          var extension = new URLClassLoader(new java.net.URL[] {Path.of(args[0]).toUri().toURL()}, loader);
          beans.registerBean("fixtureExtension", URLClassLoader.class, () -> extension);
          beans.registerBean(ApplicationCodeEnvironment.class, () -> new ApplicationCodeEnvironment(extension));
          beans.registerBean("fixtureMetadataCommand", Command.class, () -> new Command() {
            public String getCommand() { return "acceptanceEnvironment"; }
            public CompletableFuture<CommandResult> execute(CommandRequest request) {
              var previous = Thread.currentThread().getContextClassLoader();
              try {
                Thread.currentThread().setContextClassLoader(extension);
                var collector = new ScriptIntelligenceMetadataCollector(new RuntimeScriptIntelligenceSnapshotFactory(
                    () -> Map.of(), new ClassGraphJavaTypeMetadataIndex(List.of("probe"))));
                return CompletableFuture.completedFuture(new CommandResult(request, collector.getAllMetadata()));
              } finally { Thread.currentThread().setContextClassLoader(previous); }
            }
          });
        } catch (java.net.MalformedURLException e) { throw new IllegalStateException(e); }
      } else {
        if ("container".equals(beans.getEnvironment().getProperty("ouroboros.code-intelligence.language.deployment"))) {
          beans.registerBean(com.ouroboros.container.config.DockerConfiguration.class, com.ouroboros.container.config.DockerConfiguration::new);
          beans.registerBean(com.ouroboros.container.service.impl.DockerContainerServiceImpl.class);
          beans.registerBean(com.ouroboros.dev.runtime.service.DevRuntimeService.class, () -> new com.ouroboros.dev.runtime.service.DefaultDevRuntimeService(
              List.of(new com.ouroboros.dev.runtime.docker.backend.DockerLanguageRuntimeBackend(beans.getBean(com.ouroboros.container.service.impl.DockerContainerServiceImpl.class))),
              List.of(), new com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore()));
        }
        beans.registerBean(CodeContextProvider.class, LogicFlowCodeContextProvider::new);
        beans.registerBean(ApplicationMetadataSnapshotProvider.class, () -> app -> snapshot.get());
      }
    });
    var context = application.run(java.util.Arrays.copyOfRange(args, 4, args.length));
    context.publishEvent(new OuroborosContextRefreshedEvent(new ApplicationStartedEvent(application, new String[0], context, java.time.Duration.ZERO)));
    if (helper) {
      System.out.println("GROOVY_HELPER_READY pid=" + ProcessHandle.current().pid());
    } else {
      var result = context.getBean(DefaultDevHelperCommandService.class).exec("example", "acceptanceEnvironment", Map.of(), 30000).join();
      if (!result.getSuccess()) throw new IllegalStateException(result.getMessage());
      var metadata = com.ouroboros.util.Json.toBean(Metadata[].class, com.ouroboros.util.Json.toJsonString(result.getData()));
      snapshot.set(ApplicationMetadataSnapshot.current(Map.of("script-intelligence", List.of(metadata))).withRuntimeIdentity(Map.of(
          "helperInstanceId", result.getHelperInstanceId(), "appIncarnationId", result.getAppIncarnationId(), "runtimeGeneration", 1)));
      ready = true;
      System.out.println("GROOVY_PLATFORM_READY pid=" + ProcessHandle.current().pid());
    }
  }

  @RestController public static class Health {
    @GetMapping("/health") public String health() { if (!ready) throw new IllegalStateException("Environment not ready"); return "platform-owned-language-services"; }
  }
}
