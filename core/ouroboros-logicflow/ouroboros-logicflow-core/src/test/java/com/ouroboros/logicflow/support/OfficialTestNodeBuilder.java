package com.ouroboros.logicflow.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.DefaultFlowNodeOutPort;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.AbstractTerminalNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.logicflow.results.Decision;

public class OfficialTestNodeBuilder implements NodeBuilder<FlowNode> {
  private static final Set<String> SUPPORTED_TYPES = Set.of(
      "callflow",
      "concat",
      "const",
      "distinct",
      "filter",
      "intersect",
      "mapping",
      "merge",
      "mutex",
      "omit",
      "pick",
      "return",
      "script",
      "subtract",
      "switch",
      "take",
      "validate"
  );

  @Override
  public Boolean support(FlowNodeMeta meta) {
    return meta != null && SUPPORTED_TYPES.contains(normalizeType(meta.getType()));
  }

  @Override
  public FlowNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
    return switch (normalizeType(metadata.getType())) {
      case "const" -> new SourceTestNode(owner, metadata);
      case "return" -> new TerminalTestNode(owner, metadata);
      case "switch" -> new SwitchTestNode(owner, metadata);
      case "intersect", "subtract" -> new GenericTestNode(owner, metadata, 2, 2);
      case "omit", "pick", "take" -> new GenericTestNode(owner, metadata, 1, 2);
      default -> new GenericTestNode(owner, metadata, 1, Integer.MAX_VALUE);
    };
  }

  private static String normalizeType(String type) {
    return type == null ? "" : type.toLowerCase(Locale.ROOT);
  }

  private static class GenericTestNode extends AbstractNode<Object> {
    private final int minInputPortCount;
    private final int maxInputPortCount;

    private GenericTestNode(FlowModel flowModel, FlowNodeMeta meta, int minInputPortCount, int maxInputPortCount) {
      super(flowModel, meta);
      this.minInputPortCount = minInputPortCount;
      this.maxInputPortCount = maxInputPortCount;
    }

    @Override
    protected Integer minInputPortCount() {
      return minInputPortCount;
    }

    @Override
    protected Integer maxInputPortCount() {
      return maxInputPortCount;
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      return Collections.singletonList(inputs.isEmpty() ? null : inputs.get(0));
    }
  }

  private static class SourceTestNode extends AbstractNode<Object> {
    private SourceTestNode(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
    }

    @Override
    protected Integer minInputPortCount() {
      return 0;
    }

    @Override
    public List<FlowNodePort> getOutputPorts() {
      if (!meta.getOutputPorts().isEmpty()) {
        return super.getOutputPorts();
      }
      var dataType = DataTypeEnum.fromValue(meta.getExtraProp(String.class, "dataType").orElse("any"));
      return Collections.singletonList(new DefaultFlowNodeOutPort(this, dataType));
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      return Collections.singletonList(meta.getExtraProp("value").orElse(null));
    }
  }

  private static class TerminalTestNode extends AbstractTerminalNode {
    private TerminalTestNode(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
    }

    @Override
    protected Integer minInputPortCount() {
      return 1;
    }

    @Override
    protected Integer maxInputPortCount() {
      return 1;
    }

    @Override
    protected Integer minOutputPortCount() {
      return 0;
    }

    @Override
    protected Integer maxOutputPortCount() {
      return 0;
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      return Collections.singletonList(inputs.isEmpty() ? null : inputs.get(0));
    }
  }

  private static class SwitchTestNode extends AbstractNode<Decision> {
    private static final String SWITCH_EXCLUSIVE_GROUP_ID = "switch-branches";
    private final List<FlowNodePort> outputPorts;

    private SwitchTestNode(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
      this.outputPorts = buildOutputPorts();
    }

    @Override
    public List<FlowNodePort> getOutputPorts() {
      return outputPorts;
    }

    @Override
    protected List<Decision> doExecute(List<Object> inputs) {
      var outputs = new ArrayList<Decision>(outputPorts.size());
      for (int index = 0; index < outputPorts.size(); index++) {
        outputs.add(index == outputPorts.size() - 1
            ? Decision.satisfied(inputs.isEmpty() ? null : inputs.get(0))
            : Decision.unsatisfied());
      }
      return outputs;
    }

    @Override
    public Boolean supportsSelectiveOutputMetadata() {
      return true;
    }

    private List<FlowNodePort> buildOutputPorts() {
      if (!meta.getOutputPorts().isEmpty()) {
        return super.getOutputPorts();
      }
      List<FlowNodePort> ports = new ArrayList<>();
      List<?> conditions = meta.getExtraProp(List.class, "conditions").orElseGet(Collections::emptyList);
      for (int index = 0; index < conditions.size(); index++) {
        ports.add(new DefaultFlowNodeOutPort(this, index, buildConditionPortMeta(index, conditions.get(index), false)));
      }
      ports.add(new DefaultFlowNodeOutPort(this, conditions.size(),
          FlowNodePortMeta.builder()
              .name("default")
              .description("default")
              .dataType(inputDataType())
              .exclusiveGroupId(SWITCH_EXCLUSIVE_GROUP_ID)
              .defaultBranch(true)
              .build()));
      return ports;
    }

    private FlowNodePortMeta buildConditionPortMeta(int index, Object condition, boolean defaultBranch) {
      String name = "out" + index;
      String description = "";
      if (condition instanceof Map<?, ?> conditionMap) {
        Object configuredName = conditionMap.get("name");
        Object configuredDescription = conditionMap.get("description");
        name = configuredName == null ? name : configuredName.toString();
        description = configuredDescription == null ? description : configuredDescription.toString();
      }
      return FlowNodePortMeta.builder()
          .name(name)
          .description(description)
          .dataType(inputDataType())
          .exclusiveGroupId(SWITCH_EXCLUSIVE_GROUP_ID)
          .defaultBranch(defaultBranch)
          .build();
    }

    private DataTypeEnum inputDataType() {
      return getInputPorts().isEmpty() ? DataTypeEnum.ANY : getInputPorts().get(0).getDataType();
    }
  }
}
