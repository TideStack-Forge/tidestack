package com.ouroboros.devhelper.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.devhelper.dev.transport.mq.MqDevHelperClientTransport;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;
import com.ouroboros.util.Json;

@ExtendWith(MockitoExtension.class)
class DefaultDevHelperCommandServiceGenerationTest {

  @Mock
  private MQBridge mqBridge;

  private final List<Message<?>> sentMessages = new ArrayList<Message<?>>();
  private MessageHandler resultHandler;
  private RuntimeException sendFailure;
  private java.util.function.Consumer<Message<?>> sendAction;
  private DefaultDevHelperCommandService commandService;

  @BeforeEach
  void setUp() {
    when(mqBridge.bindingProducer(anyString())).thenAnswer(invocation -> new MQProducerBuilder(
        invocation.getArgument(0),
        properties -> new MQProducer() {
          @Override
          public void send(Message<?> message) {
            if (sendFailure != null) {
              throw sendFailure;
            }
            sentMessages.add(message);
            if (sendAction != null) sendAction.accept(message);
          }

          @Override
          public void send(String routingKey, Message<?> message) {
            if (sendFailure != null) {
              throw sendFailure;
            }
            sentMessages.add(message);
            if (sendAction != null) sendAction.accept(message);
          }
        }
    ));
    when(mqBridge.bindingConsumer(anyString())).thenAnswer(invocation -> new MQConsumerBuilder(
        invocation.getArgument(0),
        properties -> new MQConsumer() {
          @Override
          public void subscribe(MessageHandler messageHandler) {
            resultHandler = messageHandler;
          }

          @Override
          public void unsubscribe(MessageHandler messageHandler) {
          }
        }
    ));
  }

  @AfterEach
  void tearDown() {
    if (commandService != null) {
      commandService.shutdownDeadlineScheduler();
    }
  }

  @Test
  void shouldAttachRuntimeGenerationToHelperRequestsAndResults() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    AtomicReference<CommandResult> observed = new AtomicReference<CommandResult>();
    commandService.registerGlobalResultListener(observed::set);
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("runtimeGeneration", Long.valueOf(7L));
    params.put(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY, "helper-a");
    params.put(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY, Long.valueOf(1000L));
    params.put(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY, "refresh-1");

    java.util.concurrent.CompletableFuture<CommandResult> future = commandService.exec(
        "crm",
        Commands.REFRESH_METADATA.getCommand(),
        params,
        5000L
    );

    CommandRequest request = (CommandRequest) sentMessages.get(0).getPayload();
    assertEquals(Long.valueOf(7L), request.getRuntimeGeneration());
    assertEquals("helper-a", request.getHelperInstanceId());
    assertEquals(Long.valueOf(1000L), request.getHelperStartedAtEpochMillis());
    assertEquals("refresh-1", request.getRefreshOperationId());

    CommandResult result = new CommandResult(request, "ok");
    String serializedResult = Json.toJsonString(result);
    CommandResult decoded = Json.toBean(CommandResult.class, serializedResult);
    assertEquals(request.getSequenceId(), decoded.getSequenceId());
    assertEquals(true, decoded.getSuccess());
    resultHandler.handleMessage(MessageBuilder.withPayload(serializedResult.getBytes(StandardCharsets.UTF_8)).build());

