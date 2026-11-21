package com.ouroboros.logicflow.authoring.analysis;
import java.util.List;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.model.FlowNodeMeta;
public record NodeAnalysisInput(FlowNodeMeta node, List<PortShape> inputs, Map<String, TypeShape> globals, FlowTypeContracts contracts) {
  public NodeAnalysisInput(FlowNodeMeta node, List<PortShape> inputs, Map<String, TypeShape> globals) { this(node, inputs, globals, FlowTypeContracts.empty()); }
  public NodeAnalysisInput { node = node.clone(); inputs = List.copyOf(inputs); globals = Map.copyOf(globals); }
  public TypeShape firstInput() { return inputs.isEmpty() ? TypeShape.unknown("no-input") : inputs.get(0).shape(); }
}
