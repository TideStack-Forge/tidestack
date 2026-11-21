package com.ouroboros.dev.codeintelligence.service;

import com.ouroboros.dev.codeintelligence.CodeContextProvider;
import com.ouroboros.dev.codeintelligence.CodeContextRequest;
import com.ouroboros.dev.codeintelligence.CodeContextResponse;
import com.ouroboros.dev.codeintelligence.CodeSyntaxDiagnostics;
import com.ouroboros.dev.codeintelligence.ConfigurationKeyCatalog;
import com.ouroboros.dev.codeintelligence.ReadOnlyMetadataView;
import com.ouroboros.dev.codeintelligence.ScriptOutputAnalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.script.intelligence.CodeCandidateOrigin;
import com.ouroboros.util.Json;

public final class CodeContextService {
  private final CodeSyntaxDiagnostics syntax = new CodeSyntaxDiagnostics();
  private final ScriptOutputAnalysis scripts;
  private final Map<String, CodeContextProvider> providers;
  private final List<ApplicationMetadataSnapshotProvider> snapshots;
  private final java.util.function.Function<String, ScriptIntelligenceSnapshot> references;

  public CodeContextService(List<CodeContextProvider> providers, List<ApplicationMetadataSnapshotProvider> snapshots) {
    this(providers, snapshots, language -> null);
  }

  public CodeContextService(List<CodeContextProvider> providers, List<ApplicationMetadataSnapshotProvider> snapshots,
      java.util.function.Function<String, ScriptIntelligenceSnapshot> references) {
    this(providers, snapshots, references, (app, environment, query, request) -> com.ouroboros.codeintelligence.type.TypeShape.unknown("language-service-unavailable"));
  }

  public CodeContextService(List<CodeContextProvider> providers, List<ApplicationMetadataSnapshotProvider> snapshots,
      java.util.function.Function<String, ScriptIntelligenceSnapshot> references, ScriptOutputAnalysis scripts) {
    this.scripts = scripts;
    var index = new LinkedHashMap<String, CodeContextProvider>();
    for (var provider : providers) for (var type : provider.resourceTypes()) {
      if (index.putIfAbsent(type, provider) != null) throw new IllegalArgumentException("Duplicate code context resource: " + type);
    }
    this.providers = Map.copyOf(index);
    this.snapshots = List.copyOf(snapshots);
    this.references = references;
  }

  public CodeContextResponse describe(String appName, CodeContextRequest request) {
    validate(appName, request);
    var provider = providers.get(request.resource().type());
    if (provider == null) throw new BusinessException("此资源类型尚不支持代码上下文: " + request.resource().type());
    var application = applicationSnapshot(appName);
    if (application.hasInvalidModuleMetadata("script-intelligence")) throw new BusinessException("应用代码提示目录包含无效或重复元数据");
    var environment = environment(application, provider.environmentLanguage(request));
    var outputs = new LinkedHashMap<String, com.ouroboros.codeintelligence.type.TypeShape>();
    if (application.getStatus() == MetadataSnapshotStatus.CURRENT) {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
      for (int wave = 0; wave < 32 && outputs.size() < 128 && System.nanoTime() < deadline; wave++) {
        var pending = provider.analysisQueries(request, new ReadOnlyMetadataView(appName, application, environment, outputs)).stream()
            .filter(query -> !outputs.containsKey(query.key())).toList();
        if (pending.isEmpty()) break;
        for (var query : pending) {
          if (outputs.size() >= 128 || System.nanoTime() >= deadline) break;
          outputs.put(query.key(), scripts.infer(appName, application, query, request));
        }
      }
    }
    var description = provider.describe(request, new ReadOnlyMetadataView(appName, application, environment, outputs));
    var snapshot = new ScriptIntelligenceSnapshot();
    snapshot.setRuntimeProfile(description.profile());
    snapshot.setSource(environment.getSource());
    var bindings = new LinkedHashMap<String, com.ouroboros.script.intelligence.ScriptIntelligenceGlobal>();
    description.bindings().forEach(binding -> {
      var local = Json.toBean(com.ouroboros.script.intelligence.ScriptIntelligenceGlobal.class, Json.toJsonString(binding));
      local.setOrigin(CodeCandidateOrigin.DRAFT_INFERENCE);
      bindings.put(local.getName(), local);
    });
    if (description.allowGlobals()) environment.getGlobals().forEach(binding -> bindings.put(binding.getName(), binding));
    snapshot.setGlobals(List.copyOf(bindings.values()));
    if (description.allowGlobals()) {
      snapshot.setJavaImports(environment.getJavaImports());
      snapshot.setJavaTypes(environment.getJavaTypes());
      snapshot.setLanguageProjectionHints(environment.getLanguageProjectionHints());
    }
    var diagnostics = new java.util.ArrayList<>(description.diagnostics());
    diagnostics.addAll(syntax.diagnose(request, description));
    snapshot.setDiagnostics(diagnostics);
    var hints = new LinkedHashMap<String, Object>(snapshot.getLanguageProjectionHints());
    if (snapshot.getGlobals().stream().anyMatch(binding -> "Config".equals(binding.getName()))) {
      hints.put("configurationKeys", ConfigurationKeyCatalog.describe(application));
    }
    hints.putAll(description.projectionHints());
    if (syntax.supports(description.profile())) hints.put("syntaxDiagnostics", true);
    snapshot.setLanguageProjectionHints(hints);
    var material = new LinkedHashMap<String, Object>();
    material.put("appName", appName);
    material.put("resource", request.resource());
    material.put("site", request.codeSite());
    material.put("location", request.location());
    material.put("snapshot", snapshot);
    material.put("expectedResult", description.expectedResult());
    material.put("runtimeIdentity", application.getRuntimeIdentity());
    var version = digest(Json.toJsonString(material));
    snapshot.setVersionToken(version);
    return new CodeContextResponse(request.documentId(), request.documentVersion(), request.draftRevision(), version,
        application.getStatus().name(), application.getRuntimeIdentity(), snapshot, description.expectedResult());
  }

