package com.ouroboros.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public class DefaultWebSocketStompMessageSubscriber implements StompMessageSubscriber {
  private final SubscribableChannel clientInboundChannel;
  private final ConcurrentMap<MessageHandler, MessageHandler> messageHandlerMap;

  public DefaultWebSocketStompMessageSubscriber(
      @Qualifier("clientInboundChannel") SubscribableChannel clientInboundChannel) {
    this.clientInboundChannel = clientInboundChannel;
    this.messageHandlerMap = new ConcurrentHashMap<>();
  }

  @Override
  public void onSend(String destination, MessageHandler messageHandler) {
    var wrappedHandler = new WrappedMessageHandler(destination, StompCommand.SEND, messageHandler);
    messageHandlerMap.put(messageHandler, wrappedHandler);
    clientInboundChannel.subscribe(wrappedHandler);
  }

  public void unsubscribe(MessageHandler messageHandler) {
    var wrappedHandler = messageHandlerMap.remove(messageHandler);
    clientInboundChannel.unsubscribe(wrappedHandler);
  }

  public static class WrappedMessageHandler implements MessageHandler {
    private final String destination;
    private final PathPattern pathPattern;
    private final MessageHandler messageHandler;
    private final StompCommand command;

    public WrappedMessageHandler(String destination, StompCommand command, MessageHandler messageHandler) {
      this.destination = destination;
      this.command = command;
      this.pathPattern = PathPatternParser.defaultInstance.parse(destination);
      this.messageHandler = messageHandler;
    }

    @Override
    public void handleMessage(Message<?> message) {
      var accessor = StompHeaderAccessor.wrap(message);
      var destination = accessor.getDestination();

      if (!command.equals(accessor.getCommand())) {
        return;
      }
      if (destination == null || !pathPattern.matches(PathContainer.parsePath(destination))) {
        return;
      }

      doHandleMessage(message);
    }

    protected void doHandleMessage(Message<?> message) {
      messageHandler.handleMessage(message);
    }

    @Override
    public String toString() {
      return "WrappedMessageHandler{" +
          "destination='" + destination + '\'' +
          ", messageHandler=" + messageHandler +
          '}';
    }
  }
}
