package com.ouroboros.logicflow.support;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.Priority;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelProvider;

@Priority(100)
public class TestFlowModelProvider implements FlowModelProvider {
  @Override
  public Optional<FlowModel> getFlowModel(String flowName) {
    return Optional.ofNullable(loadKnownFlow(flowName));
  }

  @Override
  public Map<String, FlowModel> getFlowModelMap() {
    var childFlow = loadKnownFlow("child-return-const");
    if (childFlow == null) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(childFlow.getName(), childFlow);
  }

  private FlowModel loadKnownFlow(String flowName) {
    if (!"child-return-const".equals(flowName)) {
      return null;
    }
    return FlowModelFixtures.loadExecutionFixture("call-flow/child-return-const.json");
  }
}
