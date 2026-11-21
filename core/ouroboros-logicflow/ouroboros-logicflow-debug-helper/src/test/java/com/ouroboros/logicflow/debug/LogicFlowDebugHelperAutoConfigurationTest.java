package com.ouroboros.logicflow.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.Message;

import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.MQBridgeStubFactory;
import com.ouroboros.util.Json;

class LogicFlowDebugHelperAutoConfigurationTest {

  @Test
  void dualRegistrationPointsAtDebugHelperAutoConfiguration() {
    String springFactories = readResource("META-INF/spring.factories");
    String autoConfigurationImports = readResource(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    String target = "com.ouroboros.logicflow.debug.LogicFlowDebugHelperAutoConfiguration";

    assertTrue(springFactories.contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration=" + target));
    assertTrue(autoConfigurationImports.contains(target));
  }

  @Test
  void debugHelperUsesBootStyleAutoConfigurationAnnotation() {
    assertTrue(LogicFlowDebugHelperAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class));
  }

  @Test
  void autoConfigurationCreatesSupportAndBindsApplicationProperties() {
    var captureProducer = new CaptureProducer();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LogicFlowDebugHelperAutoConfiguration.class))
        .withBean(MQBridge.class, () -> mqBridge(captureProducer))
        .withPropertyValues(
            "spring.application.name=crm-app",
            "ouroboros.logicflow.debug.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(LogicFlowDebugRuntimeSupport.class);

          var support = context.getBean(LogicFlowDebugRuntimeSupport.class);
          var analyzer = new LogicFlowDebugRuntimeAnalyzer();
          var invocation = new com.ouroboros.logicflow.diagnostic.FlowExecutionContext("run", null, null, "main");
          analyzer.onFlowStart(invocation, new LogicFlowDebugRuntimeAnalyzerTest.StubFlowModel(), java.util.List.of("input"));
          support.sendPending();
          org.junit.jupiter.api.Assertions.assertNull(captureProducer.message);
          var target = new com.ouroboros.logicflow.diagnostic.FlowDebugTarget("crm-app", "test", "container", 1, "helper", 100, "incarnation");
          support.runtime().start("capture", target, "order-flow", 1, 30000);
          analyzer.onFlowStart(invocation, new LogicFlowDebugRuntimeAnalyzerTest.StubFlowModel(), java.util.List.of("input"));
          support.sendPending();
          assertNotNull(captureProducer.message);

          var payload = new String((byte[]) captureProducer.message.getPayload(), StandardCharsets.UTF_8);
          var event = Json.toBean(FlowDebugEvent.class, payload);
          assertEquals("crm-app", event.getAppName());
        });
  }

  private static MQBridge mqBridge(MQProducer producer) {
    return MQBridgeStubFactory.bridgeWithProducer(producer);
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  private static class CaptureProducer implements MQProducer {
    private String routingKey;
    private Message<?> message;

    @Override
    public void send(Message<?> message) {
      this.message = message;
    }

    @Override
    public void send(String routingKey, Message<?> message) {
      this.routingKey = routingKey;
      this.message = message;
    }
  }
}
