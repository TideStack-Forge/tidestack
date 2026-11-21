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

import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.runtime.provider.AiToolDescriptorProvider;

public class DefaultAiToolRegistry implements AiToolRegistry {
  private final Map<String, AiToolDescriptor> descriptorMap;
  private final List<AiToolDescriptor> outwardDescriptors;

  public DefaultAiToolRegistry(List<AiToolDescriptorProvider> providers) {
    this.descriptorMap = Collections.unmodifiableMap(loadDescriptors(providers));
    this.outwardDescriptors = Collections.unmodifiableList(new ArrayList<>(descriptorMap.values()));
  }

  @Override
  public Optional<AiToolDescriptor> get(String toolName) {
    return Optional.ofNullable(descriptorMap.get(normalize(toolName)));
  }

  @Override
  public List<AiToolDescriptor> getAll() {
    return outwardDescriptors;
  }

  private static Map<String, AiToolDescriptor> loadDescriptors(List<AiToolDescriptorProvider> providers) {
    var descriptors = new LinkedHashMap<String, AiToolDescriptor>();
    orderedProviders(providers).stream()
        .map(AiToolDescriptorProvider::getToolDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(descriptor -> hasText(descriptor.getToolName()))
        .forEach(descriptor -> descriptors.putIfAbsent(normalize(descriptor.getToolName()), descriptor));
    return descriptors;
  }

  private static List<AiToolDescriptorProvider> orderedProviders(List<AiToolDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiToolDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String toolName) {
    return toolName == null ? null : toolName.trim().toLowerCase(Locale.ROOT);
  }
}
