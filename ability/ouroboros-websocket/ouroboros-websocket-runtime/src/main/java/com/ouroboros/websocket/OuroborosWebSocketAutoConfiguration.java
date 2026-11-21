package com.ouroboros.websocket;

import java.util.Collections;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketBrokerProperties.class)
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {OuroborosWebSocketAutoConfiguration.class, WebSocketBrokerStrategySelectionConfiguration.class}))
public class OuroborosWebSocketAutoConfiguration implements WebSocketMessageBrokerConfigurer {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosWebSocketAutoConfiguration.class);

  @Autowired
  private WebSocketBrokerProperties properties;

  @Autowired
  private ObjectProvider<WebSocketBrokerStrategy> brokerStrategyProvider;

  @Autowired
  @Lazy
  private StompWebSocketChannelInterceptor authInterceptor;


  public OuroborosWebSocketAutoConfiguration() {
  }

  public OuroborosWebSocketAutoConfiguration(StompWebSocketChannelInterceptor authInterceptor) {
    this.properties = new WebSocketBrokerProperties();
    this.brokerStrategyProvider = new SingletonObjectProvider<>(new RuntimeDefaultSimpleBrokerStrategy());
    this.authInterceptor = authInterceptor;
  }

  @PostConstruct
  void init() {
    logger.info("Ouroboros WebSocket Auto Configuration loaded");
  }

  // WebSocket 端点配置
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Bootstrap SEND must be handled after the preceding live/reply SUBSCRIBE frames.
    registry.setPreserveReceiveOrder(true);
    var endpoint = registry.addEndpoint(properties.getEndpointPath())
        .setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
    if (properties.isSockjsEnabled()) {
      endpoint.withSockJS();
    }
  }

  // STOMP 消息代理配置
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setPreservePublishOrder(true);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");

    WebSocketBrokerContext context = new DefaultWebSocketBrokerContext(properties);
    brokerStrategyProvider.getObject().configure(registry, context);
  }

  // 配置客户端入站通道拦截器（用于处理认证）
  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }

  // 配置匿名 API 提供者，以使 WebSocket 能够连接
  @Bean
  public AnonymousApiProvider webSocketAnonymousApiProvider() {
    return () -> Collections.singletonList("* /websocket/stomp/**");
  }

  @Bean
  @ConditionalOnMissingBean(InitReplySender.class)
  public InitReplySender initReplySender(StompMessageSender stompMessageSender) {
    return new DefaultInitReplySender(stompMessageSender);
  }

  @Bean
  @ConditionalOnMissingBean(DefaultInitRequestValidator.class)
  public DefaultInitRequestValidator initRequestValidator() {
    return new DefaultInitRequestValidator();
  }

  @Bean
  @ConditionalOnMissingBean(StompInitSupport.class)
  public StompInitSupport stompInitSupport(DefaultInitRequestValidator initRequestValidator) {
    return new DefaultStompInitSupport(initRequestValidator);
  }

  @Bean
  @ConditionalOnMissingBean(StompCompensationRegistrar.class)
  public StompCompensationRegistrar stompCompensationRegistrar(
      StompMessageSender stompMessageSender,
      StompMessageSubscriber stompMessageSubscriber,
      DefaultInitRequestValidator initRequestValidator) {
    return new DefaultStompCompensationRegistrar(stompMessageSubscriber, stompMessageSender, initRequestValidator);
  }

  private static final class RuntimeDefaultSimpleBrokerStrategy implements WebSocketBrokerStrategy {

    @Override
    public WebSocketBrokerType getType() {
      return WebSocketBrokerType.SIMPLE;
    }

    @Override
    public void configure(MessageBrokerRegistry registry, WebSocketBrokerContext context) {
      registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void send(BrokerEnvelope envelope, LocalWebSocketOperations localOperations) {
      throw new UnsupportedOperationException("RuntimeDefaultSimpleBrokerStrategy is test-only and must not send messages");
    }

    @Override
    public BrokerHealthInfo getHealthInfo() {
      return new BrokerHealthInfo(
          WebSocketBrokerType.SIMPLE,
          BrokerHealthStatus.UP,
          "Simple broker",
          Collections.emptyMap());
    }
  }

  private static final class SingletonObjectProvider<T> implements ObjectProvider<T> {
    private final T value;

    private SingletonObjectProvider(T value) {
      this.value = value;
    }

    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public java.util.Iterator<T> iterator() {
      return Collections.singleton(value).iterator();
    }

    @Override
    public java.util.stream.Stream<T> stream() {
      return Collections.singleton(value).stream();
    }

    @Override
    public java.util.stream.Stream<T> orderedStream() {
      return stream();
    }
  }
}
