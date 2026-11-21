package com.ouroboros.widget;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.util.SPI;

public class GeneratorWidgetDefinitionProvider implements WidgetDefinitionProvider {
  private final static List<WidgetSchemaProvider> WIDGET_SCHEMA_PROVIDERS = SPI.getSortedServiceStream(WidgetSchemaProvider.class)
      .collect(Collectors.toList());
  private final static List<WidgetDefinitionGenerator> WIDGET_DEFINITION_GENERATORS = SPI.getSortedServiceStream(WidgetDefinitionGenerator.class)
      .collect(Collectors.toList());
  private static final Map<String, WidgetDefinition> WIDGET_DEFINITIONS = new ConcurrentHashMap<>();
  @Override
  public Optional<WidgetDefinition> getWidgetDefinition(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(WIDGET_DEFINITIONS.get(name.toUpperCase()));
  }

  @Override
  public List<WidgetDefinition> getAllWidgetDefinitions() {
    return WIDGET_DEFINITIONS.values().stream()
        .collect(Collectors.toList());
  }

  static void refresh() {
    WIDGET_DEFINITIONS.clear();
    WIDGET_SCHEMA_PROVIDERS.stream()
        .map(WidgetSchemaProvider::getWidgetSchemas)
        .flatMap(List::stream)
        .map(GeneratorWidgetDefinitionProvider::buildWidgetDefinition)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(w -> StringUtils.isNotEmpty(w.getName()))
        .forEach(w -> WIDGET_DEFINITIONS.put(w.getName().toUpperCase(), w));
  }

  static private Optional<WidgetDefinition> buildWidgetDefinition(WidgetSchema metadata) {
    return WIDGET_DEFINITION_GENERATORS.stream()
        .filter(g -> StringUtils.equalsIgnoreCase(metadata.getGeneratorName(), g.getName()))
        .map(g -> g.buildWidgetDefinition(metadata))
        .findFirst();
  }
}
