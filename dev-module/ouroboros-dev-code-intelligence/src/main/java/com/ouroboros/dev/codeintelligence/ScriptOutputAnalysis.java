package com.ouroboros.dev.codeintelligence;

import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;

/** IO boundary: graph and node analyzers receive only immutable completed contracts. */
@FunctionalInterface
public interface ScriptOutputAnalysis {
  TypeShape infer(String app, ApplicationMetadataSnapshot application, ScriptAnalysisQuery query, CodeContextRequest request);
}
