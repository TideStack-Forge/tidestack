package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelCallFlowExecutionTest {
  @Test
  public void callFlowFixtureShouldInvokeChildFlowSuccessfully() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-basic.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, "child-ok");
  }

  @Test
  public void callFlowFixtureShouldWrapResultWhenWrapperKeyConfigured() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-result-wrapper.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, Collections.singletonMap("wrapped", "child-ok"));
  }

  @Test
  public void callFlowForEachFixtureShouldExecuteChildFlowForEachCollectionItem() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-for-each.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, Arrays.asList("child-ok", "child-ok"), Arrays.asList("a", "b"));
  }

  @Test
  public void callFlowForEachFixtureShouldReturnEmptyListForEmptyCollection() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-for-each.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void callFlowForEachFixtureShouldRejectNullInput() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-for-each.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, new Object[]{null});
    var runtimeFailure = FlowModelAssertions.assertFlowRuntimeFailure(failure, "callChild");

    assertTrue(runtimeFailure.getMessage().contains("输入不能为null"));
    assertEquals(Collections.singletonList(null), runtimeFailure.getInputs());
  }

  @Test
  public void callFlowForEachFixtureShouldRejectNonCollectionInput() {
    var flowModel = loadCallFlowFixture("call-flow/call-flow-for-each.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "not-a-collection");
    var runtimeFailure = FlowModelAssertions.assertFlowRuntimeFailure(failure, "callChild");

    assertTrue(runtimeFailure.getMessage().contains("输入必须为集合类型"));
    assertEquals(Collections.singletonList("not-a-collection"), runtimeFailure.getInputs());
  }

  private DefaultFlowModel loadCallFlowFixture(String relativePath) {
    var childFlow = FlowModelFixtures.loadExecutionFixture("call-flow/child-return-const.json");
    return FlowModelFixtures.loadExecutionFixture(
        relativePath,
        Map.of(childFlow.getName(), childFlow)
    );
  }
}
