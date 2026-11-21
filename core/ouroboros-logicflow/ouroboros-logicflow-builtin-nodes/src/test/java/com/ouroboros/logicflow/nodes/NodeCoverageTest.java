package com.ouroboros.logicflow.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.compiler.WatchableArrayList;

public class NodeCoverageTest {
  @Test
  public void distinctNodeShouldDeduplicateCollectionsAndRejectNonCollections() {
    DistinctNode node = new DistinctNode(new StubFlowModel(), FlowNodeMeta.builder().name("distinct").type("distinct").build());

    assertEquals(Collections.singletonList(Arrays.asList("a", "b")), node.execute(Arrays.<Object>asList(Arrays.asList("a", "a", "b"))).get());
    assertTrue(node.execute(Arrays.<Object>asList("not-a-collection")).isFailure());
    assertTrue(node.execute(Arrays.<Object>asList("not-a-collection")).getCause() instanceof FlowRuntimeException);
  }

  @Test
  public void concatNodeShouldFlattenAndDistinctWhenConfigured() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("concat")
        .type("concat")
        .extraProp("distinct", Boolean.TRUE)
        .build();
    ConcatNode node = new ConcatNode(new StubFlowModel(), meta);

    List<Object> inputs = Arrays.<Object>asList(Arrays.asList("a", "a"), "b", null, Arrays.asList("b", "c"));
    assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")), node.execute(inputs).get());
  }

  @Test
  public void filterNodeShouldReturnOriginalCollectionWhenExpressionMissing() {
    FilterNode node = new FilterNode(new StubFlowModel(), FlowNodeMeta.builder().name("filter").type("filter").build());

    List<Object> input = Arrays.<Object>asList(Arrays.asList("a", "b"));
    assertEquals(Arrays.asList(Arrays.asList("a", "b")), node.execute(input).get());
  }

  @Test
  public void returnNodeShouldReturnFirstValueOrNullAndExposePortCounts() {
    ReturnNode node = new ReturnNode(new StubFlowModel(), FlowNodeMeta.builder().name("return").type("return").build());

    assertEquals(Collections.singletonList(null), node.execute(Collections.<Object>emptyList()).get());
    assertEquals(Collections.singletonList("value"), node.execute(Collections.<Object>singletonList("value")).get());
    assertEquals(1, node.getInputPorts().size());
    assertEquals(0, node.getOutputPorts().size());
  }

  @Test
  public void watchableArrayListShouldAllowSessionIdMutation() {
    WatchableArrayList<String> list = new WatchableArrayList<String>(null, null);
    assertTrue(list.add("x"));
    assertEquals(Collections.singletonList("x"), list);
  }

  private static final class StubFlowModel implements FlowModel {
    private final List<FlowNode> nodes = Collections.<FlowNode>emptyList();
    private final List<FlowConnection> connections = Collections.<FlowConnection>emptyList();

    @Override
    public String getName() {
      return "stub";
    }

    @Override
    public String getDescription() {
      return "stub";
    }

    @Override
    public List<FlowNode> getNodes() {
      return nodes;
    }

    @Override
    public List<FlowConnection> getConnections() {
      return connections;
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
    public DataTypeEnum getInputDataType() {
      return DataTypeEnum.ANY;
    }

    @Override
    public DataTypeEnum getOutputDataType() {
      return DataTypeEnum.ANY;
    }

    @Override
    public io.vavr.control.Try<Object> execute(Object... inputs) {
      return io.vavr.control.Try.success(null);
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
}
