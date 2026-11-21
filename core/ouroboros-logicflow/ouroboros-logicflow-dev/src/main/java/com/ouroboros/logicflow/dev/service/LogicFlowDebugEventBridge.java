package com.ouroboros.logicflow.dev.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.util.Json;
import org.springframework.beans.factory.ObjectProvider;

@Component
public class LogicFlowDebugEventBridge {
  private static final Logger logger = LoggerFactory.getLogger(LogicFlowDebugEventBridge.class);
  private static final int MAX_PACKET_BYTES = 65536;
  private final LogicFlowDebugEventStore store;
  private final LogicFlowDebugCaptureService captures;
  private final ObjectProvider<LogicFlowDebugNotifications> notifications;
  private final AtomicLong nextWarning = new AtomicLong();
  private final MessageHandler listener = this::handleMessage;
  private MQConsumer consumer;

  public LogicFlowDebugEventBridge(LogicFlowDebugEventStore store, LogicFlowDebugCaptureService captures,
                                   ObjectProvider<LogicFlowDebugNotifications> notifications) {
    this.store = store;
    this.captures = captures;
    this.notifications = notifications;
  }

  @Autowired
  void initMq(MQBridge bridge) {
    consumer = bridge.bindingConsumer("logicflow-debug-event-consumer")
        .destination("ouroboros.logicflow.debug")
        .group("logicflow-debug-ingest-v2")
        .extraProp("declareExchange", true)
        .build();
    consumer.subscribe(listener);
  }

  @PreDestroy
  void unsubscribe() {
    if (consumer != null) {
      consumer.unsubscribe(listener);
    }
  }

  void handleMessage(Message<?> message) {
    var event = parse(message);
    if (event == null || !"2.0".equals(event.getSchemaVersion()) || event.getTarget() == null
        || !validId(event.getAppName()) || !validId(event.getCaptureId())
        || !event.getAppName().equals(event.getTarget().appName())) {
      return;
    }
    var capture = store.getCapture(event.getAppName(), event.getCaptureId()).orElse(null);
    if (capture == null || !capture.target().equals(event.getTarget())) {
      return;
    }
    LogicFlowDebugEventStore.Receipt receipt;
    try {
      if ("flow.started".equals(event.getKind())) {
        var graph = readGraph(event);
        store.putGraph(event.getAppName(), event.getCaptureId(), graph);
        event.getPayload().remove("graph");
      }
      receipt = store.append(event);
    } catch (DebugStoreException rejected) {
      if (rejected.isCapacity()) {
        recordCapacityLoss(event);
      }
      warn("Debug event rejected; the helper retains unacknowledged packets", rejected);
      return;
    } catch (IllegalArgumentException rejected) {
      warn("Debug event rejected; the helper retains unacknowledged packets", rejected);
      return;
    }
    if (!receipt.duplicate() && captures.allowed(event.getAppName(), capture.target().profile(), capture.owner())) {
      try {
        var delivery = notifications.getIfAvailable();
        if (delivery != null) {
          delivery.updated(capture.owner(), capture.captureId(), receipt.receivedThrough());
        }
      } catch (RuntimeException failed) {
        warn("Debug notification delivery failed; recording is available through HTTP", failed);
      }
    }
  }

  private void recordCapacityLoss(FlowDebugEvent event) {
    try {
      var latest = store.getCapture(event.getAppName(), event.getCaptureId()).orElseThrow();
      if (latest.state() == LogicFlowDebugEventStore.State.STARTING || latest.state() == LogicFlowDebugEventStore.State.ACTIVE) {
        store.updateCapture(event.getAppName(), event.getCaptureId(), latest.version(),
            LogicFlowDebugEventStore.State.SATURATED, 1000);
      }
      store.declareLoss(event.getAppName(), event.getCaptureId(), event.getTarget(),
          new com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus.Loss(event.getRunId(),
              event.getCaptureSeq(), event.getSeq(), "shared-recording-capacity"));
    } catch (RuntimeException failed) {
      warn("Could not publish debug capacity loss; bounded helper lease still applies", failed);
    }
  }

  private FlowDebugGraph readGraph(FlowDebugEvent event) {
    var raw = event.getPayload().get("graph");
    if (raw == null) {
      throw new DebugStoreException("Flow start is missing its runtime graph");
    }
    var graph = Json.tryToJsonString(raw).flatMap(json -> Json.tryToBean(FlowDebugGraph.class, json))
        .getOrElseThrow(failure -> new IllegalArgumentException("Invalid runtime graph", failure));
    if (graph == null || !validId(graph.id()) || !graph.id().equals(event.getGraphId())
        || !graph.flowName().equals(event.getFlowName())) {
      throw new DebugStoreException("Runtime graph does not match the event");
    }
    return graph;
  }

  private FlowDebugEvent parse(Message<?> message) {
    if (message == null) {
      return null;
    }
    Object payload = message.getPayload();
    String json;
    if (payload instanceof byte[] bytes && bytes.length <= MAX_PACKET_BYTES) {
      json = new String(bytes, StandardCharsets.UTF_8);
    } else if (payload instanceof String value && value.length() <= MAX_PACKET_BYTES
        && value.getBytes(StandardCharsets.UTF_8).length <= MAX_PACKET_BYTES) {
      json = value;
    } else {
      return null;
    }
    var decoded = Json.tryToBean(FlowDebugEvent.class, json);
    if (decoded.isFailure()) {
      warn("Invalid debug event envelope", decoded.getCause());
      return null;
    }
    return decoded.get();
  }

  private boolean validId(String id) {
    return id != null && !id.isBlank() && id.length() <= 256;
  }

  private void warn(String reason, Throwable failure) {
    long now = System.currentTimeMillis();
    long next = nextWarning.get();
    if (now >= next && nextWarning.compareAndSet(next, now + 10000)) {
      logger.warn("{} ({})", reason, failure.getClass().getSimpleName());
    }
  }
}
