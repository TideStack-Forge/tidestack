package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;

/** Static contracts for the extra nodes, without loading their runtime implementations or files. */
public final class ExtraNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("readExcel", "ouroborosExceptions"); }

  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    if ("ouroborosExceptions".equalsIgnoreCase(input.node().getType())) return new NodeAnalysis(Map.of(), Map.of(), List.of());
    var node = input.node();
    var date = TypeShape.object(Map.of(), "java").annotated("x-javaType", "java.util.Date");
    var cell = TypeShape.scalar("string").merge(TypeShape.scalar("number")).merge(TypeShape.scalar("boolean")).merge(date);
    if (node.getExtraProp("nullValueReplacement").isEmpty()) cell = cell.merge(TypeShape.scalar("null"));
    TypeShape sheet;
    switch (node.getExtraProp(String.class, "sheetDataFormat").orElse("ListOfMap")) {
      case "ListOfList" -> sheet = TypeShape.array(TypeShape.array(cell));
      case "SheetData" -> sheet = TypeShape.object(Map.of("sheetName", TypeShape.scalar("string"), "rows", TypeShape.array(TypeShape.array(cell)),
          "success", TypeShape.scalar("boolean"), "errorMessage", TypeShape.scalar("string").merge(TypeShape.scalar("null"))), "java")
          .annotated("x-javaType", "com.ouroboros.util.excel.ExcelReader$SheetData");
      default -> {
        var headers = new LinkedHashMap<String, TypeShape>();
        var declared = node.getExtraProp(List.class, "customHeaders").orElse(List.of());
        if (node.getExtraProp(Boolean.class, "useCustomHeaders").orElse(false)) {
          for (var header : declared) if (header != null && !header.toString().isBlank()) headers.put(header.toString(), cell);
        }
        var row = (ObjectNode) TypeShape.object(headers, "map").schema();
        if (headers.isEmpty()) row.set("additionalProperties", cell.schema());
        if (node.getExtraProp(Boolean.class, "skipEmptyColumns").orElse(false)) row.putArray("required");
        sheet = TypeShape.array(new TypeShape(row));
      }
    }
    TypeShape result = sheet;
    if (!"Single".equalsIgnoreCase(node.getExtraProp(String.class, "sheetSelectionMode").orElse("Single"))) {
      if ("Map".equals(node.getExtraProp(String.class, "multiSheetResultFormat").orElse("List"))) {
        var map = (ObjectNode) TypeShape.object(Map.of(), "map").schema();
        map.set("additionalProperties", sheet.schema()); result = new TypeShape(map);
      } else result = TypeShape.array(sheet);
    }
    if ("Default".equals(node.getExtraProp(String.class, "errorHandlingStrategy").orElse("Throw"))) {
      result = result.merge(TypeShape.literal(node.getExtraProp("defaultValueOnError").orElse(null)));
    } else if (!"array".equals(result.type()) && "Skip".equals(node.getExtraProp(String.class, "errorHandlingStrategy").orElse("Throw"))) {
      result = result.merge(TypeShape.array(TypeShape.unknown("empty-error-result")));
    }
    return NodeAnalysis.output(result);
  }
}
