package com.ouroboros.logicflow;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLogicFlowExecutionFacadeTest {

  private final DefaultLogicFlowExecutionFacade facade = new DefaultLogicFlowExecutionFacade();

  @Test
  void executeShouldFailWhenFlowNameIsBlank() {
    Try<Object> result = facade.execute("", List.of());

    assertTrue(result.isFailure());
    LogicFlowExecutionGateException error = assertInstanceOf(LogicFlowExecutionGateException.class, result.getCause());
    assertEquals("逻辑流名称不能为空", error.getMessage());
  }

  @Test
  void executeShouldFailWhenFlowModelIsMissing() throws Exception {
    withProviders(Collections.emptyList(), () -> {
      Try<Object> result = facade.execute("missing-flow", List.of("payload"));

      assertTrue(result.isFailure());
      LogicFlowExecutionGateException error = assertInstanceOf(LogicFlowExecutionGateException.class, result.getCause());
      assertEquals("逻辑流模型 missing-flow 未定义", error.getMessage());
    });
  }

  @Test
  void executeShouldFailWhenFlowModelHasDesignErrors() throws Exception {
    StubFlowModel flowModel = new StubFlowModel("broken-flow")
        .setErrors(Collections.singletonList(new TestDesignException("missing entry node")));

    withProviders(Collections.singletonList(new TestFlowModelProvider(flowModel)), () -> {
      Try<Object> result = facade.execute("broken-flow", List.of("payload"));

      assertTrue(result.isFailure());
      LogicFlowExecutionGateException error = assertInstanceOf(LogicFlowExecutionGateException.class, result.getCause());
      assertEquals("missing entry node", error.getMessage());
    });
  }

  @Test
  void executeShouldResolveLatestFlowModelAndForwardInputs() throws Exception {
    StubFlowModel flowModel = new StubFlowModel("ok-flow")
        .setExecutionResult(Try.success("done"));

    withProviders(Collections.singletonList(new TestFlowModelProvider(flowModel)), () -> {
      Try<Object> result = facade.execute("ok-flow", List.of("payload", 123));

      assertTrue(result.isSuccess());
      assertEquals("done", result.get());
      assertArrayEquals(new Object[] { "payload", 123 }, flowModel.executedInputs);
      assertSame(flowModel.executedInputs, flowModel.lastObservedInputs);
    });
  }

  @Test
  void executeShouldTreatNullInputsAsEmptyList() throws Exception {
    StubFlowModel flowModel = new StubFlowModel("empty-flow")
        .setExecutionResult(Try.success("done"));

    withProviders(Collections.singletonList(new TestFlowModelProvider(flowModel)), () -> {
      Try<Object> result = facade.execute("empty-flow", null);

      assertTrue(result.isSuccess());
      assertArrayEquals(new Object[0], flowModel.executedInputs);
    });
  }

  private static void withProviders(List<FlowModelProvider> providers, ThrowingRunnable assertion) throws Exception {
    Field providersField = FlowModelCenter.class.getDeclaredField("providers");
    providersField.setAccessible(true);
    Object originalProviders = providersField.get(null);
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
    private final FlowModel flowModel;

    private TestFlowModelProvider(FlowModel flowModel) {
      this.flowModel = flowModel;
    }

    @Override
    public Optional<FlowModel> getFlowModel(String flowName) {
      if (flowModel.getName().equals(flowName)) {
        return Optional.of(flowModel);
      }
      return Optional.empty();
    }

    @Override
    public Map<String, FlowModel> getFlowModelMap() {
      return Collections.singletonMap(flowModel.getName(), flowModel);
    }
  }

  private static class StubFlowModel implements FlowModel {
    private final String name;
    private List<DesignException> errors = Collections.emptyList();
    private Try<Object> executionResult = Try.success(null);
    private Object[] executedInputs;
    private Object[] lastObservedInputs;

    private StubFlowModel(String name) {
      this.name = name;
    }

    private StubFlowModel setErrors(List<DesignException> errors) {
      this.errors = errors;
      return this;
    }

    private StubFlowModel setExecutionResult(Try<Object> executionResult) {
      this.executionResult = executionResult;
      return this;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return name;
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
      return true;
    }

    @Override
    public Boolean isValid() {
      return errors.isEmpty();
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
      executedInputs = inputs;
      lastObservedInputs = inputs;
      return executionResult;
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
    public List<DesignException> getErrors() {
      return errors;
    }
  }

  private static class TestDesignException extends DesignException {
    private TestDesignException(String message) {
      super(message);
    }
  }
}
