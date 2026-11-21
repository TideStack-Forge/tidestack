package com.ouroboros.logicflow.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

class FlowModelFactoryTest {
  @Test
  void failedInitializationCannotBeRetried() {
    var factory = FlowModelFactory.prepare(Map.of(
        "broken-flow",
        FlowModelMeta.builder().name("broken-flow").nodes(null).build()
    ));

    assertThrows(NullPointerException.class, () -> factory.initialize(NodeReferenceCatalog.empty()));
    assertThrows(IllegalStateException.class, () -> factory.initialize(NodeReferenceCatalog.empty()));
  }
}
