package com.ouroboros.logicflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelRevisionTest {
  @Test
  public void revisionShouldBecomeAvailableDuringInitialization() {
    var model = new DefaultFlowModel(FlowModelMeta.builder().name("uninitialized").build());

    assertThrows(IllegalStateException.class, model::getRevision);
    model.initialize(NodeReferenceCatalog.empty());
    assertTrue(model.getRevision().matches("[0-9a-f]{64}"));
  }

  @Test
  public void independentlyLoadedPublishedDefinitionsShouldHaveTheSameRevision() {
    DefaultFlowModel first = FlowModelFixtures.loadValidationFixture("valid/minimal-linear.json");
    DefaultFlowModel second = FlowModelFixtures.loadValidationFixture("valid/minimal-linear.json");

    assertEquals(first.getRevision(), second.getRevision());
    assertTrue(first.getRevision().matches("[0-9a-f]{64}"));
  }
}
