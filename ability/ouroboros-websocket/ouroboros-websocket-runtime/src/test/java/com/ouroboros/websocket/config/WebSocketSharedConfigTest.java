package com.ouroboros.websocket.config;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.websocket.OuroborosWebSocketAutoConfiguration;
import com.ouroboros.websocket.StompWebSocketChannelInterceptor;
import com.ouroboros.websocket.WebSocketBrokerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = WebSocketSharedConfigTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebSocketSharedConfigTest {

  @Autowired
  private WebSocketBrokerProperties properties;

  @Test
  void shouldPreserveEndpointPathAcrossAllModes() {
    assertThat(properties.getEndpointPath()).isEqualTo("/websocket/stomp");
    assertSharedPropertyAcrossAllModes(candidate -> assertThat(candidate.getEndpointPath()).isEqualTo(properties.getEndpointPath()));

    StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
    StompWebSocketEndpointRegistration endpointRegistration = mock(StompWebSocketEndpointRegistration.class);
    when(registry.addEndpoint(properties.getEndpointPath())).thenReturn(endpointRegistration);
    when(endpointRegistration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns())).thenReturn(endpointRegistration);

    newAutoConfiguration(mock(StompWebSocketChannelInterceptor.class)).registerStompEndpoints(registry);

    verify(registry).addEndpoint("/websocket/stomp");
  }

  @Test
  void shouldPreserveSockJsConfigAcrossAllModes() {
    assertThat(properties.isSockjsEnabled()).isTrue();
    assertSharedPropertyAcrossAllModes(candidate -> assertThat(candidate.isSockjsEnabled()).isEqualTo(properties.isSockjsEnabled()));

    StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
    StompWebSocketEndpointRegistration endpointRegistration = mock(StompWebSocketEndpointRegistration.class);
    when(registry.addEndpoint(properties.getEndpointPath())).thenReturn(endpointRegistration);
    when(endpointRegistration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns())).thenReturn(endpointRegistration);

    newAutoConfiguration(mock(StompWebSocketChannelInterceptor.class)).registerStompEndpoints(registry);

    verify(endpointRegistration).withSockJS();
  }

  @Test
  void shouldPreserveApplicationPrefixAcrossAllModes() {
    MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

    newAutoConfiguration(mock(StompWebSocketChannelInterceptor.class)).configureMessageBroker(registry);

    verify(registry).setApplicationDestinationPrefixes("/app");
  }

  @Test
  void shouldPreserveUserDestinationPrefixAcrossAllModes() {
    MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

    newAutoConfiguration(mock(StompWebSocketChannelInterceptor.class)).configureMessageBroker(registry);

    verify(registry).setUserDestinationPrefix("/user");
  }

  @Test
  void shouldPreserveAuthInterceptorAcrossAllModes() {
    StompWebSocketChannelInterceptor interceptor = mock(StompWebSocketChannelInterceptor.class);
    ChannelRegistration registration = mock(ChannelRegistration.class);

    newAutoConfiguration(interceptor).configureClientInboundChannel(registration);

    verify(registration).interceptors(interceptor);
  }

  @Test
  void shouldPreserveAnonymousHandshakeAllowanceAcrossAllModes() {
    AnonymousApiProvider anonymousApiProvider = newAutoConfiguration(mock(StompWebSocketChannelInterceptor.class))
        .webSocketAnonymousApiProvider();

    assertThat(anonymousApiProvider.getAnonymousApis())
        .containsExactly("* /websocket/stomp/**");
  }

  private static OuroborosWebSocketAutoConfiguration newAutoConfiguration(StompWebSocketChannelInterceptor interceptor) {
    return new OuroborosWebSocketAutoConfiguration(interceptor);
  }

  private static void assertSharedPropertyAcrossAllModes(Consumer<WebSocketBrokerProperties> assertion) {
    for (WebSocketBrokerType brokerType : WebSocketBrokerType.values()) {
      WebSocketBrokerProperties candidate = new WebSocketBrokerProperties();
      candidate.setBrokerType(brokerType);
      assertion.accept(candidate);
    }
  }

  @SpringBootConfiguration
  @EnableConfigurationProperties(WebSocketBrokerProperties.class)
  static class TestApplication {
  }
}
