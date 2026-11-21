package com.ouroboros.mq;

import java.util.*;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.cloud.stream.binding.SubscribableChannelBindingTargetFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Component;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.mq.binder.MQBinderReadyChecker;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQBindingPropertiesConfigurer;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.mq.binding.MQProducerProperties;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;

@Component
public class StreamMQBridge implements MQBridge, ApplicationContextAware {
  private final static Logger logger = LoggerFactory.getLogger(StreamMQBridge.class);
  private final Map<String, SubscribableChannel> incomingChannels;
  private final Map<String, MQProducer> producers;
  private final Object lifecycleMonitor = new Object();
  private final Object incomingChannelsMonitor = new Object();
  private final Object producersMonitor = new Object();
  private final ObjectProvider<BindingServiceProperties> bindingServiceProperties;
  private final ObjectProvider<MQBindingPropertiesConfigurer> bindingPropertiesConfigurers;
  private final ObjectProvider<MQBinderReadyChecker> binderStandbyCheckers;
  private final ObjectProvider<BinderFactory> binderFactory;
  private final ObjectProvider<BindingService> bindingService;
  private final ObjectProvider<SubscribableChannelBindingTargetFactory> channelFactory;
  private final ObjectProvider<MQProducerCreator> producerCreators;
  private final List<PendingAction> pendingActions = new ArrayList<>();
  private volatile boolean isReady = false;
  private volatile boolean closed;
  private ApplicationContext applicationContext;

  public StreamMQBridge(ObjectProvider<BindingServiceProperties> bindingServiceProperties,
           ObjectProvider<MQBindingPropertiesConfigurer> mqBindingPropertiesConfigurers,
           ObjectProvider<MQBinderReadyChecker> binderStandbyCheckers,
           ObjectProvider<BinderFactory> binderFactory,
           ObjectProvider<BindingService> bindingService,
           ObjectProvider<SubscribableChannelBindingTargetFactory> channelFactory,
           ObjectProvider<MQProducerCreator> producerCreators) {
    this.incomingChannels = new TreeMap<>(String::compareToIgnoreCase);
    this.producers = new TreeMap<>(String::compareToIgnoreCase);
    this.bindingServiceProperties = bindingServiceProperties;
    this.bindingPropertiesConfigurers = mqBindingPropertiesConfigurers;
    this.binderStandbyCheckers = binderStandbyCheckers;
    this.binderFactory = binderFactory;
    this.bindingService = bindingService;
    this.channelFactory = channelFactory;
    this.producerCreators = producerCreators;
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  private void onAppInitialized(OuroborosContextRefreshedEvent event) {
    if (!(event.getSource() instanceof ApplicationStartedEvent)) {
      return;
    }
    List<PendingAction> actions;
    synchronized (lifecycleMonitor) {
      if (isReady || closed) {
        return;
      }
      isReady = true;
      actions = List.copyOf(pendingActions);
      pendingActions.clear();
    }
    actions.forEach(action -> action.execute(this));
  }

  public void prepareBindingProperties(MQBindingProperties<?> bindingProperties) {
    bindingPropertiesConfigurers
        .forEach(injector -> injector.configBinding(bindingProperties));
  }

  public void subscribe(String bindingName, MessageHandler handler) {
    executeOrDefer(new SubscribeAction(bindingName, handler));
  }

  public void unsubscribe(String bindingName, MessageHandler handler) {
    executeOrDefer(new UnsubscribeAction(bindingName, handler));
  }

  public void send(String bindingName, Message<?> message) {
    executeOrDefer(new SendAction(bindingName, message));
  }

  public void send(String bindingName, String routingKey, Message<?> message) {
    executeOrDefer(new SendRoutingAction(bindingName, routingKey, message));
  }

  public MQConsumerBuilder bindingConsumer(String bindingName) {
    return new MQConsumerBuilder(bindingName, this::bindConsumer);
  }

  public MQProducerBuilder bindingProducer(String bindingName) {
    return new MQProducerBuilder(bindingName, this::bindProducer);
  }

  protected MQConsumer bindConsumer(MQConsumerProperties consumerProperties) {
    prepareBindingProperties(consumerProperties);
    var bindingName = consumerProperties.getBindingName();
    return new MQConsumer() {
      @Override
      public void subscribe(MessageHandler messageHandler) {
        StreamMQBridge.this.subscribe(bindingName, messageHandler);
      }

      @Override
      public void unsubscribe(MessageHandler messageHandler) {
        StreamMQBridge.this.unsubscribe(bindingName, messageHandler);
      }
    };
  }

  protected MQProducer bindProducer(MQProducerProperties producerProperties) {
    prepareBindingProperties(producerProperties);
    var bindingName = producerProperties.getBindingName();
    return new MQProducer() {
      @Override
      public void send(Message<?> message) {
        StreamMQBridge.this.send(bindingName, message);
      }

      @Override
      public void send(String routingKey, Message<?> message) {
        StreamMQBridge.this.send(bindingName, routingKey, message);
      }
    };
  }

  protected void doSubscribe(String bindingName, MessageHandler handler) {
    var channel = getSubscribableChannel(bindingName);
    if (channel == null) {
      return;
    }
    channel.subscribe(handler);
  }

  protected void doUnsubscribe(String bindingName, MessageHandler handler) {
    var channel = getSubscribableChannel(bindingName);
    if (channel == null) {
      return;
    }
    channel.unsubscribe(handler);
  }

  protected void doSend(String bindingName, Message<?> message) {
    var producer = getProducer(bindingName);
    try {
      producer.send(message);
    } catch (RuntimeException exception) {
      discardProducer(bindingName, producer);
      throw exception;
    }
  }

  protected void doSend(String bindingName, String routingKey, Message<?> message) {
    var producer = getProducer(bindingName);
    try {
      producer.send(routingKey, message);
    } catch (RuntimeException exception) {
      discardProducer(bindingName, producer);
      throw exception;
    }
  }

  private void executeOrDefer(PendingAction action) {
    if (closed) {
      if (action instanceof UnsubscribeAction) return;
      throw new IllegalStateException("MQ bridge has stopped");
    }
    if (isReady) {
      action.execute(this);
      return;
    }
    boolean executeNow;
    synchronized (lifecycleMonitor) {
      if (closed) throw new IllegalStateException("MQ bridge has stopped");
      executeNow = isReady;
      if (!executeNow) {
        pendingActions.add(action);
      }
    }
    if (executeNow) {
      action.execute(this);
    }
  }

  private SubscribableChannel getSubscribableChannel(String bindingName) {
    synchronized (incomingChannelsMonitor) {
      if (closed) throw new IllegalStateException("MQ bridge has stopped");
      return incomingChannels.compute(bindingName, (k, v) -> {
        if (v != null && !(v instanceof DisabledConsumer)) {
          return v;
        }
        var channelFactory = this.channelFactory.getIfAvailable();
        var bindingService = this.bindingService.getIfAvailable();
        var bindingReady = isBindingReady(bindingName);
        if (channelFactory == null || bindingService == null || !bindingReady) {
          if (v instanceof DisabledConsumer) {
            return v;
          }
          return new DisabledConsumer("消息队列配置不全，" + bindingName + "订阅通道不可用");
        }

        var channel = channelFactory.createInput(bindingName);
        bindingService.bindConsumer(channel, bindingName);
        return channel;
      });
    }
  }

  private boolean isBindingReady(String bindingName) {
    var binderName = Optional.ofNullable(bindingServiceProperties.getIfAvailable())
        .map(p -> p.getBinder(bindingName))
        .orElse(null);

    var binder = Optional.ofNullable(binderFactory.getIfAvailable())
        .map(binderFactory -> binderFactory.getBinder(binderName, SubscribableChannel.class));

    var isBinderAvailable = binder
        .map(getIsBinderReady(binderName));

    return isBinderAvailable.orElse(false);
  }

  private Function<Binder<?, ?, ?>, Boolean> getIsBinderReady(String binderName) {
    return binder -> binderStandbyCheckers
        .stream()
        .anyMatch(checker -> checker.isReady(binderName, binder));
  }

  private MQProducer getProducer(String bindingName) {
    synchronized (producersMonitor) {
      MQProducer current = producers.get(bindingName);
      if (current != null) {
        return current;
      }
      MQProducer created = producerCreators.stream()
          .map(creator -> creator.create(bindingName))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("消息队列发送者对象创建失败：" + bindingName));
      producers.put(bindingName, created);
      return created;
    }
  }

