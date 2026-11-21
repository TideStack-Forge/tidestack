package com.ouroboros.logicflow.authoring.analysis;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
public interface FlowNodeAnalyzer {
  Set<String> nodeTypes();
  NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions);
}
