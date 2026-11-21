package com.ouroboros.logicflow.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowInputTransfer;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.results.Decision;
import com.ouroboros.logicflow.support.LifecycleRecordingAnalyzer;

public class FlowRuntimeLifecycleTest {
  private final List<String> events = new CopyOnWriteArrayList<>();

  @BeforeEach
  public void recordDiagnostics() {
    LifecycleRecordingAnalyzer.observe(events::add);
  }

  @AfterEach
  public void releaseObserver() {
    com.ouroboros.logicflow.support.TestFlowRuntimeAnalyzer.enabled(true);
    LifecycleRecordingAnalyzer.enabledWhen(() -> true);
    LifecycleRecordingAnalyzer.flowsWhen(model -> true);
    LifecycleRecordingAnalyzer.observe(event -> { });
    LifecycleRecordingAnalyzer.observeInvocations(context -> { });
    LifecycleRecordingAnalyzer.observeTransfers(transfer -> { });
  }

  @Test
  public void synchronousNodeStartsBeforeItsSideEffect() {
    assertExecutionOrder(false);
  }

  @Test
  public void asynchronousNodeStartsBeforeItsSideEffect() {
    assertExecutionOrder(true);
  }

  private void assertExecutionOrder(boolean async) {
    var node = new Node("work", async, inputs -> {
      events.add("side-effect");
      return inputs;
    });

    var result = new Model(List.of(node)).execute("input");

    assertEquals("input", result.get());
    assertEquals(List.of("work:started:[input]", "side-effect", "work:completed:[input]", "flow:completed"), events);
  }

  @Test
  public void inactiveNodeHasOnlySkippedTerminal() {
    var node = new Node("inactive", false, inputs -> {
      events.add("unexpected-side-effect");
      return inputs;
    });

    new Model(List.of(node)).execute(Decision.unsatisfied());

    assertEquals(List.of("inactive:skipped"), events);
  }

  @Test
  public void failedNodeReportsFailureAfterStartWithoutChangingTheCause() {
    var failure = new IllegalArgumentException("business failure");
    var node = new Node("work", false, inputs -> { throw failure; });

    var result = new Model(List.of(node)).execute("input");

    assertTrue(result.isFailure());
    assertEquals(failure, result.getCause().getCause());
    assertEquals(List.of("work:started:[input]", "work:failed"), events);
  }

  @Test
  public void analyzerFailureDoesNotPreventBusinessExecution() {
    LifecycleRecordingAnalyzer.observe(event -> { throw new IllegalStateException("diagnostic unavailable"); });
    var node = new Node("work", false, Function.identity());

    assertEquals("input", new Model(List.of(node)).execute("input").get());
  }

  @Test
  public void disabledAnalyzerResumesOnNextInvocationWithoutRecompilation() {
    var enabled = new java.util.concurrent.atomic.AtomicBoolean(false);
    LifecycleRecordingAnalyzer.enabledWhen(enabled::get);
    var compiled = FunctionalFlowCompiler.INSTANCE.compile(new Model(List.of(new Node("work", false, Function.identity()))));
    assertEquals("idle", compiled.apply(new Object[]{"idle"}).get());
    assertTrue(events.isEmpty());
    enabled.set(true);
    assertEquals("active", compiled.apply(new Object[]{"active"}).get());
    assertEquals(List.of("work:started:[active]", "work:completed:[active]", "flow:completed"), events);
  }

  @Test
  public void analyzerAdmissionFailureDoesNotPreventBusinessExecution() {
    LifecycleRecordingAnalyzer.enabledWhen(() -> { throw new IllegalStateException("admission unavailable"); });
    assertEquals("input", new Model(List.of(new Node("work", false, Function.identity()))).execute("input").get());
    assertTrue(events.isEmpty());
  }

  @Test
  public void nestedAsyncCallRetainsItsParentAndCleansPooledThreadContext() {
    List<FlowExecutionContext> contexts = new CopyOnWriteArrayList<>();
    LifecycleRecordingAnalyzer.observeInvocations(contexts::add);
    var child = new Model(List.of(new Node("child", false, Function.identity())));
    var caller = new Node("caller", true, inputs -> List.of(child.execute(inputs.getFirst()).get()));

    assertEquals("input", new Model(List.of(caller)).execute("input").get());
    assertEquals("next", child.execute("next").get());

    assertEquals(3, contexts.size());
    assertEquals(contexts.get(0).invocationId(), contexts.get(1).parentInvocationId());
    assertEquals("caller", contexts.get(1).callerNodeName());
    assertEquals(null, contexts.get(2).parentInvocationId());
    assertTrue(!contexts.get(0).invocationId().equals(contexts.get(1).invocationId()));
  }

  @Test
  public void failedDependencyMarksDownstreamBlockedWithoutStartingIt() {
    var upstream = new Node("upstream", true, inputs -> { throw new IllegalStateException("broken"); });
    var downstream = new Node("downstream", false, inputs -> { throw new AssertionError("must not execute"); });
    connect(upstream, downstream);

    assertTrue(new Model(List.of(upstream, downstream)).execute("input").isFailure());

    assertEquals(List.of("upstream:started:[input]", "upstream:failed", "downstream:blocked"), events);
  }

