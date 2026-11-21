package com.ouroboros.websocket.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.websocket.StompMessageSender;
import com.ouroboros.websocket.BrokerHealthStatus;
import com.ouroboros.websocket.DistributedSessionRegistry;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.broker.redis.RedisDistributedSessionRegistry;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import io.vavr.control.Either;

/** LF-DBG-PRIVATE-001: real STOMP/Redis; same application name, distinct default process identities. */
@EnabledIfSystemProperty(named = "logicflow.redis.port", matches = "[0-9]+")
class RedisDefaultIdentityPrivateDeliveryIT {
  @Test
  void privateHintReachesSameOwnerOnTwoReplicasAndNeverOtherOwnerOrPublicTopic() throws Exception {
    String scope = "logicflow-ws-it-" + UUID.randomUUID();
    try (var first = start(scope); var second = start(scope)) {
      assertNotEquals(first.getBean(WebSocketBrokerProperties.class).getInstanceId(), second.getBean(WebSocketBrokerProperties.class).getInstanceId());
      int firstPort = ((WebServerApplicationContext) first).getWebServer().getPort();
      int secondPort = ((WebServerApplicationContext) second).getWebServer().getPort();
      var local = new WebSocketTestClient("http://127.0.0.1:" + firstPort + "/websocket/stomp");
      var remote = new WebSocketTestClient("http://127.0.0.1:" + secondPort + "/websocket/stomp");
      var other = new WebSocketTestClient("http://127.0.0.1:" + secondPort + "/websocket/stomp");
      try {
        local.connect("alice"); remote.connect("alice"); other.connect("bob");
        String queue = "/queue/dev/logicflow/debug/capture";
        local.subscribe("/user" + queue); remote.subscribe("/user" + queue); other.subscribe("/user" + queue);
        other.subscribe("/topic/dev/crm/logicflow/debug");
        Thread.sleep(500);
        first.getBean(StompMessageSender.class).sendToUser("alice", queue, "received:3");
        assertEquals("received:3", local.waitForMessage(5));
        assertEquals("received:3", remote.waitForMessage(5));
        assertNull(other.waitForMessage(1));
        assertNull(local.waitForMessage(1));
        second.getBean(StompMessageSender.class).sendToUser("alice", queue, "received:4");
        assertEquals("received:4", local.waitForMessage(5));
        assertEquals("received:4", remote.waitForMessage(5));
        local.subscribe("/topic/compatibility"); remote.subscribe("/topic/compatibility");
        Thread.sleep(300);
        first.getBean(StompMessageSender.class).send("/topic/compatibility", "public-update");
        assertEquals("public-update", local.waitForMessage(5));
        assertEquals("public-update", remote.waitForMessage(5));
        assertNull(remote.waitForMessage(1));
      } finally { local.disconnect(); remote.disconnect(); other.disconnect(); }
    }
  }

  @Test
  void sharedRegistryTracksDefaultProcessIdentitiesAndRemovesDisconnectedRoutes() {
    String scope = "logicflow-ws-registry-" + UUID.randomUUID();
    try (var first = start(scope); var second = start(scope)) {
      var local = first.getBean(RedisDistributedSessionRegistry.class);
      var peer = second.getBean(DistributedSessionRegistry.class);
      String firstId = first.getBean(WebSocketBrokerProperties.class).getInstanceId();
      String secondId = second.getBean(WebSocketBrokerProperties.class).getInstanceId();
      local.registerSession("first", "alice", firstId);
      peer.registerSession("second", "alice", secondId);
      try {
        var route = peer.findSession("first").orElseThrow();
        assertEquals("first", route.getSessionId());
        assertEquals("alice", route.getUserId());
        assertEquals(firstId, route.getInstanceId());
        assertNotNull(route.getConnectedAt());
        assertEquals(2, local.findUserSessions("alice").size());
        local.renewLocalSessions();
        var health = first.getBean(WebSocketBrokerStrategy.class).getHealthInfo();
        assertEquals(BrokerHealthStatus.UP, health.getStatus());
        assertEquals(firstId, health.getDetails().get("instanceId"));
        assertEquals(1, health.getDetails().get("registrySize"));
        local.unregisterSession("first");
        assertTrue(peer.findSession("first").isEmpty());
        assertEquals(1, peer.findUserSessions("alice").size());
        assertEquals(0, first.getBean(WebSocketBrokerStrategy.class).getHealthInfo().getDetails().get("registrySize"));
      } finally {
        local.unregisterSession("first");
        peer.unregisterSession("second");
      }
      assertTrue(local.findUserSessions("alice").isEmpty());
    }
  }

  private org.springframework.context.ConfigurableApplicationContext start(String scope) {
    return new SpringApplication(IntegrationTestWebSocketApplication.class).run(
        "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "--server.port=0", "--spring.main.banner-mode=off", "--spring.application.name=identical-develop",
        "--ouroboros.websocket.broker-type=redis-pubsub", "--spring.data.redis.host=127.0.0.1",
        "--spring.data.redis.port=" + System.getProperty("logicflow.redis.port"),
        "--ouroboros.websocket.redis.topic-broadcast-channel=" + scope + ":topic",
        "--ouroboros.websocket.redis.user-broadcast-channel=" + scope + ":user",
        "--ouroboros.websocket.redis.session-broadcast-channel=" + scope + ":session",
        "--ouroboros.websocket.redis.registry-key-prefix=" + scope);
  }

}
