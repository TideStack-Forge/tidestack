package com.ouroboros.mq.runtime;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import io.vavr.control.Try;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LogicFlowMQConsumerPayloadForwardingTest {

  @Test
  public void handleMessageForwardsByteArrayJsonPayloadIntoFlowContext() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(anyString(), any())).thenReturn(Try.success("ok"));
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    consumer.handleMessage(MessageBuilder.withPayload("{\"name\":\"alice\"}".getBytes(StandardCharsets.UTF_8)).build());

    Map<?, ?> flowContext = captureSingleFlowContext(executionFacade);
    Map<?, ?> parsedPayload = assertInstanceOf(Map.class, flowContext.get("payload"));
    assertEquals("alice", parsedPayload.get("name"));
  }

  @Test
  public void handleMessageForwardsStringJsonPayloadAndHeadersIntoCaseInsensitiveFlowContext() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(anyString(), any())).thenReturn(Try.success("ok"));
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    consumer.handleMessage(
      MessageBuilder.withPayload("{\"name\":\"alice\"}")
        .setHeader("X-Trace-Id", "trace-123")
        .build()
    );

    Map<?, ?> flowContext = captureSingleFlowContext(executionFacade);
    Map<?, ?> parsedPayload = assertInstanceOf(Map.class, flowContext.get("PAYLOAD"));
    assertEquals("alice", parsedPayload.get("name"));
    assertSame(parsedPayload, flowContext.get("payload"));
    MessageHeaders headers = assertInstanceOf(MessageHeaders.class, flowContext.get("HEADERS"));
    assertEquals("trace-123", headers.get("X-Trace-Id"));
    assertSame(headers, flowContext.get("headers"));
  }

  @Test
  public void handleMessageForwardsMapPayloadWithoutReencoding() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(anyString(), any())).thenReturn(Try.success("ok"));
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    Map<String, Object> payload = new HashMap<>();
    payload.put("name", "alice");

    consumer.handleMessage(MessageBuilder.withPayload(payload).build());

    Map<?, ?> flowContext = captureSingleFlowContext(executionFacade);
    assertSame(payload, flowContext.get("payload"));
    Map<?, ?> parsedPayload = assertInstanceOf(Map.class, flowContext.get("payload"));
    assertEquals("alice", parsedPayload.get("name"));
  }

  @Test
  public void handleMessageForwardsFallbackParsedPayloadIntoFlowContext() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(anyString(), any())).thenReturn(Try.success("ok"));
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    PayloadBean payload = new PayloadBean();
    payload.name = "alice";

    consumer.handleMessage(MessageBuilder.withPayload(payload).build());

    Map<?, ?> flowContext = captureSingleFlowContext(executionFacade);
    Map<?, ?> parsedPayload = assertInstanceOf(Map.class, flowContext.get("payload"));
    assertEquals("alice", parsedPayload.get("name"));
  }

  @Test
  public void handleMessageSkipsFlowExecutionWhenStringPayloadIsInvalidJson() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    consumer.handleMessage(MessageBuilder.withPayload("{invalid-json").build());

    verifyNoInteractions(executionFacade);
  }

  @Test
  public void handleMessageSkipsFlowExecutionWhenByteArrayPayloadIsInvalidJson() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    LogicFlowMQConsumer consumer = new LogicFlowMQConsumer("orders", "missing-flow", executionFacade);

    consumer.handleMessage(MessageBuilder.withPayload("{invalid-json".getBytes(StandardCharsets.UTF_8)).build());

    verifyNoInteractions(executionFacade);
  }

  private Map<?, ?> captureSingleFlowContext(LogicFlowExecutionFacade executionFacade) {
    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(eq("missing-flow"), inputsCaptor.capture());
    List inputs = inputsCaptor.getValue();
    assertEquals(1, inputs.size());
    return assertInstanceOf(Map.class, inputs.get(0));
  }

  public static class PayloadBean {
    public String name;
  }
}
