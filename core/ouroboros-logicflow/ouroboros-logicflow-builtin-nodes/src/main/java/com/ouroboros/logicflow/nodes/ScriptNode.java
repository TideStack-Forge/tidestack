package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.util.NodeUtil;
import com.ouroboros.logicflow.nodes.support.LogicFlowScripts;

public class ScriptNode extends AbstractNode<Object> {
  private final Function<Map<String, Object>, Try<Object>> scriptRunner;
  private final List<String> inputNames;

  public ScriptNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    var type = meta.getExtraProp(String.class, "language").orElse("groovy");
    var script = meta.getExtraProp(String.class, "script").orElse("");
    inputNames = getInputPorts().stream()
        .map(FlowNodePort::getName)
        .collect(Collectors.toList());
    scriptRunner = compileScript(type, script);
  }

  public Function<Map<String, Object>, Try<Object>> compileScript(String type, String script) {
    // 如果没有脚本，则直接返回输入
    if (script == null || script.trim().isEmpty()) {
      return context -> Try.success(context.get("$0"));
    }

    return LogicFlowScripts.compile(type, script);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    var context = NodeUtil.buildContextMap(inputs, inputNames);
    return scriptRunner.apply(context)
        .map(Collections::singletonList)
        .getOrElseThrow(e -> new FlowRuntimeException("脚本 [" + this.getName() + "] 执行失败", e, this, inputs));
  }

  public static class Builder implements NodeBuilder<ScriptNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "script".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ScriptNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ScriptNode(owner, metadata);
    }
  }
}
