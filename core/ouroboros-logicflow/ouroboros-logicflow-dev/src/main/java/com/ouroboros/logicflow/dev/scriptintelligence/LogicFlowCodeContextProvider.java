package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.expression.intelligence.SpelTypeQuery;
import com.ouroboros.logicflow.authoring.analysis.LogicFlowDataflowAnalyzer;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

public final class LogicFlowCodeContextProvider implements CodeContextProvider {
  private final LogicFlowDataflowAnalyzer analyzer;
  private final SpelTypeQuery expressions;
  public LogicFlowCodeContextProvider() { analyzer = new LogicFlowDataflowAnalyzer(); expressions = new SpelTypeQuery(); }
  public Set<String> resourceTypes() { return Set.of("logicflow"); }

  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var flow = Json.toBean(FlowModelMeta.class, request.draft().toString());
    var node = flow.getNodes().stream().filter(candidate -> java.util.Objects.equals(candidate.getName(), request.location().nodeName())).findFirst()
        .orElseThrow(() -> new BusinessException("代码上下文节点不存在"));
    var definition = SPI.getCachedSortedServiceStream(LogicFlowNodeDefineProvider.class).flatMap(provider -> provider.getNodeDefines().stream())
        .filter(candidate -> node.getType().equalsIgnoreCase(candidate.getType())).findFirst().orElseThrow(() -> new BusinessException("未声明节点定义"));
    if (!declared(Json.toJsonString(definition.getProperties()), request.codeSite(), request.location().pointer())) throw new BusinessException("代码位置与节点字段声明不匹配");
    return describeFlow(flow, node.getName(), request.location().pointer(), request.language(), entryInputs(flow), metadata.environment(),
        new MetadataFlowTypeContracts(metadata.application(), Map.of(), metadata.scriptOutputs()));
  }

  public List<ScriptAnalysisQuery> analysisQueries(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var flow = Json.toBean(FlowModelMeta.class, request.draft().toString());
    var globals = new LinkedHashMap<String, TypeShape>();
    metadata.environment().getGlobals().forEach(binding -> { if (binding.getShape() != null) globals.put(binding.getName(), binding.getShape()); });
    var queries = new ArrayList<ScriptAnalysisQuery>();
    analyzer.analyze(flow, entryInputs(flow), globals, expressions,
        new MetadataFlowTypeContracts(metadata.application(), Map.of(), metadata.scriptOutputs(), queries::add));
    return List.copyOf(queries);
  }

  public CodeContextDescription describeFlow(FlowModelMeta flow, String nodeName, String pointer, String language,
      List<TypeShape> inputs, com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot environment,
      com.ouroboros.logicflow.authoring.analysis.FlowTypeContracts contracts) {
    var globals = new LinkedHashMap<String, TypeShape>();
    environment.getGlobals().forEach(binding -> globals.put(binding.getName(), binding.getShape() == null
        ? TypeShape.unknown("legacy-runtime-shape") : binding.getShape()));
    var analysis = analyzer.analyze(flow, inputs, globals, expressions, contracts);
    var nodeAnalysis = analysis.nodes().get(nodeName);
    if (nodeAnalysis == null) throw new BusinessException("代码上下文节点不存在: " + nodeName);
    var scope = nodeAnalysis.codeScopes().get(pointer);
    if (scope == null && pointer.equals("/mapping")) {
      scope = nodeAnalysis.codeScopes().entrySet().stream().filter(entry -> entry.getKey().startsWith("/mapping/"))
          .map(Map.Entry::getValue).findFirst().orElse(null);
    }
    if (scope == null) throw new BusinessException("此节点代码字段没有分析结果: " + pointer);
    if (!scope.profile().equalsIgnoreCase(language)) throw new BusinessException("代码语言与执行字段不匹配");
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    scope.bindings().forEach((name, shape) -> {
      if (!globals.containsKey(name)) {
        var binding = new ScriptIntelligenceGlobal();
        binding.setName(name);
        binding.setKind("variable");
        binding.setShape(shape);
        binding.setTypeDisplayName(shape.type());
        binding.setSourceTag("logicflow-input");
        bindings.add(binding);
      }
    });
    var diagnostics = analysis.diagnostics().stream().map(issue -> Map.<String, Object>of(
        "nodeName", String.valueOf(issue.nodeName()), "pointer", issue.pointer(), "code", issue.code(), "message", issue.message())).toList();
    return new CodeContextDescription(scope.profile(), bindings, scope.expectedResult(), diagnostics, true,
        Map.of("embeddedExpressionMode", "bare", "embeddedArrayValues", false));
  }

  public static List<TypeShape> entryInputs(FlowModelMeta flow) {
    if (!flow.getInputSchema().isEmpty()) return List.of(TypeShape.mapSchema(Json.toBean(JsonNode.class, Json.toJsonString(flow.getInputSchema()))));
    var type = flow.getInputDataType();
    return Set.of("string", "boolean", "number", "integer", "array", "object").contains(type)
        ? List.of(TypeShape.scalar(type)) : List.of();
  }

  private boolean declared(String properties, String site, String pointer) {
    return matches(Json.toBean(JsonNode.class, properties), site, pointer);
  }

  private boolean matches(JsonNode item, String site, String pointer) {
    if (item.isObject() && site.equals(item.path("codeSite").asText())) {
      var pattern = item.path("codePointer").asText("/" + item.path("name").asText());
      if (pattern.endsWith("/**") && pointer.startsWith(pattern.substring(0, pattern.length() - 2))) return true;
      var expected = pattern.split("/"); var actual = pointer.split("/");
      if (expected.length == actual.length) {
        boolean equal = true;
        for (int i = 0; i < expected.length; i++) if (!expected[i].equals("*") && !expected[i].equals(actual[i])) equal = false;
        if (equal) return true;
      }
    }
    for (var child : item) if (matches(child, site, pointer)) return true;
    return false;
  }
}
