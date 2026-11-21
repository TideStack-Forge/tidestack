package com.ouroboros.logicflow.authoring.provider;
import java.util.*;
import java.util.stream.*;
import com.ouroboros.logicflow.authoring.model.NodeDefine;

/** Canonical precedence and ordering of design-time node definitions. */
public final class LogicFlowNodeCatalog {
  private LogicFlowNodeCatalog() { }
  public static List<NodeDefine> merge(Stream<LogicFlowNodeDefineProvider> providers) {
    Map<String, NodeDefine> nodeDefinesByType = new TreeMap<String, NodeDefine>(String.CASE_INSENSITIVE_ORDER);
    providers
        .map(LogicFlowNodeDefineProvider::getNodeDefines)
        .flatMap(Collection::stream)
        .filter(nodeDefine -> nodeDefine != null && nodeDefine.getType() != null && !nodeDefine.getType().isBlank())
        .forEach(nodeDefine -> nodeDefinesByType.put(nodeDefine.getType(), nodeDefine));
    return new ArrayList<NodeDefine>(nodeDefinesByType.values())
        .stream()
        .sorted(Comparator.comparingInt(NodeDefine::getOrder))
        .collect(Collectors.toList());
  }
}
