package com.ouroboros.websocket.broker.redis;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.ouroboros.websocket.config.WebSocketBrokerProperties;

public class RedisRegistryHeartbeat {

  private final RedisDistributedSessionRegistry registry;
  private final WebSocketBrokerProperties.RedisProperties props;
  private final ScheduledExecutorService scheduler;

  public RedisRegistryHeartbeat(
      RedisDistributedSessionRegistry registry,
      WebSocketBrokerProperties.RedisProperties props) {
    this.registry = registry;
    this.props = props;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "websocket-redis-registry-heartbeat");
      thread.setDaemon(true);
      return thread;
    });
  }

  @PostConstruct
  public void start() {
    long intervalMillis = props.getHeartbeatInterval().toMillis();
    scheduler.scheduleAtFixedRate(
        this::renewAllSessions,
        intervalMillis,
        intervalMillis,
        TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stop() {
    scheduler.shutdownNow();
  }

  private void renewAllSessions() {
    registry.renewLocalSessions();
  }
}
