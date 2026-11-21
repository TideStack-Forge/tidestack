package com.ouroboros.ai.runtime.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.runtime.provider.AiModelDescriptorProvider;

public class DefaultAiModelRegistry implements AiModelRegistry {
  private final Map<String, AiModelDescriptor> descriptorMap;
  private final List<AiModelDescriptor> outwardDescriptors;

  public DefaultAiModelRegistry(List<AiModelDescriptorProvider> providers) {
    this.descriptorMap = Collections.unmodifiableMap(loadDescriptors(providers));
    this.outwardDescriptors = Collections.unmodifiableList(new ArrayList<>(descriptorMap.values().stream()
        .filter(AiModelDescriptor::isEnabled)
        .collect(Collectors.toList())));
  }

  @Override
  public Optional<AiModelDescriptor> get(String modelId) {
    return Optional.ofNullable(descriptorMap.get(normalize(modelId)))
        .filter(AiModelDescriptor::isEnabled);
  }

  @Override
  public List<AiModelDescriptor> getAll() {
    return outwardDescriptors;
  }

  private static Map<String, AiModelDescriptor> loadDescriptors(List<AiModelDescriptorProvider> providers) {
    var descriptors = new LinkedHashMap<String, AiModelDescriptor>();
    orderedProviders(providers).stream()
        .map(AiModelDescriptorProvider::getModelDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(descriptor -> hasText(descriptor.getModelId()))
        .forEach(descriptor -> descriptors.putIfAbsent(normalize(descriptor.getModelId()), descriptor));
    return descriptors;
  }

  private static List<AiModelDescriptorProvider> orderedProviders(List<AiModelDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiModelDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String modelId) {
    return modelId == null ? null : modelId.trim().toLowerCase(Locale.ROOT);
  }
}