    assertEquals(request.getSequenceId(), observed.get().getSequenceId());
    assertEquals(0, pendingResultCount(commandService, "SEQ_COMMAND_RESULTS"));
    CommandResult completed = future.get(1, TimeUnit.SECONDS);
    assertEquals(true, completed.getSuccess());
    assertEquals(Long.valueOf(7L), completed.getRuntimeGeneration());
    assertEquals("helper-a", completed.getHelperInstanceId());
    assertEquals(Long.valueOf(1000L), completed.getHelperStartedAtEpochMillis());
    assertEquals("refresh-1", completed.getRefreshOperationId());
  }

  @Test
  void shouldRemoveBoundFuturesWhenCommandSendFails() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    sendFailure = new IllegalStateException("mq unavailable");

    assertThrows(IllegalStateException.class, () -> commandService.exec(
        "crm",
        Commands.REFRESH_METADATA.getCommand(),
        new LinkedHashMap<String, Object>(),
        1000L
    ));
    assertThrows(IllegalStateException.class, () -> commandService.execRough(
        "crm",
        Commands.KNOCK_KNOCK.getCommand(),
        new LinkedHashMap<String, Object>(),
        1000L
    ));

    assertEquals(0, pendingResultCount(commandService, "SEQ_COMMAND_RESULTS"));
    assertEquals(0, pendingResultCount(commandService, "COMMAND_RESULTS"));
  }

  @Test
  void shouldCompleteThroughTheResultPipelineWhenAResponseIsLost() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    AtomicReference<CommandResult> observed = new AtomicReference<CommandResult>();
    commandService.registerCommandResultListener(
        Commands.REFRESH_METADATA.getCommand(),
        observed::set
    );
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("runtimeGeneration", Long.valueOf(7L));
    params.put(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY, "helper-a");
    params.put(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY, Long.valueOf(1000L));
    params.put(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY, "incarnation-a");
    params.put(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY, "refresh-1");

    java.util.concurrent.CompletableFuture<CommandResult> future = commandService.exec(
        "crm",
        Commands.REFRESH_METADATA.getCommand(),
        params,
        20L
    );

    CommandResult result = future.get(1, TimeUnit.SECONDS);
    assertFalse(result.getSuccess());
    assertEquals(CommandFailureType.RESPONSE_TIMEOUT, result.getFailureType());
    assertEquals("crm", result.getAppName());
    assertEquals(Long.valueOf(7L), result.getRuntimeGeneration());
    assertEquals("helper-a", result.getHelperInstanceId());
    assertEquals(Long.valueOf(1000L), result.getHelperStartedAtEpochMillis());
    assertEquals("incarnation-a", result.getAppIncarnationId());
    assertEquals("refresh-1", result.getRefreshOperationId());
    assertEquals(result, observed.get());
    assertEquals(0, pendingResultCount(commandService, "SEQ_COMMAND_RESULTS"));
  }

  @Test
  void wrongSourceDoesNotClaimPendingRequestBeforeTheValidResponderArrives() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    var observed = new AtomicReference<CommandResult>();
    commandService.registerGlobalResultListener(observed::set);
    var future = commandService.exec("crm", "captureStatus", Map.of(
        "profile", "test", "helperInstanceId", "expected", "helperStartedAtEpochMillis", 1000L,
        "appIncarnationId", "incarnation", "runtimeGeneration", 3L), 5000);
    var request = (CommandRequest) sentMessages.getFirst().getPayload();
    var wrong = new CommandResult(request, "wrong").setHelperInstanceId("old-process");
    resultHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(wrong).getBytes(StandardCharsets.UTF_8)).build());
    assertFalse(future.isDone());
    org.junit.jupiter.api.Assertions.assertNull(observed.get());
    var valid = new CommandResult(request, "valid");
    resultHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(valid).getBytes(StandardCharsets.UTF_8)).build());
    assertEquals("valid", future.get(1, TimeUnit.SECONDS).getData());
    assertEquals("expected", observed.get().getHelperInstanceId());
  }

  @Test
  void legacyRoughResponsesCompleteActiveWaitersAndRespectListenerRemoval() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    var global = new ArrayList<CommandResult>();
    var specific = new ArrayList<CommandResult>();
    java.util.function.Consumer<CommandResult> globalListener = global::add;
    java.util.function.Consumer<CommandResult> specificListener = specific::add;
    commandService.registerGlobalResultListener(globalListener);
    commandService.registerCommandResultListener("RefreshMetadata", specificListener);
    var cancelled = commandService.execRough("crm", "refreshMetadata", null, 5000);
    cancelled.cancel(false);
    var active = commandService.execRough("crm", "refreshMetadata", null, 5000);
    var request = (CommandRequest) sentMessages.getLast().getPayload();
    deliver(new CommandResult(request, "stream-only").setChannelName("other-channel"));
    assertFalse(active.isDone());
    deliver(new CommandResult(request, "metadata"));
    assertEquals("metadata", active.get(1, TimeUnit.SECONDS).getData());
    assertEquals(2, specific.size());
    assertEquals(2, global.size());
    commandService.unregisterGlobalResultListener(globalListener);
    commandService.unregisterCommandResultListener("REFRESHMETADATA", specificListener);
    deliver(new CommandResult(request, "later-broadcast"));
    assertEquals(2, specific.size());
    assertEquals(2, global.size());
  }

  @Test
  void synchronousResponsesCompleteBeforeDeadlineRegistrationAndKeepParsedIdentity() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    sendAction = message -> deliver(new CommandResult((CommandRequest) message.getPayload(), "immediate"));
    var future = commandService.exec("crm", "refreshMetadata", Map.of(
        "runtimeGeneration", "7", "helperStartedAtEpochMillis", "1000", "profile", "test"), 5000);
    var result = future.get(1, TimeUnit.SECONDS);
    assertEquals("immediate", result.getData());
    assertEquals(Long.valueOf(7), result.getRuntimeGeneration());
    assertEquals(Long.valueOf(1000), result.getHelperStartedAtEpochMillis());
    assertEquals("test", result.getProfile());
    var legacy = commandService.exec("crm", "knockKnock", Map.of(
        "runtimeGeneration", "legacy", "helperStartedAtEpochMillis", "unknown"), 5000).get(1, TimeUnit.SECONDS);
    org.junit.jupiter.api.Assertions.assertNull(legacy.getRuntimeGeneration());
    org.junit.jupiter.api.Assertions.assertNull(legacy.getHelperStartedAtEpochMillis());
    assertEquals("immediate", legacy.getData());
  }

  @Test
  void listenerFailureStillCompletesTheCorrectPendingFuture() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    var future = commandService.exec("crm", "captureStatus", Map.of(), 5000);
    commandService.registerGlobalResultListener(result -> { throw new IllegalStateException("listener failed"); });
    var request = (CommandRequest) sentMessages.getFirst().getPayload();
    assertThrows(IllegalStateException.class, () -> deliver(new CommandResult(request, "capturing")));
    assertEquals("capturing", future.get(1, TimeUnit.SECONDS).getData());
  }

  @Test
  void malformedBroadcastResponseIsIgnoredWithoutDisturbingCommandState() throws Exception {
    commandService = new DefaultDevHelperCommandService(new MqDevHelperClientTransport(mqBridge));
    var observed = new ArrayList<CommandResult>();
    commandService.registerGlobalResultListener(observed::add);
    var pending = commandService.exec("crm", "captureStatus", Map.of(), 5000);
    resultHandler.handleMessage(MessageBuilder.withPayload("null".getBytes(StandardCharsets.UTF_8)).build());
    resultHandler.handleMessage(MessageBuilder.withPayload("invalid payload type").build());
    assertFalse(pending.isDone());
    org.junit.jupiter.api.Assertions.assertTrue(observed.isEmpty());
    var request = (CommandRequest) sentMessages.getFirst().getPayload();
    deliver(new CommandResult(request, "ready"));
    assertEquals("ready", pending.get(1, TimeUnit.SECONDS).getData());
    assertEquals(1, observed.size());
  }

  private void deliver(CommandResult result) {
    resultHandler.handleMessage(MessageBuilder.withPayload(Json.toJsonString(result).getBytes(StandardCharsets.UTF_8)).build());
  }

  private int pendingResultCount(DefaultDevHelperCommandService commandService, String fieldName) throws Exception {
    Field field = DefaultDevHelperCommandService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    Map<?, ?> pendingResults = (Map<?, ?>) field.get(commandService);
    return pendingResults.size();
  }
}
