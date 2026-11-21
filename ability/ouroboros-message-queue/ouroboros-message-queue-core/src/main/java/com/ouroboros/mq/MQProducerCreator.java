package com.ouroboros.mq;

import java.util.Optional;

public interface MQProducerCreator {
  Optional<MQProducer> create(String bindingName);
}
