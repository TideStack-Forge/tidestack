package com.ouroboros.logicflow;

import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.model.FlowModel;

public interface LogicFlowExecutionFacade {
  Try<Object> execute(String flowName, List<Object> inputs);

  /** Executes the already resolved published snapshot, without resolving the name again. */
  default Try<Object> execute(FlowModel flowModel, List<Object> inputs) {
    if (flowModel == null || !Boolean.TRUE.equals(flowModel.isEnabled())) {
      return Try.failure(new LogicFlowExecutionGateException("逻辑流模型不可用"));
    }
    List<DesignException> errors = flowModel.getErrors();
    if (errors != null && !errors.isEmpty()) {
      return Try.failure(new LogicFlowExecutionGateException(
          errors.stream().map(Throwable::getMessage).collect(java.util.stream.Collectors.joining(","))));
    }
    return Try.of(() -> inputs == null ? List.of() : inputs)
        .flatMap(values -> flowModel.execute(values.toArray()));
  }
}
