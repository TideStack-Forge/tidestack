package com.ouroboros.errorcode.runtime.registry;

import java.util.ArrayList;
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

import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.provider.ErrorCodeDefinitionProvider;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.util.SPI;

public class DefaultErrorCodeRegistry implements ErrorCodeRegistry {
  private final Map<Integer, ErrorCodeDefinition> definitionMap;
  private final List<ErrorCodeDefinition> outwardDefinitions;

  public DefaultErrorCodeRegistry() {
    this(SPI.getCachedServiceStream(ErrorCodeDefinitionProvider.class).collect(Collectors.toList()));
  }

  public DefaultErrorCodeRegistry(List<ErrorCodeDefinitionProvider> providers) {
    this.definitionMap = Collections.unmodifiableMap(loadDefinitions(providers));
    this.outwardDefinitions = Collections.unmodifiableList(definitionMap.values().stream()
        .filter(ErrorCodeDefinition::isEnabled)
        .collect(Collectors.toList()));
  }

  @Override
  public Optional<ErrorCodeDefinition> getDefinition(Integer code) {
    return Optional.ofNullable(definitionMap.get(code))
        .filter(ErrorCodeDefinition::isEnabled);
  }

  @Override
  public List<ErrorCodeDefinition> getAllDefinitions() {
    return outwardDefinitions;
  }

  private static Map<Integer, ErrorCodeDefinition> loadDefinitions(List<ErrorCodeDefinitionProvider> providers) {
    var tieredProviders = Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(ErrorCodeDefinitionProvider::getOrder))
        .collect(Collectors.groupingBy(
            ErrorCodeDefinitionProvider::getOrder,
            TreeMap::new,
            Collectors.toList()
        ));

    Map<Integer, ErrorCodeDefinition> mergedDefinitions = new TreeMap<>();
    tieredProviders.values().forEach(tierProviders -> mergeTierDefinitions(mergedDefinitions, tierProviders));
    return mergedDefinitions;
  }

  private static void mergeTierDefinitions(Map<Integer, ErrorCodeDefinition> mergedDefinitions,
                                           Collection<ErrorCodeDefinitionProvider> tierProviders) {
    Map<Integer, ErrorCodeDefinition> tierDefinitions = new LinkedHashMap<>();
    tierProviders.stream()
        .map(ErrorCodeDefinitionProvider::getDefinitions)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(definition -> definition.getCode() != null)
        .forEach(definition -> tierDefinitions.put(definition.getCode(), definition));

    tierDefinitions.forEach(mergedDefinitions::putIfAbsent);
  }
}
