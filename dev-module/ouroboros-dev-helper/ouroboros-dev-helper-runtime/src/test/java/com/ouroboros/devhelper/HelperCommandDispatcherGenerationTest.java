package com.ouroboros.devhelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.devhelper.transport.mq.MqDevHelperServerTransport;
import com.ouroboros.devhelper.command.Command;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;
import com.ouroboros.util.Json;

@ExtendWith(MockitoExtension.class)
class HelperCommandDispatcherGenerationTest {

  @Mock
  private MQBridge mqBridge;

  private final List<Message<?>> sentMessages = new ArrayList<Message<?>>();
  private MessageHandler commandHandler;
  private HelperCommandDispatcher dispatcher;

  @org.junit.jupiter.api.AfterEach
  void releaseDispatcher() {
    if (dispatcher != null) {
      dispatcher.shutdown();
    }
  }

  @BeforeEach
  void setUp() {
    when(mqBridge.bindingProducer(anyString())).thenAnswer(invocation -> new MQProducerBuilder(
        invocation.getArgument(0),
        properties -> new MQProducer() {
          @Override
          public void send(Message<?> message) {
            sentMessages.add(message);
          }

          @Override
          public void send(String routingKey, Message<?> message) {
            sentMessages.add(message);
          }
        }
    ));
    when(mqBridge.bindingConsumer(anyString())).thenAnswer(invocation -> new MQConsumerBuilder(
        invocation.getArgument(0),
        properties -> new MQConsumer() {
          @Override
          public void subscribe(MessageHandler messageHandler) {
            commandHandler = messageHandler;
          }

          @Override
          public void unsubscribe(MessageHandler messageHandler) {
          }
        }
    ));
    doAnswer(invocation -> {
      commandHandler = invocation.getArgument(1);
      return null;
    }).when(mqBridge).subscribe(anyString(), any(MessageHandler.class));
    doAnswer(invocation -> {
      sentMessages.add(invocation.getArgument(1));
      return null;
    }).when(mqBridge).send(anyString(), any(Message.class));
  }

  @Test
  void shouldReturnDelayedCommandResultBeforeCancellingDeadline() {
    var completion = new CompletableFuture<CommandResult>();
    Command command = new Command() {
      public String getCommand() { return "delayed"; }
      public CompletableFuture<CommandResult> execute(CommandRequest request) { return completion; }
    };
    dispatcher = new HelperCommandDispatcher("crm", "test", new MqDevHelperServerTransport(mqBridge), List.of(command));
    var request = new CommandRequest().setAppName("crm").setCommand("delayed").setSequenceId("delayed-result").setTimeout(1000);
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals(0, sentMessages.size());
    completion.complete(new CommandResult(request, "completed"));
    assertEquals(1, sentMessages.size());
    var result = (CommandResult) sentMessages.getFirst().getPayload();
    assertEquals(true, result.getSuccess());
    assertEquals("delayed-result", result.getSequenceId());
    assertEquals("test", result.getProfile());
    assertEquals(com.ouroboros.devhelper.util.HelperRuntimeIdentity.current().getInstanceId(), result.getHelperInstanceId());
  }

