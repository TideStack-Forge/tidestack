package com.ouroboros.mq.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.mq.MQBridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MQProducerFlowNodeExecutionTest {

  @Test
  @SuppressWarnings("unchecked")
  void doExecuteShouldTreatNullHeadersInputAsEmptyMap() {
    MQBridge bridge = mock(MQBridge.class);
    new MQProducerFlowNode(bridge);

    FlowModel flowModel = mock(FlowModel.class);
    FlowNodeMeta meta = mock(FlowNodeMeta.class);
    when(meta.getExtraProp(String.class, "bindingName")).thenReturn((Optional) Optional.of("orders"));

    MQProducerFlowNode.MQProducerFlowNodeImpl node = new MQProducerFlowNode.MQProducerFlowNodeImpl(flowModel, meta);
    List<Object> inputs = Arrays.asList("payload", null);

    Object result = assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(node, "doExecute", inputs));

    assertEquals(Collections.singletonList("payload"), result);
    verify(bridge).send(eq("orders"), any(Message.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void doExecuteShouldRejectNullPayload() {
    MQBridge bridge = mock(MQBridge.class);
    new MQProducerFlowNode(bridge);

    FlowModel flowModel = mock(FlowModel.class);
    FlowNodeMeta meta = mock(FlowNodeMeta.class);
    when(meta.getExtraProp(String.class, "bindingName")).thenReturn((Optional) Optional.of("orders"));

    MQProducerFlowNode.MQProducerFlowNodeImpl node = new MQProducerFlowNode.MQProducerFlowNodeImpl(flowModel, meta);
    List<Object> inputs = Arrays.asList(null, Collections.emptyMap());

    FlowRuntimeException exception = assertThrows(FlowRuntimeException.class,
        () -> ReflectionTestUtils.invokeMethod(node, "doExecute", inputs));

    assertTrue(exception.getMessage().contains("第一个输入不得为 null"));
    verifyNoInteractions(bridge);
  }

  @Test
  @SuppressWarnings("unchecked")
  void doExecuteShouldRejectNonMapHeaders() {
    MQBridge bridge = mock(MQBridge.class);
    new MQProducerFlowNode(bridge);

    FlowModel flowModel = mock(FlowModel.class);
    FlowNodeMeta meta = mock(FlowNodeMeta.class);
    when(meta.getExtraProp(String.class, "bindingName")).thenReturn((Optional) Optional.of("orders"));

    MQProducerFlowNode.MQProducerFlowNodeImpl node = new MQProducerFlowNode.MQProducerFlowNodeImpl(flowModel, meta);
    List<Object> inputs = Arrays.asList("payload", "not-a-map");

    FlowRuntimeException exception = assertThrows(FlowRuntimeException.class,
        () -> ReflectionTestUtils.invokeMethod(node, "doExecute", inputs));

    assertTrue(exception.getMessage().contains("第二个输入必须为 Map 类型"));
    verifyNoInteractions(bridge);
  }

  @Test
  @SuppressWarnings("unchecked")
  void doExecuteShouldRejectMissingPayloadInputList() {
    MQBridge bridge = mock(MQBridge.class);
    new MQProducerFlowNode(bridge);

    FlowModel flowModel = mock(FlowModel.class);
    FlowNodeMeta meta = mock(FlowNodeMeta.class);
    when(meta.getExtraProp(String.class, "bindingName")).thenReturn((Optional) Optional.of("orders"));

    MQProducerFlowNode.MQProducerFlowNodeImpl node = new MQProducerFlowNode.MQProducerFlowNodeImpl(flowModel, meta);
    List<Object> inputs = Collections.emptyList();

    FlowRuntimeException exception = assertThrows(FlowRuntimeException.class,
        () -> ReflectionTestUtils.invokeMethod(node, "doExecute", inputs));

    assertTrue(exception.getMessage().contains("第一个输入不得缺失"));
    verifyNoInteractions(bridge);
  }
}
