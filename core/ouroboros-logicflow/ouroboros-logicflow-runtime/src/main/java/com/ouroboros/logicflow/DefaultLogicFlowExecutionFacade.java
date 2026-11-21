package com.ouroboros.logicflow;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelCenter;

@Component
public class DefaultLogicFlowExecutionFacade implements LogicFlowExecutionFacade {
  private static final Logger logger = LoggerFactory.getLogger(DefaultLogicFlowExecutionFacade.class);

  @Override
  public Try<Object> execute(String flowName, List<Object> inputs) {
    return Try.of(() -> flowName)
        .flatMap(this::requireFlowName)
        .map(validFlowName -> new ExecutionRequest(validFlowName, normalizeInputs(inputs)))
        .flatMap(request -> resolveFlowModel(request.flowName)
            .map(flowModel -> new ExecutionContext(request, flowModel)))
        .flatMap(this::executeResolvedFlowModel);
  }

  @Override
  public Try<Object> execute(FlowModel flowModel, List<Object> inputs) {
    return Try.of(() -> new ExecutionContext(
            new ExecutionRequest(flowModel == null ? null : flowModel.getName(), normalizeInputs(inputs)), flowModel))
        .flatMap(this::executeResolvedFlowModel);
  }

  private Try<Object> executeResolvedFlowModel(ExecutionContext context) {
    return checkFlowModel(context).flatMap(ctx -> ctx.flowModel.execute(ctx.request.inputs.toArray()));
  }

  private Try<String> requireFlowName(String flowName) {
    if (StringUtils.isBlank(flowName)) {
      return Try.failure(new LogicFlowExecutionGateException("逻辑流名称不能为空"));
    }
    return Try.success(flowName);
  }

  private List<Object> normalizeInputs(List<Object> inputs) {
    return inputs == null ? Collections.emptyList() : inputs;
  }

  private Try<FlowModel> resolveFlowModel(String flowName) {
    Optional<FlowModel> flowModel = FlowModelCenter.getFlowModel(flowName);
    if (!flowModel.isPresent()) {
      logger.warn("逻辑流模型 {} 未定义", flowName);
      return Try.failure(new LogicFlowExecutionGateException("逻辑流模型 " + flowName + " 未定义"));
    }
    return Try.success(flowModel.get());
  }

  private Try<ExecutionContext> checkFlowModel(ExecutionContext context) {
    if (context.flowModel == null || !Boolean.TRUE.equals(context.flowModel.isEnabled())) {
      return Try.failure(new LogicFlowExecutionGateException("逻辑流模型不可用"));
    }
    List<DesignException> flowDesignExceptions = context.flowModel.getErrors();
    if (ObjectUtils.isEmpty(flowDesignExceptions)) {
      return Try.success(context);
    }
    String message = flowDesignExceptions.stream().map(Throwable::getMessage).collect(Collectors.joining(","));
    logger.warn("逻辑流模型 {} 定义存在错误 {}", context.flowModel.getName(), message);
    return Try.failure(new LogicFlowExecutionGateException(message));
  }

  private static class ExecutionRequest {
    private final String flowName;
    private final List<Object> inputs;

    private ExecutionRequest(String flowName, List<Object> inputs) {
      this.flowName = flowName;
      this.inputs = inputs;
    }
  }

  private static class ExecutionContext {
    private final ExecutionRequest request;
    private final FlowModel flowModel;

    private ExecutionContext(ExecutionRequest request, FlowModel flowModel) {
      this.request = request;
      this.flowModel = flowModel;
    }
  }
}
