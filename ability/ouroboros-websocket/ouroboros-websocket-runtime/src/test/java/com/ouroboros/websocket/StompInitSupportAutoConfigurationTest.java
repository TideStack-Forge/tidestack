package com.ouroboros.websocket;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StompInitSupportAutoConfigurationTest {

  @Test
  void shouldRegisterInitSupportBeanMethodsOnAutoConfiguration() throws Exception {
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
  void shouldKeepDefaultInitRequestContextAsPlainValueType() {
    assertThat(DefaultInitRequestContext.class.getAnnotations()).isEmpty();
    assertThat(DefaultInitRequestContext.class.getDeclaredConstructors()).isNotEmpty();
  }

  @Test
  void shouldAllowOverrideOfInitSupportBeans() {
    assertThat(OuroborosWebSocketAutoConfiguration.class.getDeclaredMethods())
        .extracting(Method::getName)
        .contains("initReplySender", "initRequestValidator", "stompCompensationRegistrar");
  }
}
