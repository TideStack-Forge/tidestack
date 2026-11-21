package com.ouroboros.dev.app.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.websocket.OuroborosWebSocketAutoConfiguration;
import com.ouroboros.util.Json;

/** Exercises the actual STOMP interceptor, user routing, broker and compensation registrar together. */
@SpringBootTest(classes = CollaborationRealtimeTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollaborationRealtimeTest {
  @LocalServerPort int port;
  @Autowired CollaborationGateway gateway;

  @Configuration(proxyBeanMethods = false)
  @Import({ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
      WebMvcAutoConfiguration.class, WebSocketServletAutoConfiguration.class, JacksonAutoConfiguration.class,
      OuroborosWebSocketAutoConfiguration.class, CollaborationGateway.class})
  static class Config {
    @Bean TokenService tokenService() { return mock(TokenService.class); }
    @Bean UserCenter userCenter() { return mock(UserCenter.class); }
    @Bean AppEditAccessEvaluator access() {
      var access = mock(AppEditAccessEvaluator.class);
      when(access.canView(eq("app"), anyString())).thenReturn(true);
      when(access.canEdit("app", "alice")).thenReturn(true);
      return access;
    }
    @Bean CollaborativeDocumentService documents() {
      var documents = mock(CollaborativeDocumentService.class);
      when(documents.list("app", "canvas")).thenReturn(List.of());
      return documents;
    }
  }

  @Test
  void twoAuthenticatedSessionsReceiveInitialSnapshotsAndTheSameLiveChange() throws Exception {
    // Authenticate the existing CONNECT path with ordinary platform token/user collaborators.
    for (var user : List.of("alice", "bob")) {
      when(tokenService.verifyAccessToken(user)).thenReturn(io.vavr.control.Either.right(
          new com.ouroboros.security.token.TokenInfo(user, "session", java.util.Map.of(), Long.MAX_VALUE)));
      when(userCenter.getUser(user)).thenReturn(Optional.of((User) () -> user));
    }
    try (var alice = new Peer("alice"); var bob = new Peer("bob")) {
      assertTrue(alice.initial().contains("\"writable\":true"));
      assertTrue(bob.initial().contains("\"writable\":false"));
      var document = new CollaborativeDocumentService.Document(CollaborativeDocumentService.FORMAT,
          "overview", 2, "{}", "AAE=", List.of());
      gateway.publish("app", "canvas", document);
      assertTrue(alice.messages.poll(5, TimeUnit.SECONDS).contains("\"revision\":2"));
      assertTrue(bob.messages.poll(5, TimeUnit.SECONDS).contains("\"revision\":2"));
    }
  }

  @Autowired TokenService tokenService;
  @Autowired UserCenter userCenter;

  private class Peer implements AutoCloseable {
    final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final LinkedBlockingQueue<String> initial = new LinkedBlockingQueue<>();
    final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
    final WebSocketStompClient client;
    final StompSession session;

    Peer(String user) throws Exception {
      client = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
      client.setMessageConverter(new CompositeMessageConverter(List.of(new MappingJackson2MessageConverter(), new StringMessageConverter())));
      var headers = new StompHeaders();
      headers.add("token", user);
      session = client.connectAsync("http://127.0.0.1:" + port + "/websocket/stomp",
          new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { errors.add(payload.toString()); }
            @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable error) {
              errors.add(error.toString());
            }
          }).get(10, TimeUnit.SECONDS);
      var live = "/user/queue/app/app/collaboration/canvas";
      session.subscribe(live, handler(messages));
      session.subscribe("/queue/init" + live + "/" + user, handler(initial));
      var initHeaders = new StompHeaders();
      initHeaders.setDestination("/app" + live + "/init");
      initHeaders.add("subscriptionToken", user);
      session.send(initHeaders, java.util.Map.of());
    }

    String initial() throws Exception {
      var snapshot = initial.poll(5, TimeUnit.SECONDS);
      assertNotNull(snapshot, () -> "STOMP errors " + errors);
      var complete = initial.poll(5, TimeUnit.SECONDS);
      assertNotNull(complete);
      assertTrue(complete.contains("init-complete"));
      return snapshot;
    }

    private StompFrameHandler handler(LinkedBlockingQueue<String> target) {
      return new StompFrameHandler() {
        public Type getPayloadType(StompHeaders headers) { return java.util.Map.class; }
        public void handleFrame(StompHeaders headers, Object payload) { target.add(Json.toJsonString(payload)); }
      };
    }

    public void close() { session.disconnect(); client.stop(); }
  }
}
