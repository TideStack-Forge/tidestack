package com.ouroboros.mq;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.cloud.stream.binding.SubscribableChannelBindingTargetFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.mq.binder.MQBinderReadyChecker;
import com.ouroboros.mq.binding.MQBindingPropertiesConfigurer;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StreamMQBridgeDegradedPathContractTest {

  @Test
  void subscribeAndUnsubscribeFailClosedWhenBinderIsNotReady() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    BindingService bindingService = mock(BindingService.class);
    SubscribableChannelBindingTargetFactory channelFactory = mock(SubscribableChannelBindingTargetFactory.class);
    MessageHandler handler = mock(MessageHandler.class);
    Binder<?, ?, ?> binder = mock(Binder.class);

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> false,
        binderFactory,
        bindingService,
        channelFactory);
    markReady(bridge);

    assertDoesNotThrow(() -> bridge.subscribe("orders", handler));
    assertDoesNotThrow(() -> bridge.unsubscribe("orders", handler));
    verifyNoInteractions(bindingService, channelFactory);
  }

  @Test
  void sendFailsFastWhenNoProducerCreatorCanHandleTheBinding() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    BindingService bindingService = mock(BindingService.class);
    SubscribableChannelBindingTargetFactory channelFactory = mock(SubscribableChannelBindingTargetFactory.class);
    Binder<?, ?, ?> binder = mock(Binder.class);

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);
    when(binderFactory.getBinder("rabbit", MessageChannel.class)).thenReturn((Binder) binder);

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> true,
        binderFactory,
        bindingService,
        channelFactory,
        (MQProducerCreator) bindingName -> Optional.empty());
    markReady(bridge);

    assertThrows(IllegalStateException.class,
        () -> bridge.send("orders", MessageBuilder.withPayload("payload").build()));
    assertThrows(IllegalStateException.class,
        () -> bridge.send("orders", "route-a", MessageBuilder.withPayload("payload").build()));
    verifyNoInteractions(bindingService, channelFactory);
  }

  @Test
  void pendingActionsShouldNotReplayOnDuplicateStartupEvents() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    BindingService bindingService = mock(BindingService.class);
    SubscribableChannelBindingTargetFactory channelFactory = mock(SubscribableChannelBindingTargetFactory.class);
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
        bindingService,
        channelFactory,
        (MQProducerCreator) bindingName -> Optional.of(producer));

    bridge.send("orders", MessageBuilder.withPayload("payload").build());

    startBridge(bridge);
    startBridge(bridge);

    verify(producer, times(1)).send(any());
  }

  @Test
  void pendingActionsShouldDrainConcurrentStartupSendersExactlyOnce() throws Exception {
    int senderCount = 32;
    for (int round = 0; round < 20; round++) {
      AtomicInteger sentMessages = new AtomicInteger();
      ConcurrentMap<String, AtomicInteger> payloadCounts = new ConcurrentHashMap<>();
      MQProducer producer = new MQProducer() {
        @Override
        public void send(Message<?> message) {
          sentMessages.incrementAndGet();
          payloadCounts
              .computeIfAbsent(String.valueOf(message.getPayload()), payload -> new AtomicInteger())
              .incrementAndGet();
        }

        @Override
        public void send(String routingKey, Message<?> message) {
          sentMessages.incrementAndGet();
        }
      };
      StreamMQBridge bridge = newBridge((MQProducerCreator) bindingName -> Optional.of(producer));
      ExecutorService executor = Executors.newFixedThreadPool(senderCount + 1);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();

      try {
        futures.add(executor.submit(() -> {
          awaitStart(start);
          startBridge(bridge);
        }));
        for (int senderIndex = 0; senderIndex < senderCount; senderIndex++) {
          int payloadIndex = senderIndex;
          futures.add(executor.submit(() -> {
            awaitStart(start);
            bridge.send("orders", MessageBuilder.withPayload("payload-" + payloadIndex).build());
          }));
        }

        start.countDown();
        for (Future<?> future : futures) {
          future.get(5, TimeUnit.SECONDS);
        }
        assertEquals(senderCount, sentMessages.get());
        assertEquals(senderCount, payloadCounts.size());
        for (int payloadIndex = 0; payloadIndex < senderCount; payloadIndex++) {
          var payloadCount = payloadCounts.get("payload-" + payloadIndex);
          assertEquals(1, payloadCount == null ? 0 : payloadCount.get());
        }
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void readySendsShouldInitializeProducerCacheOnceUnderConcurrency() throws Exception {
    int senderCount = 16;
    AtomicInteger creatorCalls = new AtomicInteger();
    AtomicInteger sentMessages = new AtomicInteger();
    CountDownLatch creatorEntered = new CountDownLatch(1);
    CountDownLatch creatorMayReturn = new CountDownLatch(1);
    MQProducer producer = new MQProducer() {
      @Override
      public void send(Message<?> message) {
        sentMessages.incrementAndGet();
      }

      @Override
      public void send(String routingKey, Message<?> message) {
        sentMessages.incrementAndGet();
      }
    };
    StreamMQBridge bridge = newBridge((MQProducerCreator) bindingName -> {
      creatorCalls.incrementAndGet();
      creatorEntered.countDown();
      awaitStart(creatorMayReturn);
      return Optional.of(producer);
    });
    markReady(bridge);
    ExecutorService executor = Executors.newFixedThreadPool(senderCount);
    CountDownLatch senderReady = new CountDownLatch(senderCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int senderIndex = 0; senderIndex < senderCount; senderIndex++) {
        int payloadIndex = senderIndex;
        futures.add(executor.submit(() -> {
          senderReady.countDown();
          awaitStart(start);
          bridge.send("orders", MessageBuilder.withPayload("payload-" + payloadIndex).build());
        }));
      }

      assertTrue(senderReady.await(5, TimeUnit.SECONDS));
      start.countDown();
      assertTrue(creatorEntered.await(5, TimeUnit.SECONDS));
      creatorMayReturn.countDown();
      for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }

      assertEquals(1, creatorCalls.get());
      assertEquals(senderCount, sentMessages.get());
    } finally {
      creatorMayReturn.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void pendingActionsShouldDrainConcurrentStartupSubscribersExactlyOnce() throws Exception {
    int subscriberCount = 16;
    for (int round = 0; round < 10; round++) {
      BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
      BinderFactory binderFactory = mock(BinderFactory.class);
      BindingService bindingService = mock(BindingService.class);
      SubscribableChannelBindingTargetFactory channelFactory = mock(SubscribableChannelBindingTargetFactory.class);
      Binder<?, ?, ?> binder = mock(Binder.class);
      ConcurrentMap<MessageHandler, AtomicInteger> subscriptions = new ConcurrentHashMap<>();
      SubscribableChannel channel = new SubscribableChannel() {
        @Override
        public boolean subscribe(MessageHandler handler) {
          subscriptions.computeIfAbsent(handler, key -> new AtomicInteger()).incrementAndGet();
          return true;
        }

        @Override
        public boolean unsubscribe(MessageHandler handler) {
          return true;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
          return true;
        }
      };

      when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");
      when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);
      when(channelFactory.createInput("orders")).thenReturn(channel);

      StreamMQBridge bridge = newBridge(
          bindingServiceProperties,
          (MQBindingPropertiesConfigurer) properties -> {
          },
          (MQBinderReadyChecker) (binderName, candidate) -> true,
          binderFactory,
          bindingService,
          channelFactory);
      ExecutorService executor = Executors.newFixedThreadPool(subscriberCount + 1);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();
      List<MessageHandler> handlers = new ArrayList<>();

      try {
        futures.add(executor.submit(() -> {
          awaitStart(start);
          startBridge(bridge);
        }));
        for (int subscriberIndex = 0; subscriberIndex < subscriberCount; subscriberIndex++) {
          MessageHandler handler = mock(MessageHandler.class);
          handlers.add(handler);
          futures.add(executor.submit(() -> {
            awaitStart(start);
            bridge.subscribe("orders", handler);
          }));
        }

        start.countDown();
        for (Future<?> future : futures) {
          future.get(5, TimeUnit.SECONDS);
        }

        verify(channelFactory, times(1)).createInput("orders");
        verify(bindingService, times(1)).bindConsumer(channel, "orders");
        assertEquals(subscriberCount, subscriptions.size());
        for (MessageHandler handler : handlers) {
          var subscriptionCount = subscriptions.get(handler);
          assertEquals(1, subscriptionCount == null ? 0 : subscriptionCount.get());
        }
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void subscribeShouldRecoverWhenBinderBecomesReadyAfterDisabledConsumerWasCached() {
    BindingServiceProperties bindingServiceProperties = mock(BindingServiceProperties.class);
    BinderFactory binderFactory = mock(BinderFactory.class);
    BindingService bindingService = mock(BindingService.class);
    SubscribableChannelBindingTargetFactory channelFactory = mock(SubscribableChannelBindingTargetFactory.class);
    MessageHandler handler = mock(MessageHandler.class);
    SubscribableChannel channel = mock(SubscribableChannel.class);
    Binder<?, ?, ?> binder = mock(Binder.class);
    boolean[] binderReady = {false};

    when(bindingServiceProperties.getBinder("orders")).thenReturn("rabbit");
    when(binderFactory.getBinder("rabbit", SubscribableChannel.class)).thenReturn((Binder) binder);
    when(channelFactory.createInput("orders")).thenReturn(channel);

    StreamMQBridge bridge = newBridge(
        bindingServiceProperties,
        (MQBindingPropertiesConfigurer) properties -> {
        },
        (MQBinderReadyChecker) (binderName, candidate) -> binderReady[0],
        binderFactory,
        bindingService,
        channelFactory);
    markReady(bridge);

    assertDoesNotThrow(() -> bridge.subscribe("orders", handler));
    verifyNoInteractions(bindingService, channelFactory, channel);

    binderReady[0] = true;

    assertDoesNotThrow(() -> bridge.subscribe("orders", handler));

    verify(channelFactory).createInput("orders");
    verify(bindingService).bindConsumer(channel, "orders");
    verify(channel).subscribe(handler);
  }

  @Test
  void onlyOwningContextClosureUnbindsDynamicConsumersAndRejectsFurtherWork() {
    var properties = mock(BindingServiceProperties.class);
    var binders = mock(BinderFactory.class);
    var bindings = mock(BindingService.class);
    var channels = mock(SubscribableChannelBindingTargetFactory.class);
    var channel = mock(SubscribableChannel.class);
    var binder = mock(Binder.class);
    when(binders.getBinder(null, SubscribableChannel.class)).thenReturn(binder);
    when(channels.createInput("orders")).thenReturn(channel);
    var bridge = newBridge(properties, binders, bindings, channels,
        (MQBinderReadyChecker) (name, candidate) -> true);
    var owner = mock(org.springframework.context.ApplicationContext.class);
    var child = mock(org.springframework.context.ApplicationContext.class);
    bridge.setApplicationContext(owner);
    markReady(bridge);
    var handler = mock(MessageHandler.class);
    bridge.subscribe("orders", handler);
    bridge.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
    bridge.subscribe("orders", handler);
    verify(channel, times(2)).subscribe(handler);
    bridge.onContextClosed(new org.springframework.context.event.ContextClosedEvent(owner));
    bridge.close();
    verify(bindings).unbindConsumers("orders");
    assertThrows(IllegalStateException.class, () -> bridge.subscribe("orders", handler));
    assertThrows(IllegalStateException.class, () -> bridge.send("orders", MessageBuilder.withPayload("late").build()));
    assertDoesNotThrow(() -> bridge.unsubscribe("orders", handler));
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
        factory.getBeanProvider(BindingService.class),
        factory.getBeanProvider(SubscribableChannelBindingTargetFactory.class),
        factory.getBeanProvider(MQProducerCreator.class));
  }

  private void markReady(StreamMQBridge bridge) {
    ReflectionTestUtils.setField(bridge, "isReady", true);
  }

  private void startBridge(StreamMQBridge bridge) {
    ReflectionTestUtils.invokeMethod(
        bridge,
        "onAppInitialized",
        new OuroborosContextRefreshedEvent(mock(ApplicationStartedEvent.class)));
  }

  private void awaitStart(CountDownLatch start) {
    try {
      assertTrue(start.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
