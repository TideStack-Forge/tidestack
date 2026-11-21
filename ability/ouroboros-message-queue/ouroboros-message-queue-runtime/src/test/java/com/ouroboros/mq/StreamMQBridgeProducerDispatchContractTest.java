package com.ouroboros.mq;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.mq.binder.MQBinderReadyChecker;
import com.ouroboros.mq.binding.MQBindingPropertiesConfigurer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamMQBridgeProducerDispatchContractTest {

  @Test
  void sendShouldNotRequireBindingServiceOrChannelFactoryWhenProducerCreatorCanHandleBinding() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    Binder<?, ?, ?> binder = mock(Binder.class);
    MQProducer producer = mock(MQProducer.class);

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);
    when(binderFactory.getBinder("rabbit", MessageChannel.class)).thenReturn((Binder) binder);

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> true,
        binderFactory,
        (MQProducerCreator) bindingName -> Optional.of(producer));
    markReady(bridge);

    bridge.send("orders", MessageBuilder.withPayload("payload").build());

    verify(producer).send(any());
  }

  @Test
  void sendShouldNotRequireSubscribableChannelBinderLookupForProducerDispatch() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    MQProducer producer = mock(MQProducer.class);

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> true,
        binderFactory,
        (MQProducerCreator) bindingName -> Optional.of(producer));
    markReady(bridge);

    bridge.send("orders", MessageBuilder.withPayload("payload").build());

    verify(producer).send(any());
    verifyNoInteractions(binderFactory);
  }

  @Test
  void sendShouldForwardRoutingKeyToActiveProducerDispatch() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    MQProducer producer = mock(MQProducer.class);

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> true,
        binderFactory,
        (MQProducerCreator) bindingName -> Optional.of(producer));
    markReady(bridge);

    bridge.send("orders", "route-a", MessageBuilder.withPayload("payload").build());

    verify(producer).send(eq("route-a"), any());
    verifyNoInteractions(binderFactory);
  }

  @Test
  void sendShouldQueryProducerCreatorsInOrderUntilOneCreatesProducer() {
    MQProducerCreator firstCreator = mock(MQProducerCreator.class);
    MQProducerCreator secondCreator = mock(MQProducerCreator.class);
    MQProducerCreator thirdCreator = mock(MQProducerCreator.class);
    MQProducer producer = mock(MQProducer.class);

    when(firstCreator.create("orders")).thenReturn(Optional.empty());
    when(secondCreator.create("orders")).thenReturn(Optional.of(producer));

    StreamMQBridge bridge = newBridge(firstCreator, secondCreator, thirdCreator);
    markReady(bridge);

    bridge.send("orders", MessageBuilder.withPayload("payload").build());

    verify(firstCreator).create("orders");
    verify(secondCreator).create("orders");
    verify(thirdCreator, never()).create(anyString());
    verify(producer).send(any());
  }

  @Test
  void sendShouldReuseCachedProducerWithoutRequeryingCreators() {
    MQProducerCreator creator = mock(MQProducerCreator.class);
    MQProducer producer = mock(MQProducer.class);

    when(creator.create("orders")).thenReturn(Optional.of(producer));

    StreamMQBridge bridge = newBridge(creator);
    markReady(bridge);

    bridge.send("orders", MessageBuilder.withPayload("first").build());
    bridge.send("orders", MessageBuilder.withPayload("second").build());

    verify(creator).create("orders");
    verify(producer, times(2)).send(any());
  }

  @Test
  void sendShouldResolveProducerAgainAfterItBecomesAvailable() {
    AtomicInteger attempts = new AtomicInteger();
    MQProducer producer = mock(MQProducer.class);
    MQProducerCreator creator = bindingName -> attempts.getAndIncrement() == 0
        ? Optional.empty()
        : Optional.of(producer);

    StreamMQBridge bridge = newBridge(creator);
    markReady(bridge);

    assertThrows(IllegalStateException.class,
        () -> bridge.send("orders", MessageBuilder.withPayload("first").build()));
    bridge.send("orders", MessageBuilder.withPayload("second").build());

    verify(producer).send(any());
  }

  @Test
  void sendShouldDiscardFailedProducerBeforeTheNextAttempt() {
    MQProducerCreator creator = mock(MQProducerCreator.class);
    MQProducer failed = mock(MQProducer.class);
    MQProducer recovered = mock(MQProducer.class);

    when(creator.create("orders")).thenReturn(Optional.of(failed), Optional.of(recovered));
    org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable")).when(failed).send(any());

    StreamMQBridge bridge = newBridge(creator);
    markReady(bridge);

    assertThrows(IllegalStateException.class,
        () -> bridge.send("orders", MessageBuilder.withPayload("first").build()));
    bridge.send("orders", MessageBuilder.withPayload("second").build());

    verify(creator, times(2)).create("orders");
    verify(recovered).send(any());
  }

  private StreamMQBridge newBridge(Object... beans) {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    for (int i = 0; i < beans.length; i++) {
      factory.registerSingleton("bean" + i, beans[i]);
    }
    return new StreamMQBridge(
        factory.getBeanProvider(BindingServiceProperties.class),
        factory.getBeanProvider(MQBindingPropertiesConfigurer.class),
        factory.getBeanProvider(MQBinderReadyChecker.class),
        factory.getBeanProvider(BinderFactory.class),
        factory.getBeanProvider(org.springframework.cloud.stream.binding.BindingService.class),
        factory.getBeanProvider(org.springframework.cloud.stream.binding.SubscribableChannelBindingTargetFactory.class),
        factory.getBeanProvider(MQProducerCreator.class));
  }

  private void markReady(StreamMQBridge bridge) {
    ReflectionTestUtils.setField(bridge, "isReady", true);
  }
}
