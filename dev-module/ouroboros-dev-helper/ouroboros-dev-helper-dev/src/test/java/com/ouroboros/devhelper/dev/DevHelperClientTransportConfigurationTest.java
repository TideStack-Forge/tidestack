package com.ouroboros.devhelper.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;
import com.ouroboros.util.Json;

class DevHelperClientTransportConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(DevHelperDevAutoConfiguration.class));

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void defaultMqBindingsDeliverResponsesAndReleaseTheirSubscription(boolean explicitMq) {
    var bridge = mock(MQBridge.class);
    var producer = mock(MQProducer.class);
    var consumer = mock(MQConsumer.class);
    var handlers = new ArrayList<MessageHandler>();
    when(bridge.bindingProducer(anyString())).thenAnswer(call -> new MQProducerBuilder(call.getArgument(0), properties -> {
      assertThat(properties.getDestination()).isEqualTo("ouroboros.dev-helper.command");
      assertThat(properties.getExtraProps()).containsEntry("declareExchange", true).containsEntry("exchangeType", "topic");
      return producer;
    }));
    when(bridge.bindingConsumer(anyString())).thenAnswer(call -> new MQConsumerBuilder(call.getArgument(0), properties -> {
      assertThat(properties.getDestination()).isEqualTo("ouroboros.dev-helper.response");
      assertThat(properties.getBroadcast()).isTrue();
      return consumer;
    }));
    org.mockito.Mockito.doAnswer(call -> { handlers.add(call.getArgument(0)); return null; })
        .when(consumer).subscribe(any(MessageHandler.class));
    org.mockito.Mockito.doAnswer(call -> {
      var request = (CommandRequest) ((org.springframework.messaging.Message<?>) call.getArgument(0)).getPayload();
      var result = new CommandResult(request, "mq-result");
      handlers.getFirst().handleMessage(MessageBuilder.withPayload(
          Json.toJsonString(result).getBytes(StandardCharsets.UTF_8)).build());
      return null;
    }).when(producer).send(any(org.springframework.messaging.Message.class));

    var contextRunner = explicitMq ? runner.withPropertyValues("ouroboros.dev-helper.transport=mq") : runner;
    contextRunner.withBean(MQBridge.class, () -> bridge).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(DevHelperClientTransport.class);
      var future = context.getBean(DevHelperCommandService.class).exec("crm", "probe", Map.of(), 1000);
      assertThat(future.get(1, TimeUnit.SECONDS).getData()).isEqualTo("mq-result");
      verify(bridge).bindingProducer("ouroboros-dev-helper-command-client");
      verify(bridge).bindingConsumer("ouroboros-dev-helper-response-client");
    });
    verify(consumer).unsubscribe(handlers.getFirst());
  }

  @Test
  void suppliedTransportReplacesMqWithoutRequiringABridge() {
    assertCustomTransport(runner);
  }

  @Test
  void configuredLocalTransportPreservesIdentityAndUnsolicitedEvents() {
    assertCustomTransport(runner.withPropertyValues("ouroboros.dev-helper.transport=ipc"));
  }

  @Test
  void applicationStartupProbesHelpersThroughTheSelectedTransport() {
    var transport = new LocalTransport();
    runner.withBean(DevHelperClientTransport.class, () -> transport).run(context -> {
      context.publishEvent(new org.springframework.boot.context.event.ApplicationStartedEvent(
          new org.springframework.boot.SpringApplication(), new String[0],
          context.getSourceApplicationContext(), java.time.Duration.ZERO));
      assertThat(transport.sent).hasSize(1);
      var request = transport.sent.getFirst();
      assertThat(request.getAppName()).isEmpty();
      assertThat(request.getCommand()).isEqualTo("knockKnock");
      assertThat(request.getSequenceId()).isNull();
      assertThat(request.getParams()).containsEntry("source", "OuroborosDevelop");
      assertThat(request.getTimeout()).isEqualTo(1000);
    });
    assertThat(transport.closed).isTrue();
  }

  @Test
  void socketClientStartsWithoutMqOrAnAlreadyRunningHelper() {
    runner.withPropertyValues("ouroboros.dev-helper.transport=socket", "ouroboros.dev-helper.socket.port=1", "ouroboros.dev-helper.socket.token=secret")
        .run(context -> {
          assertThat(context).hasNotFailed().doesNotHaveBean(MQBridge.class);
          assertThat(context.getBean(DevHelperClientTransport.class)).isInstanceOf(com.ouroboros.devhelper.dev.transport.socket.SocketDevHelperClientTransport.class);
          context.publishEvent(new org.springframework.boot.context.event.ApplicationStartedEvent(
              new org.springframework.boot.SpringApplication(), new String[0], context.getSourceApplicationContext(), java.time.Duration.ZERO));
        });
  }

  private void assertCustomTransport(ApplicationContextRunner contextRunner) {
    var transport = new LocalTransport();
    contextRunner.withBean(DevHelperClientTransport.class, () -> transport).run(context -> {
      assertThat(context).hasNotFailed().doesNotHaveBean(MQBridge.class);
      var service = context.getBean(DevHelperCommandService.class);
      var observed = new ArrayList<CommandResult>();
      service.registerGlobalResultListener(observed::add);
      var future = service.exec("crm", "probe", Map.of("profile", "local", "runtimeGeneration", 3), 1000);
      var request = transport.sent.getFirst();
      assertThat(request.getProfile()).isEqualTo("local");
      assertThat(request.getRuntimeGeneration()).isEqualTo(3);
      transport.receiver.accept(new CommandResult(request, "local-result"));
      assertThat(future.get(1, TimeUnit.SECONDS).getData()).isEqualTo("local-result");
      transport.receiver.accept(new CommandResult().setAppName("crm").setCommand("knockKnock").setSuccess(true));
      assertThat(observed).hasSize(2);
    });
    assertThat(transport.closed).isTrue();
  }

  @Test
  void selectingAnUnavailableTransportFailsInsteadOfFallingBackToMq() {
    runner.withPropertyValues("ouroboros.dev-helper.transport=missing").run(context -> {
      assertThat(context).hasFailed();
      assertThat(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
          .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class)
          .hasMessageContaining(DevHelperClientTransport.class.getName());
    });
  }

  private static class LocalTransport implements DevHelperClientTransport {
    private final List<CommandRequest> sent = new ArrayList<>();
    private Consumer<CommandResult> receiver;
    private boolean closed;

    @Override public void send(CommandRequest request) { sent.add(request); }
    @Override public TransportSubscription subscribe(Consumer<CommandResult> receiver) {
      this.receiver = receiver;
      return () -> { closed = true; this.receiver = null; };
    }
  }
}
