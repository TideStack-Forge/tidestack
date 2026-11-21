package com.ouroboros.dev.codeintelligence;
import java.util.Set;
public interface CodeContextProvider {
  Set<String> resourceTypes();
  default String environmentLanguage(CodeContextRequest request) { return request.language(); }
  default java.util.List<ScriptAnalysisQuery> analysisQueries(CodeContextRequest request, ReadOnlyMetadataView metadata) { return java.util.List.of(); }
  CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata);
}
