package com.ouroboros.devhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperServerTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;
import com.ouroboros.util.Json;

class DevHelperServerTransportConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(DevHelperAutoConfiguration.class))
      .withBean(DataDictService.class, () -> mock(DataDictService.class))
      .withPropertyValues("spring.application.name=crm", "spring.profiles.active=local");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void defaultMqBindingsDispatchCommandsAndReleaseTheirSubscription(boolean explicitMq) {
    var bridge = mock(MQBridge.class);
    var handlers = new ArrayList<MessageHandler>();
    var sent = new ArrayList<CommandResult>();
    when(bridge.bindingProducer(anyString())).thenAnswer(call -> new MQProducerBuilder(call.getArgument(0), properties -> {
      assertThat(properties.getDestination()).isEqualTo("ouroboros.dev-helper.response");
      assertThat(properties.getExtraProps()).containsEntry("declareExchange", true);
      return mock(MQProducer.class);
    }));
    when(bridge.bindingConsumer(anyString())).thenAnswer(call -> new MQConsumerBuilder(call.getArgument(0), properties -> {
      assertThat(properties.getDestination()).isEqualTo("ouroboros.dev-helper.command");
      assertThat(properties.getBroadcast()).isTrue();
      return mock(MQConsumer.class);
    }));
    doAnswer(call -> { handlers.add(call.getArgument(1)); return null; })
        .when(bridge).subscribe(anyString(), any(MessageHandler.class));
    doAnswer(call -> { sent.add((CommandResult) ((Message<?>) call.getArgument(1)).getPayload()); return null; })
        .when(bridge).send(anyString(), any(Message.class));

    var contextRunner = explicitMq ? runner.withPropertyValues("ouroboros.dev-helper.transport=mq") : runner;
    contextRunner.withBean(MQBridge.class, () -> bridge).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(DevHelperServerTransport.class);
      var request = new CommandRequest().setAppName("crm").setCommand("knockKnock").setSequenceId("mq-sequence");
      handlers.getFirst().handleMessage(MessageBuilder.withPayload(
          Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());
      assertThat(sent).hasSize(1);
      assertThat(sent.getFirst().getSuccess()).isTrue();
      assertThat(sent.getFirst().getSequenceId()).isEqualTo("mq-sequence");
      verify(bridge).bindingProducer("ouroboros-dev-helper-result");
      verify(bridge).bindingConsumer("ouroboros-dev-helper-command");
    });
    verify(bridge).unsubscribe("ouroboros-dev-helper-command", handlers.getFirst());
  }

  @Test
  void suppliedTransportReplacesMqWithoutRequiringABridge() {
    assertCustomTransport(runner);
  }

  @Test
  void configuredLocalTransportUsesTheSameCommandsAndRuntimeIdentity() {
    assertCustomTransport(runner.withPropertyValues("ouroboros.dev-helper.transport=ipc"));
  }

  @Test
  void socketConfigurationStartsWithoutMqAndClosesItsPort() {
    var port = new java.util.concurrent.atomic.AtomicInteger();
    runner.withPropertyValues("ouroboros.dev-helper.transport=socket", "ouroboros.dev-helper.socket.port=0", "ouroboros.dev-helper.socket.token=secret")
        .run(context -> {
          assertThat(context).hasNotFailed().doesNotHaveBean(MQBridge.class);
          port.set(context.getBean(com.ouroboros.devhelper.transport.socket.SocketDevHelperServerTransport.class).port());
        });
    org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> new java.net.Socket("127.0.0.1", port.get()));
  }

  private void assertCustomTransport(ApplicationContextRunner contextRunner) {
    var transport = new LocalTransport();
    contextRunner.withBean(DevHelperServerTransport.class, () -> transport).run(context -> {
      assertThat(context).hasNotFailed().doesNotHaveBean(MQBridge.class);
      var request = new CommandRequest().setAppName("crm").setProfile("local")
          .setCommand("knockKnock").setSequenceId("local-sequence");
      transport.receiver.accept(request);
      assertThat(transport.sent).hasSize(1);
      var result = transport.sent.getFirst();
      assertThat(result.getSuccess()).isTrue();
      assertThat(result.getSequenceId()).isEqualTo("local-sequence");
      assertThat(result.getProfile()).isEqualTo("local");
      assertThat(result.getHelperInstanceId()).isEqualTo(HelperRuntimeIdentity.current().getInstanceId());
      context.getBean(HelperCommandDispatcher.class).knockKnock();
      assertThat(transport.sent).hasSize(2);
      assertThat(transport.sent.getLast().getSequenceId()).isNull();
    });
    assertThat(transport.closed).isTrue();
  }

  @Test
  void selectingAnUnavailableTransportFailsInsteadOfFallingBackToMq() {
    runner.withPropertyValues("ouroboros.dev-helper.transport=missing").run(context -> {
      assertThat(context).hasFailed();
      assertThat(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
          .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class)
          .hasMessageContaining(DevHelperServerTransport.class.getName());
    });
  }

  private static class LocalTransport implements DevHelperServerTransport {
    private final List<CommandResult> sent = new ArrayList<>();
    private Consumer<CommandRequest> receiver;
    private boolean closed;

    @Override public void send(CommandResult result) { sent.add(result); }
    @Override public TransportSubscription subscribe(Consumer<CommandRequest> receiver) {
      this.receiver = receiver;
      return () -> { closed = true; this.receiver = null; };
    }
  }
}
