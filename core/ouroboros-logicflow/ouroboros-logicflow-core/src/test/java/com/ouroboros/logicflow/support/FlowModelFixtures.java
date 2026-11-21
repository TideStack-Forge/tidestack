package com.ouroboros.logicflow.support;

import com.ouroboros.logicflow.model.DefaultFlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.util.LogicFlowJson;
import com.ouroboros.logicflow.util.LogicFlowResources;

public final class FlowModelFixtures {
  private static final String EXECUTION_ROOT = "fixtures/execution/";
  private static final String SMOKE_ROOT = "fixtures/smoke/";
  private static final String VALIDATION_ROOT = "fixtures/validation/";

  private FlowModelFixtures() {
  }

  public static DefaultFlowModel loadSmokeFixture(String relativePath) {
    return loadFixture(SMOKE_ROOT + relativePath);
  }

  public static DefaultFlowModel loadValidationFixture(String relativePath) {
    return loadFixture(VALIDATION_ROOT + relativePath);
  }

  public static DefaultFlowModel loadExecutionFixture(String relativePath) {
    return loadFixture(EXECUTION_ROOT + relativePath);
  }

  public static DefaultFlowModel loadFixture(String resourcePath) {
    var json = LogicFlowResources.resourceContents(resourcePath)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Fixture not found: " + resourcePath));
    var meta = LogicFlowJson.toBean(FlowModelMeta.class, json);
    if (meta == null) {
      throw new IllegalArgumentException("Fixture cannot be parsed: " + resourcePath);
    }
    return FlowModelFactory.build(meta, NodeReferenceCatalog.empty());
  }
}
