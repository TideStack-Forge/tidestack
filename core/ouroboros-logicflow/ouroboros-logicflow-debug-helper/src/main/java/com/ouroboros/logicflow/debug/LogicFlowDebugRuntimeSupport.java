package com.ouroboros.logicflow.debug;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQProducer;

@Component
public class LogicFlowDebugRuntimeSupport implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LogicFlowDebugRuntimeSupport.class);
  private static final AtomicReference<LogicFlowDebugRuntimeSupport> CURRENT = new AtomicReference<>();
  private final boolean enabled;
  private final String appName;
  private final LogicFlowDebugCaptureRuntime runtime = new LogicFlowDebugCaptureRuntime();
  private final MQProducer producer;
  private final ScheduledExecutorService sender;
  private long nextWarning;

  public LogicFlowDebugRuntimeSupport(MQBridge mqBridge,
      @Value("${spring.application.name:default}") String appName,
      @Value("${ouroboros.logicflow.debug.enabled:true}") boolean enabled) {
    this.appName = appName;
    this.enabled = enabled;
    if (!CURRENT.compareAndSet(null, this)) {
      throw new IllegalStateException("Multiple LogicFlow debug helper contexts in the same classloader");
    }
    try {
      producer = mqBridge.bindingProducer("logicflow-debug-event-producer")
          .destination("ouroboros.logicflow.debug")
          .extraProp("declareExchange", true)
          .extraProp("exchangeType", "topic")
          .build();
      sender = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "logicflow-debug-sender");
        thread.setDaemon(true);
        return thread;
      });
      sender.scheduleWithFixedDelay(this::sendPending, 100, 100, TimeUnit.MILLISECONDS);
    } catch (RuntimeException failure) {
      CURRENT.compareAndSet(this, null);
      throw failure;
    }
  }

  public LogicFlowDebugCaptureRuntime runtime() {
    if (!enabled) {
      throw new IllegalStateException("LogicFlow debugging is disabled in this process");
    }
    return runtime;
  }

  public String applicationName() {
    return appName;
  }

  static LogicFlowDebugCaptureRuntime currentRuntime() {
    var support = CURRENT.get();
    return support == null || !support.enabled ? null : support.runtime;
  }

  void sendPending() {
    if (!enabled) {
      return;
    }
    try {
      for (var packet : runtime.duePackets()) {
        producer.send(MessageBuilder.withPayload(packet.payload()).build());
      }
      runtime.transportSucceeded();
    } catch (RuntimeException failure) {
      runtime.transportFailed(failure);
      long now = System.nanoTime();
      if (nextWarning == 0 || now - nextWarning >= 0) {
        nextWarning = now + TimeUnit.MINUTES.toNanos(1);
        LOGGER.warn("LogicFlow debug transport failed; bounded retry window retained", failure);
      }
    }
  }

  @Override
  @PreDestroy
  public void close() {
    CURRENT.compareAndSet(this, null);
    sender.shutdownNow();
  }
}
