package com.ouroboros.businesslog.provider;

import java.util.Collections;
import java.util.List;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateProvider;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

/**
 * @author liansz
 **/
@Priority(0)
public class JarBusinessOperationTemplateProvider implements BusinessOperationTemplateProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarBusinessOperationTemplateProvider.class);

  private static final String METADATA_FILE_SUFFIX = ".business-log.json";

  private final List<BusinessOperationTemplate> templates;

  public JarBusinessOperationTemplateProvider() {
    var spec = TypedMetadataSpec.builder(DefaultBusinessOperationTemplate.class)
        .typedSuffix(METADATA_FILE_SUFFIX)
        .jsonParser(DefaultBusinessOperationTemplate.class)
        .keyExtractor(DefaultBusinessOperationTemplate::getName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(JarBusinessOperationTemplateProvider::logDiagnostic);
    templates = result.values().values().stream()
        .map(template -> (BusinessOperationTemplate) template)
        .toList();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("业务日志模板元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("业务日志模板元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  @Override
  public BusinessOperationTemplateSource getSource() {
    return BusinessOperationTemplateSource.JAR;
  }

  @Override
  public List<BusinessOperationTemplate> getAllTemplates() {
    return Collections.unmodifiableList(templates);
  }
}
