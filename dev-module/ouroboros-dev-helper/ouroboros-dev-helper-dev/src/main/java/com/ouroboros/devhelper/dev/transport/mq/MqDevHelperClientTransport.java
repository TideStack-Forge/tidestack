package com.ouroboros.devhelper.dev.transport.mq;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.util.Json;

/** Adapts the existing platform MQ bindings without changing the broker protocol. */
public class MqDevHelperClientTransport implements DevHelperClientTransport {
  private static final Logger logger = LoggerFactory.getLogger(MqDevHelperClientTransport.class);
  private final MQProducer commandChannel;
  private final MQConsumer resultChannel;

  public MqDevHelperClientTransport(MQBridge mqBridge) {
    commandChannel = mqBridge.bindingProducer("ouroboros-dev-helper-command-client")
        .destination("ouroboros.dev-helper.command")
        .extraProp("declareExchange", true)
        .extraProp("exchangeType", "topic")
        .build();
    resultChannel = mqBridge.bindingConsumer("ouroboros-dev-helper-response-client")
        .destination("ouroboros.dev-helper.response")
        .broadcast(true)
        .build();
  }

  @Override
  public void send(CommandRequest request) {
    commandChannel.send(MessageBuilder.withPayload(request).build());
  }

  @Override
  public TransportSubscription subscribe(Consumer<CommandResult> receiver) {
    MessageHandler handler = message -> {
      if (!(message.getPayload() instanceof byte[] bytes)) {
        logger.warn("Rejected invalid helper result payload");
        return;
      }
      CommandResult result = Json.toBean(CommandResult.class, new String(bytes, StandardCharsets.UTF_8));
      if (result == null) {
        logger.warn("Received null command result");
        return;
      }
      receiver.accept(result);
    };
    resultChannel.subscribe(handler);
    return () -> resultChannel.unsubscribe(handler);
  }
}
