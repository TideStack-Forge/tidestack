package com.ouroboros.logicflow.nodes;

import static com.ouroboros.logicflow.util.NodeUtil.buildContextMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.logicflow.expression.LogicFlowExpression;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.logicflow.results.NamedOutPortValueSetter;
import com.ouroboros.logicflow.results.OutPortValueSetter;
import com.ouroboros.logicflow.util.LogicFlowValues;
import com.ouroboros.logicflow.nodes.support.LogicFlowMaps;

public class SwitchNode extends AbstractDecisionNode {
  private static final String SWITCH_EXCLUSIVE_GROUP_ID = "switch-branches";

  final private List<Tuple2<Condition, Predicate<Map<String, Object>>>> conditionFunctions;
  final private List<FlowNodePort> outputPorts;
  final private List<String> inputPortNames;

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected SwitchNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    var rawConditions = meta.getExtraProp(List.class, "conditions").orElseGet(Collections::emptyList);
    final var conditions = (List<Condition>) rawConditions.stream()
        // TODO: 错误处理
        .map(cond -> cond instanceof Map condMap ? LogicFlowMaps.tryToBean(Condition.class, condMap).getOrElse(() -> null) : null)
        .collect(Collectors.toList());
    this.conditionFunctions = compileConditions(conditions);
    this.outputPorts = buildOutgoingPorts(conditions);
    this.inputPortNames = getInputPorts().stream().map(FlowNodePort::getName).collect(Collectors.toList());
  }

  @Override
  public List<FlowNodePort> getOutputPorts() {
    return outputPorts;
  }

  /**
   * 编译所有条件表达式
   *
   * @param conditionList 表达式列表
   * @return 条件函数列表
   */
  private List<Tuple2<Condition, Predicate<Map<String, Object>>>> compileConditions(List<Condition> conditionList) {
    final List<Tuple2<Condition, Predicate<Map<String, Object>>>> conditions = new ArrayList<>();
    for (var condition : conditionList) {
      if (condition == null) {
        addError(new NodeDesignException("条件表达式错误", meta));
        conditions.add(Tuple.of(null, context -> false));
        continue;
      }
      var predicate = compileCondition(condition.getExpression());
      conditions.add(Tuple.of(condition, predicate));
    }
    // 增加默认条件
    conditions.add(Tuple.of(null, context -> true));
    return conditions;
  }

  /**
   * 编译条件表达式
   *
   * @param exprStr 表达式字符串
   * @return 条件函数
   */
  private Predicate<Map<String, Object>> compileCondition(String exprStr) {
    final String expr = LogicFlowExpression.wrapExpression(exprStr);
    return context -> {
      var result = LogicFlowExpression.eval(expr, context);
      if (result.isRight()) {
        if (result.get() instanceof Boolean bool) {
          return bool;
        }
        return LogicFlowValues.toBoolean(result.get());
      }
      if (result.getLeft() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new FlowRuntimeException("条件表达式执行失败", result.getLeft(), this, null);
    };
  }

  /**
   * 构造输出端口
   *
   * @param conditions 条件列表
   * @return 输出端口列表
   */
  private List<FlowNodePort> buildOutgoingPorts(List<Condition> conditions) {
    final var outgoingPorts = new ArrayList<FlowNodePort>();
    final var inputDataType = getInputPorts().get(0).getDataType();
    // 增加条件输出端口
    for (int i = 0; i < conditions.size(); i++) {
      var condition = conditions.get(i);
      var port = FlowNodePortMeta.builder()
          .name(condition == null ? "out" + i : condition.getName())
          .description(condition == null ? "" : condition.getDescription())
          .dataType(inputDataType)
          .exclusiveGroupId(SWITCH_EXCLUSIVE_GROUP_ID)
          .build();
      outgoingPorts.add(new DefaultFlowNodeOutPort(this, i, port));
    }
    // 增加默认输出端口
    var index = conditions.size();
    var defaultMeta = FlowNodePortMeta.builder()
        .name("default")
        .description("默认输出")
        .dataType(inputDataType)
        .exclusiveGroupId(SWITCH_EXCLUSIVE_GROUP_ID)
        .defaultBranch(true)
        .build();
    outgoingPorts.add(new DefaultFlowNodeOutPort(this, index, defaultMeta));
    return outgoingPorts;
  }

  protected Object getOutputValue(List<Object> output) {
    return output.get(0);
  }

  @Override
  protected void makeDecision(List<Object> inputs, OutPortValueSetter setPort, NamedOutPortValueSetter setNamedPort) {
    final var context = buildContextMap(inputs, inputPortNames);
    final var outputValue = getOutputValue(inputs);
    for (int i = 0; i < conditionFunctions.size(); i++) {
      final var conditionTuple = conditionFunctions.get(i);
      try {
        if (conditionTuple._2.test(context)) {
          setPort.accept(i, outputValue);
          return;
        }
      } catch (Exception e) {
        throw new FlowRuntimeException("条件表达式执行失败，" + conditionTuple._1.getExpression(), e, this, inputs);
      }
    }
  }

  public static class Condition {
    private String name;
    private String description;
    private String expression;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getExpression() {
      return expression;
    }

    public void setExpression(String expression) {
      this.expression = expression;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }

  public static class Builder implements NodeBuilder<SwitchNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "switch".equalsIgnoreCase(meta.getType());
    }

    @Override
    public SwitchNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new SwitchNode(owner, metadata);
    }
  }
}
