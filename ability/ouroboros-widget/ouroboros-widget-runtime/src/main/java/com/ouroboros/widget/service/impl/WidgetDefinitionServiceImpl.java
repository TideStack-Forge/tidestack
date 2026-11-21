package com.ouroboros.widget.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.util.SPI;
import com.ouroboros.widget.WidgetDefinition;
import com.ouroboros.widget.WidgetDefinitionProvider;
import com.ouroboros.widget.service.WidgetDefinitionService;

@Service
public class WidgetDefinitionServiceImpl implements WidgetDefinitionService {
  private final static List<WidgetDefinitionProvider> DEFAULT_WIDGET_DEFINITION_PROVIDERS = SPI.getSortedServiceStream(WidgetDefinitionProvider.class)
      .collect(Collectors.toList());

  private final List<WidgetDefinitionProvider> widgetDefinitionProviders;

  public WidgetDefinitionServiceImpl() {
    this(DEFAULT_WIDGET_DEFINITION_PROVIDERS);
  }

  WidgetDefinitionServiceImpl(List<WidgetDefinitionProvider> widgetDefinitionProviders) {
    this.widgetDefinitionProviders = widgetDefinitionProviders == null
        ? Collections.emptyList()
        : widgetDefinitionProviders.stream().filter(Objects::nonNull).sorted(SPI::sortByPriority).collect(Collectors.toList());
  }

  @Override
  public List<WidgetDefinition> getAllWidgetDefinitions() {
    return widgetDefinitionProviders.stream()
        .map(WidgetDefinitionProvider::getAllWidgetDefinitions)
        .flatMap(List::stream)
        .filter(definition -> definition != null && StringUtils.isNotBlank(definition.getName()))
        .collect(
            () -> new TreeMap<String, WidgetDefinition>(String::compareToIgnoreCase),
            (map, definition) -> map.put(definition.getName(), definition),
            TreeMap::putAll
        )
        .values()
        .stream()
        .collect(Collectors.toList());
  }
}
