package com.ouroboros.statistics.reporting.runtime.center;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.util.SPI;

public class DefaultDatasetDefinitionCenter implements DefinitionCenter<DatasetDefinition> {
  private final List<DatasetDefinitionProvider> providers;
  private final boolean enabledOnly;
  private volatile Map<String, DatasetDefinition> definitionMap;

  public DefaultDatasetDefinitionCenter() {
    this(SPI.getCachedServiceStream(DatasetDefinitionProvider.class).collect(Collectors.toList()), true);
  }

  public DefaultDatasetDefinitionCenter(List<DatasetDefinitionProvider> providers) {
    this(providers, true);
  }

  public DefaultDatasetDefinitionCenter(List<DatasetDefinitionProvider> providers, boolean enabledOnly) {
    this.enabledOnly = enabledOnly;
    this.providers = Collections.unmodifiableList(new ArrayList<DatasetDefinitionProvider>(
        Optional.ofNullable(providers).orElse(Collections.emptyList())));
    refresh();
  }

  public Optional<DatasetDefinition> getDefinition(String name) {
    return Optional.ofNullable(definitionMap.get(name));
  }

  public List<DatasetDefinition> getDefinitions() {
    return definitionMap.values().stream().collect(Collectors.toList());
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  public void onMetadataRefreshing() {
    refresh();
  }

  public synchronized void refresh() {
    definitionMap = Collections.unmodifiableMap(loadDefinitions(providers, enabledOnly));
  }

  private static Map<String, DatasetDefinition> loadDefinitions(List<DatasetDefinitionProvider> providers,
                                                                boolean enabledOnly) {
    Map<Integer, List<DatasetDefinitionProvider>> tieredProviders = Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(DatasetDefinitionProvider::getOrder))
        .collect(Collectors.groupingBy(
            DatasetDefinitionProvider::getOrder,
            TreeMap::new,
            Collectors.toList()
        ));

    Map<String, DatasetDefinition> mergedDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    tieredProviders.values().forEach(tierProviders -> mergeTierDefinitions(mergedDefinitions, tierProviders));
    if (enabledOnly) {
      mergedDefinitions.entrySet().removeIf(entry -> !Boolean.TRUE.equals(entry.getValue().getIsEnabled()));
    }
    return mergedDefinitions;
  }

  private static void mergeTierDefinitions(Map<String, DatasetDefinition> mergedDefinitions,
                                           Collection<DatasetDefinitionProvider> tierProviders) {
    Map<String, DatasetDefinition> tierDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    tierProviders.stream()
        .map(DatasetDefinitionProvider::getDefinitions)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(definition -> !isBlank(definition.getName()))
        .forEach(definition -> {
          com.ouroboros.statistics.reporting.validation.DatasetDefinitionValidator.validateName(definition.getName());
          DatasetDefinition existing = tierDefinitions.putIfAbsent(definition.getName(), definition);
          if (existing != null) {
            throw new PlatformException("Duplicate dataset definition in the same precedence tier: "
                + definition.getName());
          }
        });

    tierDefinitions.forEach(mergedDefinitions::put);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