  private void discardProducer(String bindingName, MQProducer producer) {
    synchronized (producersMonitor) {
      producers.remove(bindingName, producer);
    }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @EventListener(ContextClosedEvent.class)
  public void onContextClosed(ContextClosedEvent event) {
    if (event.getApplicationContext() == applicationContext) {
      close();
    }
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    synchronized (lifecycleMonitor) {
      if (closed) return;
      closed = true;
      isReady = false;
      pendingActions.clear();
    }
    // These inputs are created dynamically, outside the framework's Bindable registry.
    // Stop them while their connection factories and binder contexts are still available.
    synchronized (incomingChannelsMonitor) {
      var bindings = bindingService.getIfAvailable();
      if (bindings != null) {
        incomingChannels.forEach((name, channel) -> {
          if (!(channel instanceof DisabledConsumer)) {
            try {
              bindings.unbindConsumers(name);
            } catch (RuntimeException failure) {
              logger.warn("Could not close dynamic MQ consumer {}", name, failure);
            }
          }
        });
      }
      incomingChannels.clear();
    }
    synchronized (producersMonitor) {
      producers.clear();
    }
  }

  static class DisabledConsumer implements SubscribableChannel {
    private final String errorMsg;

    public DisabledConsumer(String errorMsg) {
      this.errorMsg = errorMsg;
    }

    @Override
    public boolean subscribe(MessageHandler handler) {
      logger.error(errorMsg);
      return false;
    }

    @Override
    public boolean unsubscribe(MessageHandler handler) {
      logger.error(errorMsg);
      return false;
    }

    @Override
    public boolean send(Message<?> message, long timeout) {
      logger.error(errorMsg);
      return false;
    }
  }

  private interface PendingAction {
    void execute(StreamMQBridge bridge);
  }

  private record SubscribeAction(String bindingName, MessageHandler handler) implements PendingAction {
    @Override
    public void execute(StreamMQBridge bridge) {
      bridge.doSubscribe(bindingName, handler);
    }
  }

  private record UnsubscribeAction(String bindingName, MessageHandler handler) implements PendingAction {
    @Override
    public void execute(StreamMQBridge bridge) {
      bridge.doUnsubscribe(bindingName, handler);
    }
  }

  private record SendAction(String bindingName, Message<?> message) implements PendingAction {
    @Override
    public void execute(StreamMQBridge bridge) {
      bridge.doSend(bindingName, message);
    }
  }

  private record SendRoutingAction(
      String bindingName,
      String routingKey,
      Message<?> message) implements PendingAction {
    @Override
    public void execute(StreamMQBridge bridge) {
      bridge.doSend(bindingName, routingKey, message);
    }
  }

}