  private ApplicationMetadataSnapshot applicationSnapshot(String appName) {
    var available = snapshots.stream().map(source -> source.getApplicationMetadataSnapshot(appName))
        .filter(snapshot -> snapshot.getStatus() != MetadataSnapshotStatus.UNAVAILABLE).toList();
    if (available.size() > 1) throw new BusinessException("应用存在多个代码提示快照来源");
    return available.isEmpty() ? ApplicationMetadataSnapshot.unavailable() : available.get(0);
  }

  private ScriptIntelligenceSnapshot environment(ApplicationMetadataSnapshot application, String language) {
    var matches = application.findModuleMetadata("script-intelligence", language);
    if (matches.size() > 1) throw new BusinessException("重复的语言环境目录: " + language);
    if (matches.isEmpty()) {
      var reference = references.apply(language);
      if (reference != null) {
        reference.setSource("dev-platform-fallback");
        markEnvironment(reference, CodeCandidateOrigin.PLATFORM_REFERENCE);
        return reference;
      }
      var empty = new ScriptIntelligenceSnapshot();
      empty.setRuntimeProfile(language);
      empty.setSource("unavailable");
      return empty;
    }
    var metadata = matches.get(0);
    if (metadata.getDetails() == null) throw new BusinessException("语言环境目录缺少内容: " + language);
    var snapshot = Json.toBean(ScriptIntelligenceSnapshot.class, Json.toJsonString(metadata.getDetails()));
    snapshot.setSource("dev-helper-cache");
    markEnvironment(snapshot, CodeCandidateOrigin.APPLICATION_SNAPSHOT);
    return snapshot;
  }

  private static void markEnvironment(ScriptIntelligenceSnapshot snapshot, CodeCandidateOrigin origin) {
    snapshot.getGlobals().forEach(binding -> binding.setOrigin(origin));
    snapshot.getJavaTypes().forEach(type -> type.setOrigin(origin));
  }

  private static void validate(String appName, CodeContextRequest request) {
    if (appName == null || appName.isBlank() || request == null || request.resource() == null || request.location() == null
        || request.draft() == null || !request.draft().isObject() || request.codeSite() == null || request.language() == null
        || request.documentId() == null || request.documentId().isBlank() || request.location().pointer() == null
        || request.codeSite().isBlank() || request.language().isBlank() || request.resource().type() == null
        || request.resource().type().isBlank() || request.documentVersion() < 0 || request.draftRevision() < 0
        || !request.location().pointer().startsWith("/")) {
      throw new BusinessException("代码上下文请求缺少应用、资源、位置、语言或草稿");
    }
    if (request.draft().toString().length() > 4_000_000) throw new BusinessException("代码上下文草稿超出大小预算");
  }

  private static String digest(String material) {
    return com.ouroboros.codeintelligence.runtime.ContentVersion.of(material);
  }
}
