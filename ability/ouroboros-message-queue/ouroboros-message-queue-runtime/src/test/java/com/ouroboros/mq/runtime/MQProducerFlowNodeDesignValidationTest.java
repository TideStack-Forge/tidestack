package com.ouroboros.mq.runtime;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MQProducerFlowNodeDesignValidationTest {

  @Test
  @SuppressWarnings("unchecked")
  void constructorShouldAddDesignErrorWhenBindingNameIsBlank() {
    FlowModel flowModel = mock(FlowModel.class);
    FlowNodeMeta meta = mock(FlowNodeMeta.class);

    when(meta.getExtraProp(String.class, "bindingName")).thenReturn((Optional) Optional.of("   "));

    MQProducerFlowNode.MQProducerFlowNodeImpl node = new MQProducerFlowNode.MQProducerFlowNodeImpl(flowModel, meta);

    assertTrue(node.getErrors().stream().anyMatch(error -> error.getMessage().contains("bindingName")));
  }
}
