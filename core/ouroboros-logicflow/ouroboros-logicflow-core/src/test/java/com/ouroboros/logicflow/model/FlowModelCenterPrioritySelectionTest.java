package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;

public class FlowModelCenterPrioritySelectionTest {
  @Test
  public void getFlowModelShouldPreferHigherPriorityEnabledModelEvenWhenItIsInvalid() throws Exception {
    var highPriority = new TestFlowModel("shared-flow", "high", true, false);
    var lowPriority = new TestFlowModel("shared-flow", "low", true, true);

    withProviders(Arrays.asList(
        new TestFlowModelProvider(highPriority),
        new TestFlowModelProvider(lowPriority)
    ), () -> {
      var selected = FlowModelCenter.getFlowModel("shared-flow");

      assertTrue(selected.isPresent());
      assertSame(highPriority, selected.get());
      assertFalse(selected.get().isValid());
    });
  }

  @Test
  public void getFlowModelShouldFallBackToLowerPriorityModelWhenHigherPriorityModelIsDisabled() throws Exception {
    var highPriorityDisabled = new TestFlowModel("shared-flow", "high-disabled", false, false);
    var lowPriority = new TestFlowModel("shared-flow", "low", true, true);

    withProviders(Arrays.asList(
        new TestFlowModelProvider(highPriorityDisabled),
        new TestFlowModelProvider(lowPriority)
    ), () -> {
      var selected = FlowModelCenter.getFlowModel("shared-flow");

      assertTrue(selected.isPresent());
      assertSame(lowPriority, selected.get());
      assertEquals("low", selected.get().getDescription());
    });
  }

  @Test
  public void getFlowModelMapShouldKeepHigherPriorityEnabledModelForSameName() throws Exception {
    var highPriority = new TestFlowModel("shared-flow", "high", true, false);
    var lowPriority = new TestFlowModel("shared-flow", "low", true, true);

    withProviders(Arrays.asList(
        new TestFlowModelProvider(highPriority),
        new TestFlowModelProvider(lowPriority)
    ), () -> {
      var selected = FlowModelCenter.getFlowModelMap().get("shared-flow");

      assertSame(highPriority, selected);
      assertEquals("high", selected.getDescription());
    });
  }

  private static void withProviders(List<FlowModelProvider> providers, ThrowingRunnable assertion) throws Exception {
    var providersField = FlowModelCenter.class.getDeclaredField("providers");
    providersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    var originalProviders = (List<FlowModelProvider>) providersField.get(null);
    try {
      providersField.set(null, providers);
      assertion.run();
    } finally {
      providersField.set(null, originalProviders);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static class TestFlowModelProvider implements FlowModelProvider {
    private final Map<String, FlowModel> flowModels;

    private TestFlowModelProvider(FlowModel... flowModels) {
      this.flowModels = new HashMap<>();
      for (var flowModel : flowModels) {
        this.flowModels.put(flowModel.getName(), flowModel);
      }
    }

    @Override
    public Optional<FlowModel> getFlowModel(String flowName) {
      return Optional.ofNullable(flowModels.get(flowName));
    }

    @Override
    public Map<String, FlowModel> getFlowModelMap() {
      return flowModels;
    }
  }

  private static class TestFlowModel implements FlowModel {
    private final String name;
    private final String description;
    private final Boolean enabled;
    private final Boolean valid;

    private TestFlowModel(String name, String description, Boolean enabled, Boolean valid) {
      this.name = name;
      this.description = description;
      this.enabled = enabled;
      this.valid = valid;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
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
      return enabled;
    }

    @Override
    public Boolean isValid() {
      return valid;
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
    public Try<Object> execute(Object... inputs) {
      return Try.failure(new UnsupportedOperationException());
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
    public FlowNode getErrorEntryNode() {
      return null;
    }

    @Override
    public FlowNode getNode(String name) {
      return null;
    }

    @Override
    public List<DesignException> getErrors() {
      return Collections.emptyList();
    }
  }
}
