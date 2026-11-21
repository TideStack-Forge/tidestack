package com.ouroboros.websocket;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketAutoConfigurationTest {

  @Test
  void shouldExposeInitSupportBeanFactoryMethods() throws Exception {
    Method initReplySender = OuroborosWebSocketAutoConfiguration.class.getMethod("initReplySender", StompMessageSender.class);
    Method initRequestValidator = OuroborosWebSocketAutoConfiguration.class.getMethod("initRequestValidator");
    Method stompInitSupport = OuroborosWebSocketAutoConfiguration.class.getMethod(
        "stompCompensationRegistrar",
        StompMessageSender.class,
        StompMessageSubscriber.class,
        DefaultInitRequestValidator.class);

    assertThat(initReplySender.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).isTrue();
    assertThat(initReplySender.isAnnotationPresent(org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class)).isTrue();
    assertThat(initRequestValidator.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).isTrue();
    assertThat(initRequestValidator.isAnnotationPresent(org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class)).isTrue();
    assertThat(stompInitSupport.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).isTrue();
    assertThat(stompInitSupport.isAnnotationPresent(org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class)).isTrue();

    OuroborosWebSocketAutoConfiguration configuration = new OuroborosWebSocketAutoConfiguration(mock(StompWebSocketChannelInterceptor.class));

    assertThat(configuration.initReplySender(mock(StompMessageSender.class))).isInstanceOf(DefaultInitReplySender.class);
    assertThat(configuration.initRequestValidator()).isInstanceOf(DefaultInitRequestValidator.class);
    assertThat(configuration.stompCompensationRegistrar(
        mock(StompMessageSender.class),
        mock(StompMessageSubscriber.class),
        configuration.initRequestValidator())).isInstanceOf(DefaultStompCompensationRegistrar.class);
  }

  @Test
  void shouldPreserveBrokerPrefixesAndInboundInterceptorRegistration() {
    StompWebSocketChannelInterceptor interceptor = mock(StompWebSocketChannelInterceptor.class);
    OuroborosWebSocketAutoConfiguration configuration = new OuroborosWebSocketAutoConfiguration(interceptor);

    MessageBrokerRegistry messageBrokerRegistry = mock(MessageBrokerRegistry.class);
    ChannelRegistration channelRegistration = mock(ChannelRegistration.class);

    configuration.configureMessageBroker(messageBrokerRegistry);
    configuration.configureClientInboundChannel(channelRegistration);

    verify(messageBrokerRegistry).setApplicationDestinationPrefixes("/app");
    verify(messageBrokerRegistry).setUserDestinationPrefix("/user");
    verify(messageBrokerRegistry).enableSimpleBroker("/topic", "/queue");
    verify(channelRegistration).interceptors(interceptor);
  }
}
