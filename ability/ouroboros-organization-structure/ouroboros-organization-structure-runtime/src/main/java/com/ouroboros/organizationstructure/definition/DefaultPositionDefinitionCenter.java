package com.ouroboros.organizationstructure.definition;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.PositionDefinitionProvider;
import com.ouroboros.util.SPI;

public class DefaultPositionDefinitionCenter implements PositionDefinitionCenter {
  private final Map<String, PositionDefinition> definitionMap;

  public DefaultPositionDefinitionCenter() {
    this(SPI.getCachedServiceStream(PositionDefinitionProvider.class).collect(Collectors.toList()));
  }

  public DefaultPositionDefinitionCenter(List<PositionDefinitionProvider> providers) {
    this.definitionMap = Collections.unmodifiableMap(loadDefinitions(providers));
  }

  @Override
  public Optional<PositionDefinition> getDefinition(String key) {
    return Optional.ofNullable(definitionMap.get(key));
  }

  @Override
  public List<PositionDefinition> getDefinitions() {
    return definitionMap.values().stream().collect(Collectors.toList());
  }

  private static Map<String, PositionDefinition> loadDefinitions(List<PositionDefinitionProvider> providers) {
    Map<Integer, List<PositionDefinitionProvider>> tieredProviders = Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(PositionDefinitionProvider::getOrder))
        .collect(Collectors.groupingBy(PositionDefinitionProvider::getOrder, TreeMap::new, Collectors.toList()));
    Map<String, PositionDefinition> mergedDefinitions = new LinkedHashMap<>();
    tieredProviders.values().forEach(tierProviders -> mergeTierDefinitions(mergedDefinitions, tierProviders));
    return mergedDefinitions;
  }

  private static void mergeTierDefinitions(Map<String, PositionDefinition> mergedDefinitions,
                                           Collection<PositionDefinitionProvider> tierProviders) {
    Map<String, PositionDefinition> tierDefinitions = new LinkedHashMap<>();
    tierProviders.stream()
        .map(PositionDefinitionProvider::getDefinitionsOrEmpty)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(definition -> !isBlank(definition.getKey()))
        .filter(definition -> !isBlank(definition.getName()))
        .forEach(definition -> {
          PositionDefinition existing = tierDefinitions.putIfAbsent(definition.getKey(), definition);
          if (existing != null) {
            throw new IllegalStateException("Duplicate position definition in the same precedence tier: "
                + definition.getKey());
          }
        });
    tierDefinitions.forEach(mergedDefinitions::put);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
