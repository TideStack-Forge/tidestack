package com.ouroboros.websocket.integration;

import java.util.Optional;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenPair;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.websocket.StompMessageSender;

import io.vavr.control.Either;
import io.vavr.control.Try;

@SpringBootApplication
class IntegrationTestWebSocketApplication {

  @Bean
  TokenService tokenService() {
    return new TokenService() {
      @Override
      public TokenPair buildTokenPair(java.util.Map<String, Object> accessTokenClaims,
                                      java.util.Map<String, Object> refreshTokenClaims) {
        throw new UnsupportedOperationException("Not used in websocket integration tests");
      }

      @Override
      public TokenPair buildTokenPair(java.util.Map<String, Object> accessTokenClaims,
                                      long accessTokenExpireSeconds,
                                      java.util.Map<String, Object> refreshTokenClaims,
                                      long refreshTokenExpireSeconds) {
        throw new UnsupportedOperationException("Not used in websocket integration tests");
      }

      @Override
      public Either<com.ouroboros.security.exception.TokenException, TokenInfo> verifyAccessToken(String token) {
        return Either.right(new TokenInfo(token, "test-session", java.util.Collections.emptyMap(), System.currentTimeMillis() + 3600000));
      }

      @Override
      public Either<com.ouroboros.security.exception.TokenException, TokenInfo> verifyRefreshToken(String token) {
        return Either.right(new TokenInfo(token, "test-session", java.util.Collections.emptyMap(), System.currentTimeMillis() + 3600000));
      }

      @Override
      public Try<Boolean> revokeAccessToken(String token) {
        return Try.success(Boolean.TRUE);
      }

      @Override
      public Try<Boolean> revokeRefreshToken(String token) {
        return Try.success(Boolean.TRUE);
      }
    };
  }

  @Bean
  UserCenter userCenter() {
    return new UserCenter() {
      @Override
      public Optional<User> getUser(String userIdentity) {
        return Optional.of((User) () -> userIdentity);
      }

      @Override
      public java.util.List<User> getUsers(java.util.Collection<String> userIdentities) {
        return java.util.Collections.emptyList();
      }

      @Override
      public java.util.List<User> getAllUsers() {
        return java.util.Collections.emptyList();
      }
    };
  }

  @Controller
  static class BroadcastController {
    private final StompMessageSender stompMessageSender;

    BroadcastController(StompMessageSender stompMessageSender) {
      this.stompMessageSender = stompMessageSender;
    }

    @MessageMapping("/broadcast")
    public void broadcast(String message) {
      stompMessageSender.send("/topic/integration", message);
    }
  }
}
