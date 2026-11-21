package com.ouroboros.develop.logicflowdebug;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cloud.stream.config.BindingServiceConfiguration;
import org.springframework.cloud.stream.function.FunctionConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.mq.rabbit.OuroborosRabbitMQAutoConfiguration;
import com.ouroboros.devhelper.dev.DefaultDevHelperCommandService;
import com.ouroboros.devhelper.HelperCommandDispatcher;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.logicflow.debug.LogicFlowDebugHelperAutoConfiguration;
import com.ouroboros.logicflow.compiler.FunctionalFlowCompiler;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.util.Json;
import io.vavr.control.Try;

/** Test fixture only. Each helper runs in a separate JVM and uses production command and analyzer wiring. */
public class DebugMqFixture {
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class,
      ConfigurationPropertiesAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
      IntegrationAutoConfiguration.class, BindingServiceConfiguration.class, FunctionConfiguration.class,
      org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration.class,
      org.springframework.cloud.function.cloudevent.CloudEventsFunctionExtensionConfiguration.class})
  @Import({com.ouroboros.mq.StreamMQBridge.class, com.ouroboros.mq.DefaultProducerCreator.class,
      com.ouroboros.mq.binding.MQBindingServicePropertiesConfigurer.class,
      com.ouroboros.mq.binder.EmptyMQBinderReadyChecker.class, OuroborosRabbitMQAutoConfiguration.class})
  static class Transport {}

  @Configuration(proxyBeanMethods = false)
  @Import({Transport.class, DefaultDevHelperCommandService.class,
      com.ouroboros.logicflow.dev.service.LogicFlowDebugCaptureService.class,
      com.ouroboros.logicflow.dev.service.LogicFlowDebugEventBridge.class,
      com.ouroboros.dev.app.service.runtime.TestAppLogicFlowDebugRuntime.class,
      com.ouroboros.dev.app.service.runtime.AuthenticationPrincipalTestAppProfileResolver.class})
  public static class Platform {}

  @Configuration(proxyBeanMethods = false)
  @Import({Transport.class, HelperCommandDispatcher.class, LogicFlowDebugHelperAutoConfiguration.class})
  @ComponentScan(basePackages = "com.ouroboros.devhelper.command", useDefaultFilters = false,
      includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class))
  public static class Helper {}

  public static ConfigurableApplicationContext start(Class<?> role, String appName, String profile, int port) {
    return start(role, appName, profile, port, Map.of());
  }

  public static ConfigurableApplicationContext start(Class<?> role, String appName, String profile, int port, Map<String, Object> beans) {
    var app = new SpringApplication(role);
    app.addInitializers(context -> beans.forEach((name, bean) -> context.getBeanFactory().registerSingleton(name, bean)));
    app.setWebApplicationType(Boolean.getBoolean("logicflow.fixture.web") ? WebApplicationType.SERVLET : WebApplicationType.NONE);
    var context = app.run("--server.address=127.0.0.1", "--server.port=" + System.getProperty("logicflow.fixture.web.port", "18878"),
        "--spring.main.banner-mode=off", "--spring.application.name=" + appName,
        "--spring.profiles.active=" + profile, "--ouroboros.dev-helper.test-app=true",
        "--spring.rabbitmq.host=" + System.getProperty("logicflow.rabbit.host", "127.0.0.1"), "--spring.rabbitmq.port=" + port,
        "--spring.rabbitmq.username=debug", "--spring.rabbitmq.password=debug",
        "--spring.cloud.stream.default-binder=rabbit", "--spring.cloud.stream.binding-retry-interval=1");
    context.publishEvent(new OuroborosContextRefreshedEvent(new ApplicationStartedEvent(app, new String[0], context, Duration.ZERO)));
    return context;
  }

  public static void main(String[] args) throws Exception {
    try (var context = start(Helper.class, args[0], args[1], Integer.parseInt(args[2]));
         var input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
      Files.writeString(Path.of(args[3]), Json.tryToJsonString(HelperRuntimeIdentity.current()).get());
      if (args.length > 4 && args[4].equals("watch")) {
        for (Class<?> type : List.of(LogicFlowDebugHelperAutoConfiguration.class, HelperCommandDispatcher.class,
            com.ouroboros.devhelper.command.CommandRequest.class, com.ouroboros.dev.DevelopWorkspaceContext.class)) {
          System.out.println("ARTIFACT|" + type.getName() + "|" + type.getProtectionDomain().getCodeSource().getLocation());
        }
        Path commands = Path.of(args[3]).getParent().resolve("commands");
        Files.createDirectories(commands);
        var success = compiled(false);
        var failure = compiled(true);
        while (!Files.exists(commands.resolve("exit"))) {
          try (var entries = Files.list(commands)) {
            for (Path command : entries.sorted().toList()) {
              boolean fail = Files.readString(command).trim().equals("fail");
              var result = (fail ? failure : success).apply(new Object[]{"hello"});
              System.out.println("BUSINESS|" + (result.isSuccess() ? "success" : "failed"));
              Files.delete(command);
            }
          }
          Thread.sleep(100);
        }
        return;
      }
      String line;
      while ((line = input.readLine()) != null && !line.equals("exit")) {
        var result = compiled(line.equals("fail")).apply(new Object[]{"hello"});
        System.out.println("BUSINESS|" + (result.isSuccess() ? "success" : "failed"));
      }
    }
  }

  public static java.util.function.Function<Object[], Try<Object>> compiled(boolean fail) {
    var node = org.mockito.Mockito.mock(FlowNode.class);
    org.mockito.Mockito.when(node.getName()).thenReturn("script");
    org.mockito.Mockito.when(node.getType()).thenReturn("Script");
    org.mockito.Mockito.when(node.getDescription()).thenReturn("Fixture script");
    org.mockito.Mockito.when(node.isEnabled()).thenReturn(true);
    org.mockito.Mockito.when(node.isValid()).thenReturn(true);
    org.mockito.Mockito.when(node.isAsync()).thenReturn(false);
    org.mockito.Mockito.when(node.getInputPorts()).thenReturn(Collections.singletonList(null));
    org.mockito.Mockito.when(node.getOutputPorts()).thenReturn(Collections.singletonList(null));
    org.mockito.Mockito.when(node.getIncomingConnections()).thenReturn(List.of());
    org.mockito.Mockito.when(node.getOutgoingConnections()).thenReturn(List.of());
    org.mockito.Mockito.when(node.execute(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call ->
        fail ? Try.failure(new IllegalStateException("fixture business failure")) : Try.success(call.getArgument(0)));
    var entry = org.mockito.Mockito.mock(FlowNode.class);
    org.mockito.Mockito.when(entry.getName()).thenReturn("entry");
    org.mockito.Mockito.when(entry.getType()).thenReturn("Entry");
    org.mockito.Mockito.when(entry.getDescription()).thenReturn("Fixture input");
    org.mockito.Mockito.when(entry.isEnabled()).thenReturn(true);
    org.mockito.Mockito.when(entry.isValid()).thenReturn(true);
    org.mockito.Mockito.when(entry.isAsync()).thenReturn(false);
    org.mockito.Mockito.when(entry.getInputPorts()).thenReturn(Collections.singletonList(null));
    org.mockito.Mockito.when(entry.getOutputPorts()).thenReturn(Collections.singletonList(null));
    org.mockito.Mockito.when(entry.getIncomingConnections()).thenReturn(List.of());
    org.mockito.Mockito.when(entry.execute(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> Try.success(call.getArgument(0)));
    var connection = org.mockito.Mockito.mock(FlowConnection.class);
    org.mockito.Mockito.when(connection.getSourceNode()).thenReturn(entry);
    org.mockito.Mockito.when(connection.getTargetNode()).thenReturn(node);
    org.mockito.Mockito.when(connection.getSourceName()).thenReturn("entry");
    org.mockito.Mockito.when(connection.getTargetName()).thenReturn("script");
    org.mockito.Mockito.when(connection.getSourceOutputPort()).thenReturn(0);
    org.mockito.Mockito.when(connection.getTargetInputPort()).thenReturn(0);
    org.mockito.Mockito.when(entry.getOutgoingConnections()).thenReturn(List.of(connection));
    org.mockito.Mockito.when(node.getIncomingConnections()).thenReturn(List.of(connection));
    var model = org.mockito.Mockito.mock(FlowModel.class);
    org.mockito.Mockito.when(model.getName()).thenReturn("flow");
    org.mockito.Mockito.when(model.getRevision()).thenReturn("fixture-revision");
    org.mockito.Mockito.when(model.getNodes()).thenReturn(List.of(entry, node));
    org.mockito.Mockito.when(model.getConnections()).thenReturn(List.of(connection));
    org.mockito.Mockito.when(model.getExitNode()).thenReturn(node);
    return FunctionalFlowCompiler.INSTANCE.compile(model);
  }
}
