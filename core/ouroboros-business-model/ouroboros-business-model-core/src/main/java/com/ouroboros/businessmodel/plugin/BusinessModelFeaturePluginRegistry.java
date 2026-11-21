package com.ouroboros.businessmodel.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.SPI;

/**
 * Central registry of {@link BusinessModelFeaturePlugin} instances.
 * <p>
 * Plugins are loaded via Java SPI and cached for repeated lookups.
 */
public final class BusinessModelFeaturePluginRegistry {
  private static final BusinessModelFeaturePluginRegistry INSTANCE = new BusinessModelFeaturePluginRegistry();

  private final List<BusinessModelFeaturePlugin> allPlugins;
  private final Map<String, BusinessModelFeaturePlugin> pluginMap;

  private BusinessModelFeaturePluginRegistry() {
    List<BusinessModelFeaturePlugin> loadedPlugins = SPI.getCachedReversedServiceStream(BusinessModelFeaturePlugin.class)
        .collect(Collectors.toList());
    this.allPlugins = buildAllPlugins(loadedPlugins);
    this.pluginMap = buildPluginMap(loadedPlugins);
  }

  BusinessModelFeaturePluginRegistry(List<BusinessModelFeaturePlugin> loadedPlugins) {
    this.allPlugins = buildAllPlugins(loadedPlugins);
    this.pluginMap = buildPluginMap(loadedPlugins);
  }

  private List<BusinessModelFeaturePlugin> buildAllPlugins(List<BusinessModelFeaturePlugin> loadedPlugins) {
    loadedPlugins.forEach(this::validatePluginModelTypes);
    return Collections.unmodifiableList(new ArrayList<>(loadedPlugins));
  }

  private Map<String, BusinessModelFeaturePlugin> buildPluginMap(List<BusinessModelFeaturePlugin> loadedPlugins) {
    Map<String, BusinessModelFeaturePlugin> loadedPluginMap = new LinkedHashMap<>();
    loadedPlugins.forEach(plugin -> loadedPluginMap.putIfAbsent(plugin.getPluginId(), plugin));
    return Collections.unmodifiableMap(loadedPluginMap);
  }

  /**
   * Singleton instance of the registry.
   *
   * @return registry instance
   */
  public static BusinessModelFeaturePluginRegistry getInstance() {
    return INSTANCE;
  }

  /**
   * All registered plugins.
   *
   * @return immutable plugin list
   */
  public List<BusinessModelFeaturePlugin> getAllPlugins() {
    return allPlugins;
  }

  /**
   * Lookup a plugin by id.
   *
   * @param pluginId plugin id
   * @return plugin lookup result
   */
  public Optional<BusinessModelFeaturePlugin> getPlugin(String pluginId) {
    return Optional.ofNullable(pluginMap.get(pluginId));
  }

  /**
   * Resolve plugins that support the given business model type.
   *
   * @param modelType business model type
   * @return immutable plugin list in registry order
   */
  public List<BusinessModelFeaturePlugin> getPluginsForModelType(String modelType) {
    String resolvedModelType = resolveModelType(modelType);
    List<BusinessModelFeaturePlugin> matchedPlugins = allPlugins.stream()
        .filter(plugin -> supportsModelType(plugin, resolvedModelType))
        .collect(Collectors.toList());
    return Collections.unmodifiableList(matchedPlugins);
  }

  /**
   * Resolve enabled plugins of a business model.
   * <p>
   * Enabled plugin ids are read from {@code enabledPlugins} or
   * {@code plugins.enabled} in the model extra properties. Required plugin
   * dependencies declared by {@link PluginMetadata#getRequiredPlugins()} are
   * included automatically.
   *
   * @param meta business model metadata
   * @return enabled plugins ordered by registry priority
   */
  public List<BusinessModelFeaturePlugin> getEnabledPlugins(BusinessModelMeta meta) {
    if (meta == null) {
      return Collections.emptyList();
    }

    List<String> enabledPluginIds = resolveEnabledPluginIds(meta);
    if (enabledPluginIds.isEmpty()) {
      return Collections.emptyList();
    }

    Set<String> resolvedPluginIds = new LinkedHashSet<>();
    enabledPluginIds.forEach(pluginId -> addPluginWithDependencies(pluginId, resolvedPluginIds, new LinkedHashSet<>()));

    return allPlugins.stream()
        .filter(plugin -> resolvedPluginIds.contains(plugin.getPluginId()))
        .collect(Collectors.toList());
  }

  private void validatePluginModelTypes(BusinessModelFeaturePlugin plugin) {
    List<String> supportedModelTypes = plugin.getSupportedModelTypes();
    if (supportedModelTypes == null || supportedModelTypes.stream().noneMatch(StringUtils::isNotBlank)) {
      throw new IllegalStateException("Business model feature plugin must declare at least one supported model type: " + plugin.getPluginId());
    }
  }

  private boolean supportsModelType(BusinessModelFeaturePlugin plugin, String modelType) {
    List<String> supportedModelTypes = plugin.getSupportedModelTypes();
    if (supportedModelTypes == null) {
      return false;
    }
    return supportedModelTypes.stream()
        .filter(StringUtils::isNotBlank)
        .anyMatch(supportedModelType -> BusinessModelTypes.ALL.equals(supportedModelType)
            || StringUtils.equalsIgnoreCase(supportedModelType, modelType));
  }

  private String resolveModelType(String modelType) {
    if (StringUtils.isBlank(modelType)) {
      return BusinessModelTypes.DEFAULT;
    }
    return modelType;
  }

  private List<String> resolveEnabledPluginIds(BusinessModelMeta meta) {
    List<String> enabledPlugins = toPluginIdList(meta.getExtraProp(List.class, "enabledPlugins").orElse(null));
    if (!enabledPlugins.isEmpty()) {
      return enabledPlugins;
    }

    return toPluginIdList(meta.getExtraPropDeep(List.class, Arrays.asList("plugins", "enabled")).orElse(null));
  }

  private List<String> toPluginIdList(Object rawValue) {
    if (!(rawValue instanceof List)) {
      return Collections.emptyList();
    }

    List<?> rawList = (List<?>) rawValue;
    return rawList.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());
  }

  private void addPluginWithDependencies(String pluginId, Set<String> resolvedPluginIds, Set<String> visitingPluginIds) {
    if (StringUtils.isBlank(pluginId) || resolvedPluginIds.contains(pluginId) || visitingPluginIds.contains(pluginId)) {
      return;
    }

    Optional<BusinessModelFeaturePlugin> plugin = getPlugin(pluginId);
    if (!plugin.isPresent()) {
      return;
    }

    visitingPluginIds.add(pluginId);
    PluginMetadata metadata = plugin.get().getMetadata();
    if (metadata != null) {
      metadata.getRequiredPlugins().forEach(requiredPluginId -> addPluginWithDependencies(requiredPluginId, resolvedPluginIds, visitingPluginIds));
    }
    visitingPluginIds.remove(pluginId);
    resolvedPluginIds.add(pluginId);
  }
}
