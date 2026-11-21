package com.ouroboros.businesslog.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;
import com.ouroboros.util.Json;

/** FreeMarker context mirrors BusinessLogExtractor, including its flattened HTTP field precedence. */
public final class BusinessLogCodeContextProvider implements CodeContextProvider {
  public Set<String> resourceTypes() { return Set.of("business-log-template"); }
  public String environmentLanguage(CodeContextRequest request) { return "spel"; }

  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    if (!"freemarker".equals(request.language()) || !"/summaryTemplate".equals(request.location().pointer())) {
      throw new BusinessException("业务日志代码位置需要 FreeMarker 摘要模板");
    }
    var draft = request.draft();
    var body = TypeShape.unknown("http-request-contract-unavailable");
    var response = TypeShape.unknown("http-response-contract-unavailable");
    var params = new LinkedHashMap<String, TypeShape>();
    var path = new LinkedHashMap<String, TypeShape>();
    var matcher = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(draft.path("url").asText());
    while (matcher.find()) path.put(matcher.group(1), TypeShape.scalar("string"));
    var matches = metadata.application().getModule("webapi").stream().map(model -> Json.toBean(JsonNode.class, Json.toJsonString(model.getDetails())))
        .filter(model -> draft.path("httpMethod").asText().equalsIgnoreCase(model.path("method").asText())
            && draft.path("url").asText().equals(model.path("path").asText())).toList();
    if (matches.size() == 1) {
      var api = matches.getFirst();
      body = jsonContent(api.path("requestBody"));
      for (var parameter : api.path("parameters")) {
        if ("query".equals(parameter.path("in").asText())) params.put(parameter.path("name").asText(),
            TypeShape.scalar("string").merge(TypeShape.array(TypeShape.scalar("string"))));
      }
      TypeShape allResponses = null;
      for (var item : api.path("responses")) {
        var shape = jsonContent(item);
        allResponses = allResponses == null ? shape : allResponses.merge(shape);
      }
      if (allResponses != null) response = allResponses;
    }
    var globals = new LinkedHashMap<>(response.property("data").properties());
    globals.putAll(body.properties()); globals.putAll(params); globals.putAll(path);
    var requestFields = new LinkedHashMap<String, TypeShape>();
    for (var field : List.of("method", "uri", "path", "protocol", "remoteAddress", "ipAddress")) requestFields.put(field, TypeShape.scalar("string"));
    requestFields.put("body", body); requestFields.put("data", body);
    requestFields.put("params", openMap(params, TypeShape.scalar("string").merge(TypeShape.array(TypeShape.scalar("string")))));
    requestFields.put("headers", openMap(Map.of(), TypeShape.scalar("string").merge(TypeShape.array(TypeShape.scalar("string")))));
    globals.put("Request", TypeShape.object(requestFields, "map"));
    globals.put("Response", TypeShape.object(Map.of("body", response, "data", response.property("data")), "map"));
    globals.put("IpAddress", TypeShape.scalar("string"));
    for (var name : List.of("Auth", "Employee", "Department", "Company", "Json", "Jsons")) {
      globals.put(name, TypeShape.unknown("runtime-object:" + name));
    }
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    globals.forEach((name, shape) -> {
      var binding = new ScriptIntelligenceGlobal(); binding.setName(name); binding.setKind("variable");
      binding.setShape(shape); binding.setSourceTag("business-log"); bindings.add(binding);
    });
    var diagnostics = matches.size() == 1 ? List.<Map<String, Object>>of() : List.<Map<String, Object>>of(
        Map.of("code", "unknown-http-contract", "message", "未找到唯一 WebAPI 合同；请求和响应的动态字段需由匹配的接口 Schema 提供"));
    return new CodeContextDescription("freemarker", bindings, TypeShape.scalar("string"), diagnostics, false,
        Map.of("templateMode", "freemarker"));
  }

  private TypeShape jsonContent(JsonNode message) {
    var schema = message.path("content").path("application/json").path("schema");
    return schema.isObject() && !schema.isEmpty() ? TypeShape.mapSchema(schema) : TypeShape.unknown("missing-http-json-schema");
  }
  private TypeShape openMap(Map<String, TypeShape> fields, TypeShape values) {
    var schema = (ObjectNode) TypeShape.object(fields, "map").schema(); schema.set("additionalProperties", values.schema());
    return new TypeShape(schema);
  }
}
