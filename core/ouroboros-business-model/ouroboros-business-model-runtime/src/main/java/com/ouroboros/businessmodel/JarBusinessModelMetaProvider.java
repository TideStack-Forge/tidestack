package com.ouroboros.businessmodel;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

@Priority(0)
public class JarBusinessModelMetaProvider implements BusinessModelMetaProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarBusinessModelMetaProvider.class);
  private static final String MODEL_FILE_SUFFIX = ".business.json";

  private final Map<String, BusinessModelMeta> businessModelMetas;

  public JarBusinessModelMetaProvider() {
    var spec = TypedMetadataSpec.<BusinessModelMeta>builder(BusinessModelMeta.class)
        .typedSuffix(MODEL_FILE_SUFFIX)
        .jsonParser(BusinessModelMeta.class)
        .jsonParser(DefaultBusinessModelMeta.class)
        .keyExtractor(BusinessModelMeta::getFullName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(JarBusinessModelMetaProvider::logDiagnostic);
    businessModelMetas = result.values();
  }

  @Override
  public Optional<BusinessModelMeta> getBusinessModelMeta(String fullName) {
    return Optional.ofNullable(businessModelMetas.get(fullName));
  }

  @Override
  public Map<String, BusinessModelMeta> getBusinessModelMetaMap() {
    return Collections.unmodifiableMap(businessModelMetas);
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("业务模型元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("业务模型元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
