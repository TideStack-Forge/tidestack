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

import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.runtime.provider.AiProviderConnectionDescriptorProvider;

public class DefaultAiProviderConnectionRegistry implements AiProviderConnectionRegistry {
  private final Map<String, AiProviderConnectionDescriptor> descriptorMap;
  private final List<AiProviderConnectionDescriptor> outwardDescriptors;

  public DefaultAiProviderConnectionRegistry(List<AiProviderConnectionDescriptorProvider> providers) {
    this.descriptorMap = Collections.unmodifiableMap(loadDescriptors(providers));
    this.outwardDescriptors = Collections.unmodifiableList(new ArrayList<>(descriptorMap.values().stream()
        .filter(AiProviderConnectionDescriptor::isEnabled)
        .collect(Collectors.toList())));
  }

  @Override
  public Optional<AiProviderConnectionDescriptor> get(String providerInstanceId) {
    return Optional.ofNullable(descriptorMap.get(normalize(providerInstanceId)))
        .filter(AiProviderConnectionDescriptor::isEnabled);
  }

  @Override
  public List<AiProviderConnectionDescriptor> getAll() {
    return outwardDescriptors;
  }

  private static Map<String, AiProviderConnectionDescriptor> loadDescriptors(List<AiProviderConnectionDescriptorProvider> providers) {
    var descriptors = new LinkedHashMap<String, AiProviderConnectionDescriptor>();
    orderedProviders(providers).stream()
        .map(AiProviderConnectionDescriptorProvider::getProviderConnectionDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(descriptor -> hasText(descriptor.getProviderInstanceId()))
        .forEach(descriptor -> descriptors.putIfAbsent(normalize(descriptor.getProviderInstanceId()), descriptor));
    return descriptors;
  }

  private static List<AiProviderConnectionDescriptorProvider> orderedProviders(List<AiProviderConnectionDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiProviderConnectionDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String providerInstanceId) {
    return providerInstanceId == null ? null : providerInstanceId.trim().toLowerCase(Locale.ROOT);
  }
}
