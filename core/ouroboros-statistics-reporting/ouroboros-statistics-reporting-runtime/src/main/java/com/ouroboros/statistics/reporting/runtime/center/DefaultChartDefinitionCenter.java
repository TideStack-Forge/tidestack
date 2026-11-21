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
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;
import com.ouroboros.util.SPI;

public class DefaultChartDefinitionCenter implements DefinitionCenter<ChartDefinition> {
  private final List<ChartDefinitionProvider> providers;
  private final boolean enabledOnly;
  private volatile Map<String, ChartDefinition> definitionMap;

  public DefaultChartDefinitionCenter() {
    this(SPI.getCachedServiceStream(ChartDefinitionProvider.class).collect(Collectors.toList()), true);
  }

  public DefaultChartDefinitionCenter(List<ChartDefinitionProvider> providers) {
    this(providers, true);
  }

  public DefaultChartDefinitionCenter(List<ChartDefinitionProvider> providers, boolean enabledOnly) {
    this.enabledOnly = enabledOnly;
    this.providers = Collections.unmodifiableList(new ArrayList<ChartDefinitionProvider>(
        Optional.ofNullable(providers).orElse(Collections.emptyList())));
    refresh();
  }

  public Optional<ChartDefinition> getDefinition(String name) {
    return Optional.ofNullable(definitionMap.get(name));
  }

  public List<ChartDefinition> getDefinitions() {
    return definitionMap.values().stream().collect(Collectors.toList());
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  public void onMetadataRefreshing() {
    refresh();
  }

  public synchronized void refresh() {
    definitionMap = Collections.unmodifiableMap(loadDefinitions(providers, enabledOnly));
  }

  private static Map<String, ChartDefinition> loadDefinitions(List<ChartDefinitionProvider> providers,
                                                                    boolean enabledOnly) {
    Map<Integer, List<ChartDefinitionProvider>> tieredProviders = Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(ChartDefinitionProvider::getOrder))
        .collect(Collectors.groupingBy(
            ChartDefinitionProvider::getOrder,
            TreeMap::new,
            Collectors.toList()
        ));

    Map<String, ChartDefinition> mergedDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    tieredProviders.values().forEach(tierProviders -> mergeTierDefinitions(mergedDefinitions, tierProviders));
    if (enabledOnly) {
      mergedDefinitions.entrySet().removeIf(entry -> !Boolean.TRUE.equals(entry.getValue().getIsEnabled()));
    }
    return mergedDefinitions;
  }

  private static void mergeTierDefinitions(Map<String, ChartDefinition> mergedDefinitions,
                                           Collection<ChartDefinitionProvider> tierProviders) {
    Map<String, ChartDefinition> tierDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    tierProviders.stream()
        .map(ChartDefinitionProvider::getDefinitions)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .forEach(definition -> {
          try {
            ChartDefinitionValidator.validateName(definition.getName());
          } catch (IllegalArgumentException error) {
            throw new PlatformException(error.getMessage(), error);
          }
          ChartDefinition existing = tierDefinitions.putIfAbsent(definition.getName(), definition);
          if (existing != null) {
            throw new PlatformException("Duplicate chart definition in the same precedence tier: "
                + definition.getName());
          }
        });

    tierDefinitions.forEach(mergedDefinitions::put);
  }

}
