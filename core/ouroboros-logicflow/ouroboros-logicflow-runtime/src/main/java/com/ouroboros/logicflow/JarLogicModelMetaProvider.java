package com.ouroboros.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

@Priority(0)
public class JarLogicModelMetaProvider implements FlowModelMetaProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarLogicModelMetaProvider.class);
  private static final String MODEL_FILE_SUFFIX = ".logicflow.json";

  private final Map<String, FlowModelMeta> flowModelMetas;
  private final java.util.List<String> sourceDiagnostics;

  public JarLogicModelMetaProvider() {
    var spec = TypedMetadataSpec.<FlowModelMeta>builder(FlowModelMeta.class)
        .typedSuffix(MODEL_FILE_SUFFIX)
        .jsonParser(FlowModelMeta.class)
        .keyExtractor(FlowModelMeta::getName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(JarLogicModelMetaProvider::logDiagnostic);
    flowModelMetas = result.values();
    sourceDiagnostics = result.diagnostics().stream().map(diagnostic -> diagnostic.resourceName() + ": " + diagnostic.message()).toList();
  }

  @Override
  public Optional<FlowModelMeta> getFlowModelMeta(String flowName) {
    return Optional.ofNullable(flowModelMetas.get(flowName));
  }

  @Override
  public Map<String, FlowModelMeta> getFlowModelMetas() {
    return Collections.unmodifiableMap(flowModelMetas);
  }

  @Override
  public com.ouroboros.logicflow.model.FlowModelSource observeSource() {
    return com.ouroboros.logicflow.model.FlowModelSource.capture("jar", 0, false,
        sourceDiagnostics.isEmpty(), sourceDiagnostics, flowModelMetas);
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("逻辑流元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("逻辑流元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
