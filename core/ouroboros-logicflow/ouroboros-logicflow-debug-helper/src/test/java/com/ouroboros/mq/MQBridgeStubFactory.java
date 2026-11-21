package com.ouroboros.mq;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ouroboros.mq.builder.MQProducerBuilder;

public final class MQBridgeStubFactory {
  private MQBridgeStubFactory() {
  }

  public static MQBridge bridgeWithProducer(MQProducer producer) {
    MQBridge bridge = mock(MQBridge.class);
    when(bridge.bindingProducer(anyString())).thenAnswer(call ->
        new MQProducerBuilder(call.getArgument(0), properties -> producer));
    return bridge;
  }
}
