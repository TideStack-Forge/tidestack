package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;

/** Resource-aware signatures compose through the same node SPI as structural builtins. */
public final class ReferencedNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("callrpc", "callflow", "dataModel", "insertData", "batchInsertData", "getOneData", "searchData", "pagedSearchData", "countData", "updateData", "partialUpdateData", "deleteData", "normalizeWhere"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var node = input.node();
    var type = String.valueOf(node.getType()).toLowerCase(Locale.ROOT);
    TypeShape result;
    switch (type) {
      case "callrpc" -> result = input.contracts().find("rpc-model", node.getExtraProp(String.class, "modelName").orElse(""), "output");
      case "callflow" -> {
        boolean each = node.getExtraProp(Boolean.class, "executeForEachItem").orElse(false);
        var arguments = each ? List.of(input.firstInput().element()) : input.inputs().stream().map(PortShape::shape).toList();
        result = input.contracts().callFlow(node.getExtraProp(String.class, "flowName").orElse(""), arguments, input.globals(), expressions);
        if (each) result = TypeShape.array(result);
      }
      case "datamodel" -> result = input.contracts().find("data-model", node.getExtraProp(String.class, "dataModel").orElse(""), "instance");
      case "countdata" -> result = TypeShape.scalar("integer");
      case "normalizewhere" -> result = TypeShape.unknown("normalized-query-structure");
      default -> {
        var record = input.contracts().find("data-model", node.getExtraProp(String.class, "dataModel").orElse(""), "record");
        result = switch (type) {
          case "insertdata" -> record;
          case "getonedata" -> record.merge(TypeShape.scalar("null"));
          case "searchdata", "batchinsertdata" -> TypeShape.array(record);
          case "pagedsearchdata" -> TypeShape.object(Map.of(node.getExtraProp(String.class, "itemsKey").orElse("items"), TypeShape.array(record),
              node.getExtraProp(String.class, "totalKey").orElse("total"), TypeShape.scalar("integer")), "map");
          case "deletedata" -> node.getExtraProp(Boolean.class, "returnDeletedData").orElse(false) ? record.merge(TypeShape.array(record)).merge(TypeShape.scalar("null")) : TypeShape.scalar("integer");
          default -> node.getExtraProp(Boolean.class, "returnUpdatedData").orElse(false) ? record.merge(TypeShape.array(record)).merge(TypeShape.scalar("null")) : TypeShape.scalar("integer");
        };
      }
    }
    var wrapper = node.getExtraProp(String.class, "resultWrapperKey").orElse("").trim();
    if (!wrapper.isEmpty() && !"pagedsearchdata".equals(type)) result = TypeShape.object(Map.of(wrapper, result), "map");
    var diagnostics = result.isUnknown() ? List.of(new AnalysisDiagnostic(node.getName(), "", "unknown-reference-shape", "Referenced resource has no usable type contract")) : List.<AnalysisDiagnostic>of();
    return new NodeAnalysis(Map.of(0, PortShape.always(result)), Map.of(), diagnostics);
  }
}
