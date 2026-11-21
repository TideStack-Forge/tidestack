package com.ouroboros.devhelper.transport.mq;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperServerTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.util.Json;

/** Adapts the existing helper MQ bindings without changing the broker protocol. */
public class MqDevHelperServerTransport implements DevHelperServerTransport {
  private static final Logger logger = LoggerFactory.getLogger(MqDevHelperServerTransport.class);
  private static final String COMMAND_CHANNEL = "ouroboros-dev-helper-command";
  private static final String RESPONSE_CHANNEL = "ouroboros-dev-helper-result";
  private static final int MAX_COMMAND_BYTES = 262144;

  private final MQBridge mqBridge;

  public MqDevHelperServerTransport(MQBridge mqBridge) {
    this.mqBridge = mqBridge;
    mqBridge.bindingProducer(RESPONSE_CHANNEL)
        .destination("ouroboros.dev-helper.response")
        .extraProp("declareExchange", true)
        .build();
    mqBridge.bindingConsumer(COMMAND_CHANNEL)
        .destination("ouroboros.dev-helper.command")
        .broadcast(true)
        .build();
  }

  @Override
  public void send(CommandResult result) {
    mqBridge.send(RESPONSE_CHANNEL, MessageBuilder.withPayload(result).build());
  }

  @Override
  public TransportSubscription subscribe(Consumer<CommandRequest> receiver) {
    MessageHandler handler = message -> {
      if (!(message.getPayload() instanceof byte[] bytes) || bytes.length > MAX_COMMAND_BYTES) {
        logger.warn("Rejected invalid helper command payload");
        return;
      }
      CommandRequest request = Json.toBean(CommandRequest.class, new String(bytes, StandardCharsets.UTF_8));
      if (request == null || request.getCommand() == null) {
        logger.warn("Rejected malformed helper command");
        return;
      }
      receiver.accept(request);
    };
    mqBridge.subscribe(COMMAND_CHANNEL, handler);
    return () -> mqBridge.unsubscribe(COMMAND_CHANNEL, handler);
  }
}