  @Test
  void shouldReturnAsynchronousCommandFailure() {
    var completion = new CompletableFuture<CommandResult>();
    Command command = new Command() {
      public String getCommand() { return "delayedFailure"; }
      public CompletableFuture<CommandResult> execute(CommandRequest request) { return completion; }
    };
    dispatcher = new HelperCommandDispatcher("crm", new MqDevHelperServerTransport(mqBridge), List.of(command));
    var request = new CommandRequest().setAppName("crm").setCommand(command.getCommand()).setTimeout(1000);
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());
    completion.completeExceptionally(new IllegalStateException("asynchronous failure"));
    assertEquals(1, sentMessages.size());
    var result = (CommandResult) sentMessages.getFirst().getPayload();
    assertEquals(false, result.getSuccess());
    assertEquals("asynchronous failure", result.getMessage());
  }

  @Test
  void shouldEchoRuntimeGenerationInHelperResponses() {
    Command command = new Command() {
      @Override
      public String getCommand() {
        return Commands.REFRESH_METADATA.getCommand();
      }

      @Override
      public CompletableFuture<CommandResult> execute(CommandRequest request) {
        return CompletableFuture.completedFuture(new CommandResult(request, "ok"));
      }
    };
    dispatcher = new HelperCommandDispatcher("crm", new MqDevHelperServerTransport(mqBridge), Arrays.asList(command));

    CommandRequest request = new CommandRequest()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(Long.valueOf(9L))
        .setTimeout(1000L);

    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());

    CommandResult result = (CommandResult) sentMessages.get(0).getPayload();
    assertEquals(Long.valueOf(9L), result.getRuntimeGeneration());
  }

  @Test
  void shouldReturnFailureWhenCommandThrowsBeforeReturningAFuture() {
    Command command = new Command() {
      @Override
      public String getCommand() {
        return Commands.REFRESH_METADATA.getCommand();
      }

      @Override
      public CompletableFuture<CommandResult> execute(CommandRequest request) {
        throw new IllegalStateException("command failed");
      }
    };
    dispatcher = new HelperCommandDispatcher("crm", new MqDevHelperServerTransport(mqBridge), Arrays.asList(command));
    CommandRequest request = new CommandRequest()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setTimeout(1000L);

    commandHandler.handleMessage(
        MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build()
    );

    CommandResult result = (CommandResult) sentMessages.get(0).getPayload();
    assertEquals(false, result.getSuccess());
    assertEquals("command failed", result.getMessage());
  }
  @Test
  void wrongProcessCannotExecuteACommandAndReplyReportsActualResponder() {
    var executions = new java.util.concurrent.atomic.AtomicInteger();
    Command command = new Command() {
      @Override public String getCommand() { return "probe"; }
      @Override public CompletableFuture<CommandResult> execute(CommandRequest request) {
        executions.incrementAndGet();
        return CompletableFuture.completedFuture(new CommandResult(request, "ok")
            .setHelperInstanceId("invented").setAppIncarnationId("invented"));
      }
    };
    dispatcher = new HelperCommandDispatcher("crm", "test", new MqDevHelperServerTransport(mqBridge), List.of(command));
    var request = new CommandRequest().setAppName("crm").setProfile("test").setCommand("probe")
        .setHelperInstanceId("old-process");
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals(0, executions.get());
    assertEquals(0, sentMessages.size());
    var identity = com.ouroboros.devhelper.util.HelperRuntimeIdentity.current();
    request.setHelperInstanceId(identity.getInstanceId());
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(request).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals(1, executions.get());
    var result = (CommandResult) sentMessages.getFirst().getPayload();
    assertEquals(identity.getInstanceId(), result.getHelperInstanceId());
    assertEquals(identity.getStartedAtEpochMillis(), result.getHelperStartedAtEpochMillis());
    assertEquals(identity.getAppIncarnationId(), result.getAppIncarnationId());
    assertEquals("test", result.getProfile());
  }

  @Test
  void malformedOrUnownedBroadcastCommandsAreDiscardedBeforeDispatch() {
    var executions = new java.util.concurrent.atomic.AtomicInteger();
    Command command = new Command() {
      @Override public String getCommand() { return "probe"; }
      @Override public CompletableFuture<CommandResult> execute(CommandRequest request) {
        executions.incrementAndGet();
        return CompletableFuture.completedFuture(new CommandResult(request, "ready"));
      }
    };
    dispatcher = new HelperCommandDispatcher("crm", "test", new MqDevHelperServerTransport(mqBridge), List.of(command));
    commandHandler.handleMessage(MessageBuilder.withPayload(new byte[0]).build());
    commandHandler.handleMessage(MessageBuilder.withPayload(new byte[262145]).build());
    commandHandler.handleMessage(MessageBuilder.withPayload("invalid payload type").build());
    commandHandler.handleMessage(MessageBuilder.withPayload("not-json".getBytes(StandardCharsets.UTF_8)).build());
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(new CommandRequest()
        .setAppName("other").setCommand("probe")).getBytes(StandardCharsets.UTF_8)).build());
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(new CommandRequest()
        .setAppName("crm").setProfile("other").setCommand("probe")).getBytes(StandardCharsets.UTF_8)).build());
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(new CommandRequest()
        .setAppName("crm").setProfile("test").setCommand("missing")).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals(0, sentMessages.size());
    assertEquals(0, executions.get());
    commandHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(new CommandRequest()
        .setAppName("crm").setProfile("test").setCommand("probe")).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals(1, executions.get());
    assertEquals("ready", ((CommandResult) sentMessages.getFirst().getPayload()).getData());
  }

}
