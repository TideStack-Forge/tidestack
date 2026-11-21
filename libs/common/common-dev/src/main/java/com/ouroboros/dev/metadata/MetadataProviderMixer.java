package com.ouroboros.dev.metadata;

import java.util.*;
import java.util.stream.Collectors;

public interface MetadataProviderMixer {
  List<Metadata> getAppAllMetadataByModule(String appName, String moduleName);

  Optional<Metadata> getOne(String appName, String moduleName, String metadataName);

  static MetadataProviderMixer getDistinctMixer(List<MetadataProvider> providers) {
    return new DistinctMetadataProviderMixer(providers);
  }

  static MetadataProviderMixer getConcatMixer(List<MetadataProvider> providers) {
    return new ConcatMetadataProviderMixer(providers);
  }

  class DistinctMetadataProviderMixer implements MetadataProviderMixer {
    private final List<MetadataProvider> providers;

    public DistinctMetadataProviderMixer(List<MetadataProvider> providers) {
      this.providers = providers;
    }

    @Override
    public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
      Map<String, Metadata> metadataMap = providers.stream()
          .flatMap(provider -> provider.getAppAllMetadataByModule(appName, moduleName).stream())
          // 排序，优先级由低到高，升序
          .sorted(Comparator.comparingInt(Metadata::getPriority))
          .collect(() -> new TreeMap<>(String::compareToIgnoreCase),
              (map, meta) -> map.put(meta.getName(), meta),
              TreeMap::putAll);
      return metadataMap.values().stream()
          .collect(Collectors.toList());
    }

    @Override
    public Optional<Metadata> getOne(String appName, String moduleName, String metadataName) {
      Optional<Metadata> optional = providers.stream()
          .flatMap(provider -> provider.getAppAllMetadataByModule(appName, moduleName).stream())
          // 排序，优先级由高到低，降序
          .sorted(Comparator.comparingInt(Metadata::getPriority).reversed())
          .filter(meta -> meta.getName().equalsIgnoreCase(metadataName))
          .findFirst();
      return optional;
    }

  }

  class ConcatMetadataProviderMixer implements MetadataProviderMixer {
    private final List<MetadataProvider> providers;

    public ConcatMetadataProviderMixer(List<MetadataProvider> providers) {
      this.providers = providers;
    }

    @Override
    public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
      return providers.stream()
          .flatMap(provider -> provider.getAppAllMetadataByModule(appName, moduleName).stream())
          .collect(Collectors.toList());
    }

    @Override
    public Optional<Metadata> getOne(String appName, String moduleName, String metadataName) {
      return providers.stream()
          .flatMap(provider -> provider.getAppAllMetadataByModule(appName, moduleName).stream())
          .filter(meta -> meta.getName().equalsIgnoreCase(metadataName))
          .findFirst();
    }
  }
}
