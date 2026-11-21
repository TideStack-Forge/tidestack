package com.ouroboros.logicflow.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.Test;
import org.springframework.messaging.Message;

import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.util.Json;

import io.vavr.control.Try;

public class LogicFlowDebugRuntimeAnalyzerTest {

  @Test
  public void serviceLoaderDiscoversDebugRuntimeAnalyzer() {
    boolean discovered = false;
    for (FlowRuntimeAnalyzer analyzer : ServiceLoader.load(FlowRuntimeAnalyzer.class)) {
      if (analyzer instanceof LogicFlowDebugRuntimeAnalyzer) {
        discovered = true;
      }
    }

    assertTrue(discovered);
  }

  @Test
  public void leasedCaptureStoresImmutableExecutionSnapshotsBeforeAsyncTransport() {
    var runtime = new LogicFlowDebugCaptureRuntime();
    var target = new com.ouroboros.logicflow.diagnostic.FlowDebugTarget("crm-app", "test", "container", 1, "helper", 100, "incarnation");
    runtime.start("capture", target, "order-flow", 1, 30000);
    var analyzer = new LogicFlowDebugRuntimeAnalyzer(runtime);
    var context = new com.ouroboros.logicflow.diagnostic.FlowExecutionContext("run", null, null, "main");
    var input = new java.util.LinkedHashMap<String, Object>();
    input.put("value", "before");
    input.put("password", "hidden");
    analyzer.onFlowStart(context, new StubFlowModel(), List.of(input));
    analyzer.onNodeCompleted(context, new StubFlowModel(), new StubFlowNode(), List.of(input), List.of("output"));
    input.put("value", "after");

    var events = runtime.read("capture", target, 0, 100);
    assertEquals(2, events.size());
    var event = events.getLast();
    assertEquals("2.0", event.getSchemaVersion());
    assertEquals("crm-app", event.getAppName());
    assertEquals("run", event.getRunId());
    assertEquals("order-flow", event.getFlowName());
    assertEquals("ValidateOrder", event.getNodeName());
    String json = Json.tryToJsonString(event).get();
    assertTrue(json.contains("before"));
    assertTrue(!json.contains("after"));
    assertTrue(!json.contains("hidden"));
    assertTrue(json.contains("redacted"));
  }

  private static class CaptureProducer implements MQProducer {
    private String routingKey;
    private Message<?> message;

    @Override
    public void send(Message<?> message) {
      this.message = message;
    }

    @Override
    public void send(String routingKey, Message<?> message) {
      this.routingKey = routingKey;
      this.message = message;
    }
  }

  static class StubFlowModel implements FlowModel {
    @Override
    public String getName() {
      return "order-flow";
    }

    @Override
    public String getDescription() {
      return "desc";
    }

    @Override
    public List<FlowNode> getNodes() {
      return Collections.emptyList();
    }

    @Override
    public List<FlowConnection> getConnections() {
      return Collections.emptyList();
    }

    @Override
    public Boolean isEnabled() {
      return Boolean.TRUE;
    }

    @Override
    public Boolean isValid() {
      return Boolean.TRUE;
    }

    @Override
    public com.ouroboros.logicflow.model.DataTypeEnum getInputDataType() {
      return null;
    }

    @Override
    public com.ouroboros.logicflow.model.DataTypeEnum getOutputDataType() {
      return null;
    }

    @Override
    public Try<Object> execute(Object... inputs) {
      return Try.success(null);
    }

    @Override
    public FlowNode getEntryNode() {
      return null;
    }

    @Override
    public FlowNode getExitNode() {
      return null;
    }

    @Override
    public FlowNode getNode(String name) {
      return null;
    }

    @Override
    public List<com.ouroboros.logicflow.exception.DesignException> getErrors() {
      return Collections.emptyList();
    }
  }

  static class StubFlowNode implements FlowNode {
    @Override
    public String getName() {
      return "ValidateOrder";
    }

    @Override
    public String getDescription() {
      return "desc";
    }

    @Override
    public String getType() {
      return "script";
    }

    @Override
    public List<FlowNodePort> getInputPorts() {
      return Collections.emptyList();
    }

    @Override
    public List<FlowNodePort> getOutputPorts() {
      return Collections.emptyList();
    }

    @Override
    public Boolean isAsync() {
      return Boolean.FALSE;
    }

    @Override
    public Boolean isEnabled() {
      return Boolean.TRUE;
    }

    @Override
    public Boolean isValid() {
      return Boolean.TRUE;
    }

    @Override
    public Try<List<Object>> execute(List<Object> inputs) {
      return Try.success(Collections.emptyList());
    }

    @Override
    public List<FlowConnection> getIncomingConnections() {
      return Collections.emptyList();
    }

    @Override
    public List<FlowConnection> getOutgoingConnections() {
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.logicflow.exception.NodeDesignException> getErrors() {
      return Collections.emptyList();
    }
  }
}
