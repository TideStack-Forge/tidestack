package com.ouroboros.mq;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractProducerCreatorTest {

  @Test
  void senderShouldFailWhenStreamBridgeRejectsTheMessage() {
    StreamBridge streamBridge = mock(StreamBridge.class);
    TestProducerCreator creator = new TestProducerCreator();
    ReflectionTestUtils.setField(creator, "streamBridge", streamBridge);
    Message<String> message = MessageBuilder.withPayload("payload").build();
    when(streamBridge.send(eq("orders"), any(Message.class))).thenReturn(false);

    assertThrows(IllegalStateException.class,
        () -> creator.sender("orders").accept(message));
  }

  private static final class TestProducerCreator extends AbstractProducerCreator {
    java.util.function.Consumer<Message<?>> sender(String bindingName) {
      return getSender(null, bindingName);
    }

    @Override
    protected BiConsumer<String, Message<?>> getGenderWithRoutingKey(
        Binder<?, ?, ?> binder,
        String bindingName
    ) {
      return (routingKey, message) -> {
      };
    }
  }
}
