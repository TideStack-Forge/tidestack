package com.ouroboros.notification.template.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;

/**
 * @author liansz
 */
@Priority(0)
public class JarNotificationTemplateProvider implements NotificationTemplateProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarNotificationTemplateProvider.class);

  private static final String METADATA_FILE_SUFFIX = ".notification.json";

  private final Map<String, NotificationTemplate> templateMap;

  public JarNotificationTemplateProvider() {
    var spec = TypedMetadataSpec.<NotificationTemplate>builder(NotificationTemplate.class)
        .typedSuffix(METADATA_FILE_SUFFIX)
        .jsonParser(DefaultNotificationTemplate.class)
        .keyExtractor(NotificationTemplate::getName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(JarNotificationTemplateProvider::logDiagnostic);
    templateMap = result.values();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("通知模板元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("通知模板元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  @Override
  public Optional<NotificationTemplate> getTemplate(String name) {
    return Optional.ofNullable(templateMap.get(name));
  }

  @Override
  public List<NotificationTemplate> getAllTemplate() {
    return new ArrayList<>(templateMap.values());
  }
}
