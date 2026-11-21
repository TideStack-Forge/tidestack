package com.ouroboros.logicflow.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class NodeFactoryDuplicateBuilderTest {
  @Test
  public void duplicateNodeTypeBuildersShouldFailFast() {
    List<NodeBuilder<?>> originalBuilders = NodeFactory.builders;
    FlowNodeMeta duplicateTypeNode = FlowNodeMeta.builder()
        .name("duplicate")
        .type("duplicate")
        .build();

    try {
      NodeFactory.builders = List.of(new DuplicateTypeBuilder(), new DuplicateTypeBuilder());
      NodeFactory.createNode(null, duplicateTypeNode, NodeReferenceCatalog.empty());
      fail("Expected duplicate node type builders to fail fast");
    } catch (NodeDesignException exception) {
      assertSame(duplicateTypeNode, exception.getNode());
      assertEquals("type", exception.getFieldPath());
      assertTrue(exception.getMessage().contains("duplicate"));
    } finally {
      NodeFactory.builders = originalBuilders;
    }
  }

  private static class DuplicateTypeBuilder implements NodeBuilder<FlowNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "duplicate".equals(meta.getType());
    }

    @Override
    public FlowNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      throw new AssertionError("Duplicate builders should be rejected before build");
    }
  }
}
