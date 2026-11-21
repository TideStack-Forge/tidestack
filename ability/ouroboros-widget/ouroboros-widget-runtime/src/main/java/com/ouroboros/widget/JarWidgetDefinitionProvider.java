package com.ouroboros.widget;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

public class JarWidgetDefinitionProvider implements WidgetDefinitionProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarWidgetDefinitionProvider.class);
  private static final Map<String, WidgetDefinition> WIDGET_DEFINITIONS = new TreeMap<>(String::compareToIgnoreCase);

  static {
    var spec = TypedMetadataSpec.<WidgetDefinition>builder(WidgetDefinition.class)
        .typedSuffix(".widget.json")
        .jsonParser(WidgetDefinition.class)
        .keyExtractor(WidgetDefinition::getName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(JarWidgetDefinitionProvider::logDiagnostic);
    WIDGET_DEFINITIONS.putAll(result.values());
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("Widget 元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("Widget 元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  @Override
  public Optional<WidgetDefinition> getWidgetDefinition(String name) {
    return Optional.ofNullable(WIDGET_DEFINITIONS.get(name));
  }

  @Override
  public List<WidgetDefinition> getAllWidgetDefinitions() {
    return WIDGET_DEFINITIONS.values().stream().collect(Collectors.toList());
  }
}
