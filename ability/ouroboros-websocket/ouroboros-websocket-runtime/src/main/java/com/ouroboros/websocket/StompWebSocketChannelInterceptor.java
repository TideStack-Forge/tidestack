package com.ouroboros.websocket;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;

@Component
public class StompWebSocketChannelInterceptor implements ChannelInterceptor {

  private final TokenService tokenService;
  private final UserCenter userCenter;

  @Autowired
  @Lazy
  private StompMessageSender stompMessageSender;

  @Autowired(required = false)
  private ObjectProvider<StompDestinationAuthorizer> destinationAuthorizers;

  public StompWebSocketChannelInterceptor(TokenService tokenService, UserCenter userCenter) {
    this.tokenService = tokenService;
    this.userCenter = userCenter;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (destinationAuthorizers != null) {
      destinationAuthorizers.orderedStream().forEach(authorizer -> authorizer.authorize(accessor));
    }
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      updateUserByHeader(accessor);
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && accessor.getDestination().startsWith("/queue/")) {
      String sessionId = accessor.getSessionId();
      accessor.setDestination(accessor.getDestination() + "-" + sessionId);
      return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
    return message;
  }

  @Override
  public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
        && accessor.getDestination().startsWith("/queue/authenticated")) {
      var user = accessor.getUser();
      if (user != null) {
        stompMessageSender.sendToQueue(accessor.getSessionId(), "/authenticated", user.getName());
      }
    }
  }

  private void updateUserByHeader(StompHeaderAccessor accessor) {
    String token = Optional.ofNullable(accessor.getFirstNativeHeader("token"))
        .orElseGet(() -> accessor.getFirstNativeHeader("Authorization"));
    if (StringUtils.isBlank(token)) {
      return;
    }
    tokenService.verifyAccessToken(token)
        .toJavaOptional()
        .flatMap(tokenInfo -> userCenter.getUser(tokenInfo.getUserIdentity()))
        .map(DefaultAuthentication::new)
        .ifPresent(accessor::setUser);
  }
}
