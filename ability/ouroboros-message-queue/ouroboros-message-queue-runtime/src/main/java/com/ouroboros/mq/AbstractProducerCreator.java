package com.ouroboros.mq;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

public abstract class AbstractProducerCreator implements MQProducerCreator {
  @Autowired
  private StreamBridge streamBridge;
  @Autowired
  private ObjectProvider<BindingServiceProperties> bindingServiceProperties;
  @Autowired
  private ObjectProvider<BinderFactory> binderFactory;

  @Override
  public Optional<MQProducer> create(String bindingName) {
    var binderName = Optional.ofNullable(bindingServiceProperties.getIfAvailable())
        .map(p -> p.getBinder(bindingName))
        .orElse(null);
    var binder = Optional.ofNullable(binderFactory.getIfAvailable())
        .map(binderFactory -> binderFactory.getBinder(binderName, MessageChannel.class));

    return binder
        .filter(b -> support(b, bindingName))
        .map(b -> new MQProducerImpl(b, bindingName));
  }

  protected boolean support(Binder<?, ?, ?> binder, String bindingName) {
    return false;
  }

  protected Consumer<Message<?>> getSender(Binder<?, ?, ?> binder, String bindingName) {
    return message -> {
      if (!streamBridge.send(bindingName, message)) {
        throw new IllegalStateException("消息队列发送失败：" + bindingName);
      }
    };
  }

  protected abstract BiConsumer<String, Message<?>> getGenderWithRoutingKey(Binder<?, ?, ?> binder, String bindingName);

  public class MQProducerImpl implements MQProducer {
    private final Consumer<Message<?>> sender;
    private final BiConsumer<String, Message<?>> genderWithRoutingKey;

    public MQProducerImpl(Binder<?, ?, ?> binder, String bindingName) {
      this.sender = getSender(binder, bindingName);
      this.genderWithRoutingKey = getGenderWithRoutingKey(binder, bindingName);
    }

    @Override
    public void send(Message<?> message) {
      this.sender.accept(message);
    }

    @Override
    public void send(String routingKey, Message<?> message) {
      this.genderWithRoutingKey.accept(routingKey, message);
    }
  }
}
