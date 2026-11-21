package com.ouroboros.rpc.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.CodeContextDescription;
import com.ouroboros.dev.codeintelligence.CodeContextProvider;
import com.ouroboros.dev.codeintelligence.CodeContextRequest;
import com.ouroboros.dev.codeintelligence.ConfigurationKeyCatalog;
import com.ouroboros.dev.codeintelligence.ReadOnlyMetadataView;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;

/** Models the actual template phases, using the draft and the target application's catalog. */
public final class RpcCodeContextProvider implements CodeContextProvider {
  private final List<com.ouroboros.dev.codeintelligence.InvocationInputProvider> callers;
  public RpcCodeContextProvider() { this(List.of()); }
  public RpcCodeContextProvider(List<com.ouroboros.dev.codeintelligence.InvocationInputProvider> callers) { this.callers = List.copyOf(callers); }
  public Set<String> resourceTypes() { return Set.of("rpc-model", "rpc-auth"); }

  public String environmentLanguage(CodeContextRequest request) { return "spel"; }

  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var pointer = request.location().pointer();
    var type = request.draft().path("type").asText();
    var language = com.ouroboros.rpc.RpcFieldSemantics.language(request.resource().type(), type, pointer);
    if (language == com.ouroboros.rpc.RpcFieldSemantics.Language.NONE) throw invalid(pointer);
    boolean jsonPath = language == com.ouroboros.rpc.RpcFieldSemantics.Language.JSON_PATH;
    if (!(jsonPath ? "jsonpath" : "rpc-template").equals(request.language())) {
      throw new BusinessException("RPC字段语言与执行语义不符: " + pointer);
    }
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    var diagnostics = new ArrayList<Map<String, Object>>();
    var hints = new java.util.LinkedHashMap<String, Object>();
    hints.put("configurationKeys", ConfigurationKeyCatalog.describe(metadata.application()));
    hints.put("literals", literals(pointer, metadata));
    boolean mixed = language == com.ouroboros.rpc.RpcFieldSemantics.Language.TEMPLATE;
    hints.put("templateMode", mixed ? "mixed" : "config-only");
    if (mixed && !"HttpBasic".equalsIgnoreCase(type)) invocationBindings(request, metadata, bindings, diagnostics);
    if (mixed && "AccessToken".equalsIgnoreCase(type)) add(bindings, "accessToken", TypeShape.scalar("string"));
    if (jsonPath) {
      var modelName = request.draft().path("accessToken").path("request").path("modelName").asText();
      var models = metadata.application().findModuleMetadata("rpc-model", modelName);
      var shape = TypeShape.unknown("missing-token-response-contract");
      if (models.size() == 1) {
        var definition = com.ouroboros.util.Json.toBean(JsonNode.class, com.ouroboros.util.Json.toJsonString(models.getFirst().getDetails()));
        if (definition.path("outputSchema").isObject() && !definition.path("outputSchema").isEmpty()) {
          shape = TypeShape.mapSchema(definition.path("outputSchema"));
        }
      }
      if (shape.isUnknown()) diagnostics.add(Map.of("code", "unknown-token-response", "message", "请为令牌RPC声明输出结构；配置引用的模型名在应用运行时解析"));
      hints.put("rootShape", shape.schema());
    }
    return new CodeContextDescription(jsonPath ? "jsonpath" : "rpc-template", bindings,
        TypeShape.scalar("string"), diagnostics, mixed, hints);
  }

  private void invocationBindings(CodeContextRequest request, ReadOnlyMetadataView metadata, List<ScriptIntelligenceGlobal> bindings, List<Map<String, Object>> diagnostics) {
    var schema = request.draft().path("inputSchema");
    TypeShape input;
    if (!schema.isObject() || schema.isEmpty()) {
      input = inferredCalls(request, metadata);
      if (input.isUnknown()) {
        diagnostics.add(Map.of("code", "unknown-invocation-shape", "message", "未声明输入 Schema，且调用点没有可用合同；请求配置不是调用入参"));
        return;
      }
      diagnostics.add(Map.of("code", "inferred-invocation-shape", "message", "输入来自已知调用点的静态合同；其他动态调用可通过输入 Schema 声明"));
    } else input = TypeShape.mapSchema(schema);
    // RpcInvocationRequest variables are the input map itself, with no implicit request/$0 wrapper.
    input.properties().forEach((name, shape) -> add(bindings, name, shape));
  }

  private TypeShape inferredCalls(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var models = new java.util.LinkedHashSet<String>();
    if ("rpc-model".equals(request.resource().type())) models.add(request.resource().name());
    else for (var model : metadata.application().getModule("rpc-model")) {
      if (model.getDetails() != null && request.resource().name().equals(model.getDetails().get("authCredential"))) models.add(model.getName());
    }
    TypeShape result = null;
    for (var model : models) for (var caller : callers) {
      var shape = caller.infer("rpc-model", model, metadata);
      if (shape.isPresent()) result = result == null ? shape.get() : result.merge(shape.get());
    }
    return result == null ? TypeShape.unknown("no-static-invocation-input") : result;
  }

  private static void add(List<ScriptIntelligenceGlobal> bindings, String name, TypeShape shape) {
    var global = new ScriptIntelligenceGlobal();
    global.setName(name); global.setKind("variable"); global.setShape(shape); global.setTypeDisplayName(shape.type());
    global.setSourceTag("rpc-invocation"); bindings.add(global);
  }

  private static List<String> literals(String pointer, ReadOnlyMetadataView metadata) {
    if (pointer.equals("/request/method")) return List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    if (pointer.endsWith("/in")) return List.of("path", "header", "query", "body", "cookie");
    if (pointer.equals("/hmacSignature/preset")) return List.of("aliyun_acs3", "aliyun_rpc_v1", "aws_sigv4", "volcengine_hmac_sha256", "tencent_tc3", "huawei_sdk_hmac_sha256");
    if (pointer.endsWith("/payloadHashMode")) return List.of("auto", "empty_sha256", "unsigned_payload");
    if (pointer.endsWith("/signatureMethod")) return List.of("HMAC-SHA1", "HMAC-SHA256");
    if (pointer.equals("/accessToken/mode")) return List.of("dynamic", "static");
    if (pointer.equals("/accessToken/tokenPlacement")) return List.of("Authorization", "Custom");
    if (pointer.endsWith("/expiresValueSource")) return List.of("response", "input");
    if (pointer.endsWith("/unit")) return List.of("second", "millisecond");
    String module = pointer.equals("/authCredential") ? "rpc-auth"
        : pointer.equals("/accessToken/request/modelName") ? "rpc-model" : null;
    if (module == null) return List.of();
    return metadata.application().getModule(module).stream().map(com.ouroboros.dev.metadata.Metadata::getName).sorted().toList();
  }

  private static BusinessException invalid(String pointer) { return new BusinessException("RPC字段没有此模板求值语义: " + pointer); }
}
