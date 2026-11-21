package com.ouroboros.websocket.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ouroboros.websocket.WebSocketBrokerType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ouroboros.websocket")
public class WebSocketBrokerProperties {
  @NotNull
  private WebSocketBrokerType brokerType = WebSocketBrokerType.SIMPLE;

  @NotBlank
  private String endpointPath = "/websocket/stomp";

  @NotBlank
  private String allowedOriginPatterns = "*";

  private boolean sockjsEnabled = true;

  private String instanceId = java.util.UUID.randomUUID().toString();

  @Valid
  @NotNull
  private RabbitMqProperties rabbitmq = new RabbitMqProperties();

  @Valid
  @NotNull
  private RedisProperties redis = new RedisProperties();

  public WebSocketBrokerType getBrokerType() {
    return brokerType;
  }

  public void setBrokerType(WebSocketBrokerType brokerType) {
    this.brokerType = brokerType;
  }

  public String getEndpointPath() {
    return endpointPath;
  }

  public void setEndpointPath(String endpointPath) {
    this.endpointPath = endpointPath;
  }

  public String getAllowedOriginPatterns() {
    return allowedOriginPatterns;
  }

  public void setAllowedOriginPatterns(String allowedOriginPatterns) {
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  public boolean isSockjsEnabled() {
    return sockjsEnabled;
  }

  public void setSockjsEnabled(boolean sockjsEnabled) {
    this.sockjsEnabled = sockjsEnabled;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public RabbitMqProperties getRabbitmq() {
    return rabbitmq;
  }

  public void setRabbitmq(RabbitMqProperties rabbitmq) {
    this.rabbitmq = rabbitmq;
  }

  public RedisProperties getRedis() {
    return redis;
  }

  public void setRedis(RedisProperties redis) {
    this.redis = redis;
  }

  public static class RabbitMqProperties {
    @NotBlank
    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 61613;

    @NotBlank
    private String virtualHost = "/";

    @NotBlank
    private String systemLogin = "system";

    private String systemPasscode;

    @NotBlank
    private String clientLogin = "client";

    private String clientPasscode;

    @NotBlank
    private String userDestinationBroadcast = "/topic/user-destination";

    @NotBlank
    private String userRegistryBroadcast = "/topic/user-registry";

    @Min(0)
    private long systemHeartbeatSendInterval = 10000;

    @Min(0)
    private long systemHeartbeatReceiveInterval = 10000;

    @NotBlank
    private String registryExchange = "websocket.registry";

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getVirtualHost() {
      return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
      this.virtualHost = virtualHost;
    }

    public String getSystemLogin() {
      return systemLogin;
    }

    public void setSystemLogin(String systemLogin) {
      this.systemLogin = systemLogin;
    }

    public String getSystemPasscode() {
      return systemPasscode;
    }

    public void setSystemPasscode(String systemPasscode) {
      this.systemPasscode = systemPasscode;
    }

    public String getClientLogin() {
      return clientLogin;
    }

    public void setClientLogin(String clientLogin) {
      this.clientLogin = clientLogin;
    }

    public String getClientPasscode() {
      return clientPasscode;
    }

    public void setClientPasscode(String clientPasscode) {
      this.clientPasscode = clientPasscode;
    }

    public String getUserDestinationBroadcast() {
      return userDestinationBroadcast;
    }

    public void setUserDestinationBroadcast(String userDestinationBroadcast) {
      this.userDestinationBroadcast = userDestinationBroadcast;
    }

    public String getUserRegistryBroadcast() {
      return userRegistryBroadcast;
    }

    public void setUserRegistryBroadcast(String userRegistryBroadcast) {
      this.userRegistryBroadcast = userRegistryBroadcast;
    }

    public long getSystemHeartbeatSendInterval() {
      return systemHeartbeatSendInterval;
    }

    public void setSystemHeartbeatSendInterval(long systemHeartbeatSendInterval) {
      this.systemHeartbeatSendInterval = systemHeartbeatSendInterval;
    }

    public long getSystemHeartbeatReceiveInterval() {
      return systemHeartbeatReceiveInterval;
    }

    public void setSystemHeartbeatReceiveInterval(long systemHeartbeatReceiveInterval) {
      this.systemHeartbeatReceiveInterval = systemHeartbeatReceiveInterval;
    }

    public String getRegistryExchange() {
      return registryExchange;
    }

    public void setRegistryExchange(String registryExchange) {
      this.registryExchange = registryExchange;
    }
  }

  public static class RedisProperties {
    @NotBlank
    private String topicBroadcastChannel = "websocket:topic";

    @NotBlank
    private String userBroadcastChannel = "websocket:user";

    @NotBlank
    private String sessionBroadcastChannel = "websocket:session";

    @NotBlank
    private String userRegistryChannel = "websocket:user-registry";

    @NotBlank
    private String registryKeyPrefix = "websocket:registry";

    @NotNull
    private Duration registryEntryTtl = Duration.ofSeconds(60);

    @NotNull
    private Duration heartbeatInterval = Duration.ofSeconds(20);

    private String instanceId;

    public String getTopicBroadcastChannel() {
      return topicBroadcastChannel;
    }

    public void setTopicBroadcastChannel(String topicBroadcastChannel) {
      this.topicBroadcastChannel = topicBroadcastChannel;
    }

    public String getUserBroadcastChannel() {
      return userBroadcastChannel;
    }

    public void setUserBroadcastChannel(String userBroadcastChannel) {
      this.userBroadcastChannel = userBroadcastChannel;
    }

    public String getSessionBroadcastChannel() {
      return sessionBroadcastChannel;
    }

    public void setSessionBroadcastChannel(String sessionBroadcastChannel) {
      this.sessionBroadcastChannel = sessionBroadcastChannel;
    }

    public String getUserRegistryChannel() {
      return userRegistryChannel;
    }

    public void setUserRegistryChannel(String userRegistryChannel) {
      this.userRegistryChannel = userRegistryChannel;
    }

    public String getRegistryKeyPrefix() {
      return registryKeyPrefix;
    }

    public void setRegistryKeyPrefix(String registryKeyPrefix) {
      this.registryKeyPrefix = registryKeyPrefix;
    }

    public Duration getRegistryEntryTtl() {
      return registryEntryTtl;
    }

    public void setRegistryEntryTtl(Duration registryEntryTtl) {
      this.registryEntryTtl = registryEntryTtl;
    }

    public Duration getHeartbeatInterval() {
      return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
      this.heartbeatInterval = heartbeatInterval;
    }

    public String getInstanceId() {
      return instanceId;
    }

    public void setInstanceId(String instanceId) {
      this.instanceId = instanceId;
    }
  }
}
