package com.ouroboros.mq.runtime;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.util.Json;

public class LogicFlowMQConsumer implements MessageHandler {
  private static final Logger logger = LoggerFactory.getLogger(LogicFlowMQConsumer.class);
  private final String bindingName;
  private final String flowName;
  private final LogicFlowExecutionFacade executionFacade;

  public LogicFlowMQConsumer(String bindingName, String flowName, LogicFlowExecutionFacade executionFacade) {
    this.bindingName = bindingName;
    this.flowName = flowName;
    this.executionFacade = executionFacade;
  }

  @Override
  public void handleMessage(Message<?> message) throws MessagingException {
    var payload = message.getPayload();
    Map<String, Object> messagePayload = null;
    if (payload instanceof byte[]) {
      payload = new String((byte[]) payload, StandardCharsets.UTF_8);
    }
    if (payload instanceof String) {
      messagePayload = Json.tryToMap((String) payload).getOrNull();
    }
    if (payload instanceof Map) {
      messagePayload = (Map<String, Object>) payload;
    }
    if (messagePayload == null) {
      var tryPayload = Json.tryToJsonString(payload).flatMap(Json::tryToMap);
      if (tryPayload.isFailure()) {
        logger.error("消息队列消费者 {} 无法处理消息，消息体 {} 无法转换为Map", bindingName, payload);
        return;
      }
      messagePayload = tryPayload.get();
    }
    var flowContext = new TreeMap<>(String::compareToIgnoreCase);
    flowContext.put("payload", messagePayload);
    flowContext.put("headers", message.getHeaders());
    var result = executionFacade.execute(flowName, List.of(flowContext));
    if (result.isFailure()) {
      logger.error("消息队列消费者 {} 执行逻辑流 {} 出现异常", bindingName, flowName, result.getCause());
    }
  }
}
