package com.ouroboros.dev.app.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.websocket.StompCompensationRegistrar;

class CollaborationGatewayTest {
  @Test
  void checksLiveAndBootstrapSubscriptionsAndRejectsBrokerWrites() {
    var access = mock(AppEditAccessEvaluator.class);
    var gateway = new CollaborationGateway(mock(CollaborativeDocumentService.class), access,
        mock(StompCompensationRegistrar.class));
    for (var destination : List.of("/user/queue/app/app/collaboration/canvas",
        "/queue/init/user/queue/app/app/collaboration/canvas/token",
        "/app/user/queue/app/app/collaboration/canvas/init")) {
      var frame = frame(StompCommand.SUBSCRIBE, destination, "alice", "1");
      assertThrows(BusinessException.class, () -> gateway.authorize(frame));
      when(access.canView("app", "alice")).thenReturn(true);
      assertDoesNotThrow(() -> gateway.authorize(frame));
      when(access.canView("app", "alice")).thenReturn(false);
    }
    when(access.canView("app", "alice")).thenReturn(true);
    assertThrows(BusinessException.class, () -> gateway.authorize(
        frame(StompCommand.SEND, "/user/queue/app/app/collaboration/canvas", "alice", "2")));
    assertThrows(BusinessException.class, () -> gateway.requireEdit("app", "alice"));
    when(access.canEdit("app", "alice")).thenReturn(true);
    assertDoesNotThrow(() -> gateway.requireEdit("app", "alice"));
  }

  @Test
  void publishesDurableDocumentsOnlyToCurrentAuthorizedSubscribersAndCleansUpOnUnsubscribe() {
    var access = mock(AppEditAccessEvaluator.class);
    when(access.canView(eq("app"), anyString())).thenReturn(true);
    var registrar = mock(StompCompensationRegistrar.class);
    var registration = mock(StompCompensationRegistrar.UserTopicRegistration.class);
    var channel = mock(StompCompensationRegistrar.UserTopicChannel.class);
    when(registrar.userTopic(CollaborationGateway.DESTINATION)).thenReturn(registration);
    when(registration.initialData(any())).thenReturn(registration);
    when(registration.done()).thenReturn(channel);
    var gateway = new CollaborationGateway(mock(CollaborativeDocumentService.class), access, registrar);
    gateway.register();
    gateway.authorize(frame(StompCommand.SUBSCRIBE, "/user/queue/app/app/collaboration/canvas", "alice", "a"));
    gateway.authorize(frame(StompCommand.SUBSCRIBE, "/user/queue/app/app/collaboration/canvas", "bob", "b"));
    when(access.canView("app", "bob")).thenReturn(false);
    var document = new CollaborativeDocumentService.Document(CollaborativeDocumentService.FORMAT,
        "overview", 1, "{}", "AAE=", List.of());
    gateway.publish("app", "canvas", document);
    verify(channel).sendToUser(eq("alice"), eq(Map.of("appName", "app", "type", "canvas")), any());
    verify(channel, never()).sendToUser(eq("bob"), anyMap(), any());
    gateway.authorize(frame(StompCommand.UNSUBSCRIBE, null, "alice", "a"));
    clearInvocations(channel);
    gateway.publish("app", "canvas", document);
    verifyNoInteractions(channel);
  }

  private StompHeaderAccessor frame(StompCommand command, String destination, String user, String subscription) {
    var frame = StompHeaderAccessor.create(command);
    frame.setDestination(destination);
    frame.setUser(() -> user);
    frame.setSessionId(user);
    frame.setSubscriptionId(subscription);
    return frame;
  }
}