  @Test
  public void inputTransferNamesTheActualSelectedSourceForMergedPort() {
    List<FlowInputTransfer> transfers = new CopyOnWriteArrayList<>();
    LifecycleRecordingAnalyzer.observeTransfers(transfers::add);
    var inactive = new Node("inactive", false, inputs -> List.of(Decision.unsatisfied()));
    var selected = new Node("selected", true, inputs -> List.of("selected-value"));
    var sink = new Node("sink", false, Function.identity());
    connect(inactive, sink);
    connect(selected, sink);

    assertEquals("selected-value", new Model(List.of(inactive, selected, sink)).execute("input").get());

    var incoming = transfers.stream().filter(t -> "sink".equals(t.targetNode())).toList();
    assertEquals(2, incoming.size());
    assertEquals(new FlowInputTransfer("inactive", 0, "sink", 0, false, false), incoming.get(0));
    assertEquals(new FlowInputTransfer("selected", 0, "sink", 0, true, true), incoming.get(1));
  }

  @Test
  public void mergedPortSelectsFirstActiveSourceEvenWhenItsValueIsNull() {
    List<FlowInputTransfer> transfers = new CopyOnWriteArrayList<>();
    LifecycleRecordingAnalyzer.observeTransfers(transfers::add);
    var first = new Node("first", false, inputs -> Collections.singletonList(null));
    var second = new Node("second", false, inputs -> List.of("unused"));
    var sink = new Node("sink", false, inputs -> List.of(inputs.getFirst() == null ? "accepted-null" : "wrong-value"));
    connect(first, sink);
    connect(second, sink);

    assertEquals("accepted-null", new Model(List.of(first, second, sink)).execute("input").get());
    assertEquals(List.of(new FlowInputTransfer("first", 0, "sink", 0, true, true),
        new FlowInputTransfer("second", 0, "sink", 0, true, false)),
        transfers.stream().filter(transfer -> "sink".equals(transfer.targetNode())).toList());
  }

  private void connect(Node source, Node target) {
    var connection = new Connection(source, target);
    source.outgoing().add(connection);
    target.incoming().add(connection);
  }

  @Test
  public void unobservedMiddleFlowBreaksAncestorLinkAndDoesNotHideObservedGrandchild() {
    com.ouroboros.logicflow.support.TestFlowRuntimeAnalyzer.enabled(false);
    var contexts = new ArrayList<FlowExecutionContext>();
    LifecycleRecordingAnalyzer.observeInvocations(contexts::add);
    LifecycleRecordingAnalyzer.flowsWhen(model -> !model.getName().equals("middle"));
    var child = new Model("child", List.of(new Node("leaf", false, Function.identity())));
    var middle = new Model("middle", List.of(new Node("call-child", false, inputs -> List.of(child.execute(inputs.getFirst()).get()))));
    var root = new Model("root", List.of(new Node("call-middle", false, inputs -> List.of(middle.execute(inputs.getFirst()).get()))));
    assertEquals("input", root.execute("input").get());
    assertEquals(2, contexts.size());
    assertEquals(null, contexts.get(1).parentInvocationId());
    assertEquals(null, contexts.get(1).callerNodeName());
  }

  private record Model(String name, List<FlowNode> nodes) implements FlowModel {
    Model(List<FlowNode> nodes) { this("lifecycle-test", nodes); }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return "Diagnostic lifecycle contract"; }
    @Override public List<FlowNode> getNodes() { return nodes; }
    @Override public List<FlowConnection> getConnections() { return nodes.stream().flatMap(n -> n.getOutgoingConnections().stream()).toList(); }
    @Override public Boolean isEnabled() { return true; }
    @Override public Boolean isValid() { return true; }
    @Override public DataTypeEnum getInputDataType() { return DataTypeEnum.ANY; }
    @Override public DataTypeEnum getOutputDataType() { return DataTypeEnum.ANY; }
    @Override public Try<Object> execute(Object... inputs) { return FunctionalFlowCompiler.INSTANCE.compile(this).apply(inputs); }
    @Override public FlowNode getEntryNode() { return null; }
    @Override public FlowNode getExitNode() { return nodes.getLast(); }
    @Override public FlowNode getNode(String name) { return nodes.stream().filter(n -> name.equals(n.getName())).findFirst().orElse(null); }
    @Override public List<DesignException> getErrors() { return List.of(); }
  }

  private record Node(String name, boolean async, Function<List<Object>, List<Object>> execution,
                      List<FlowConnection> incoming, List<FlowConnection> outgoing) implements FlowNode {
    Node(String name, boolean async, Function<List<Object>, List<Object>> execution) {
      this(name, async, execution, new ArrayList<>(), new ArrayList<>());
    }
    @Override public boolean equals(Object value) { return this == value; }
    @Override public int hashCode() { return System.identityHashCode(this); }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return name; }
    @Override public String getType() { return "test"; }
    @Override public List<FlowNodePort> getInputPorts() { return Collections.singletonList(null); }
    @Override public List<FlowNodePort> getOutputPorts() { return Collections.singletonList(null); }
    @Override public Boolean isAsync() { return async; }
    @Override public Boolean isEnabled() { return true; }
    @Override public Boolean isValid() { return true; }
    @Override public Try<List<Object>> execute(List<Object> inputs) { return Try.of(() -> execution.apply(inputs)); }
    @Override public List<FlowConnection> getIncomingConnections() { return incoming; }
    @Override public List<FlowConnection> getOutgoingConnections() { return outgoing; }
    @Override public List<NodeDesignException> getErrors() { return List.of(); }
  }

  private record Connection(Node source, Node target) implements FlowConnection {
    @Override public String getLabel() { return ""; }
    @Override public String getDescription() { return ""; }
    @Override public String getSourceName() { return source.name(); }
    @Override public Integer getSourceOutputPort() { return 0; }
    @Override public String getTargetName() { return target.name(); }
    @Override public Integer getTargetInputPort() { return 0; }
    @Override public FlowNode getSourceNode() { return source; }
    @Override public FlowNode getTargetNode() { return target; }
  }
}
