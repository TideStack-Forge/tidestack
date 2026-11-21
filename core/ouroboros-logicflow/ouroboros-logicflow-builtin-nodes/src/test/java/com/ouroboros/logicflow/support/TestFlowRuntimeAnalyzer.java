package com.ouroboros.logicflow.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.vavr.control.Either;

import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowModel;

public class TestFlowRuntimeAnalyzer implements FlowRuntimeAnalyzer {
  private static final AtomicReference<Object> FLOW_RESULT = new AtomicReference<Object>();
  private static final AtomicReference<Throwable> FLOW_FAILURE = new AtomicReference<Throwable>();
  private static final AtomicReference<CountDownLatch> FLOW_COMPLETION_LATCH = new AtomicReference<CountDownLatch>(new CountDownLatch(1));
  private static final Map<String, AtomicInteger> NODE_START_COUNT = new ConcurrentHashMap<String, AtomicInteger>();
  private static final Map<String, AtomicInteger> NODE_FAILURE_COUNT = new ConcurrentHashMap<String, AtomicInteger>();

  public static void reset() {
    FLOW_RESULT.set(null);
    FLOW_FAILURE.set(null);
    FLOW_COMPLETION_LATCH.set(new CountDownLatch(1));
    NODE_START_COUNT.clear();
    NODE_FAILURE_COUNT.clear();
  }

  public static Object awaitFlowResult() throws InterruptedException {
    var latch = FLOW_COMPLETION_LATCH.get();
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Timed out waiting for flow completion callback");
    }
    var failure = FLOW_FAILURE.get();
    if (failure != null) {
      throw new AssertionError("Unexpected flow failure callback", failure);
    }
    return FLOW_RESULT.get();
  }

  public static Throwable awaitFlowFailure() throws InterruptedException {
    var latch = FLOW_COMPLETION_LATCH.get();
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Timed out waiting for flow completion callback");
    }
    var result = FLOW_RESULT.get();
    if (result != null) {
      throw new AssertionError("Unexpected flow completed callback result: " + result);
    }
    var failure = FLOW_FAILURE.get();
    if (failure == null) {
      throw new AssertionError("Expected flow failure callback but none was received");
    }
    return failure;
  }

  public static int getNodeStartCount(String nodeName) {
    return NODE_START_COUNT.getOrDefault(nodeName, new AtomicInteger(0)).get();
  }

  public static int getNodeFailureCount(String nodeName) {
    return NODE_FAILURE_COUNT.getOrDefault(nodeName, new AtomicInteger(0)).get();
  }

  @Override
  public void onFlowFailed(String sessionId,
                           FlowModel flowModel,
                           List<Object> inputs,
                           List<Either<Throwable, Object>> nodeResults,
                           Throwable throwable) {
    FLOW_FAILURE.set(throwable);
    FLOW_COMPLETION_LATCH.get().countDown();
  }

  @Override
  public void onFlowCompleted(String sessionId,
                              FlowModel flowModel,
                              List<Object> flowInputs,
                              List<Either<Throwable, Object>> nodeResults,
                              Object flowResult) {
    FLOW_RESULT.set(flowResult);
    FLOW_COMPLETION_LATCH.get().countDown();
  }

  @Override
  public void onNodeStart(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs) {
    NODE_START_COUNT.computeIfAbsent(flowNode.getName(), key -> new AtomicInteger()).incrementAndGet();
  }

  @Override
  public void onNodeFailed(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs, Throwable throwable) {
    NODE_FAILURE_COUNT.computeIfAbsent(flowNode.getName(), key -> new AtomicInteger()).incrementAndGet();
  }
}
