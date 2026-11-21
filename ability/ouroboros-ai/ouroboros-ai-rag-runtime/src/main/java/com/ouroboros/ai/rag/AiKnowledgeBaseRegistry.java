package com.ouroboros.ai.rag;

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

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;
import com.ouroboros.ai.runtime.provider.AiKnowledgeBaseDescriptorProvider;

public class AiKnowledgeBaseRegistry {
  private final Map<String, AiKnowledgeBaseDescriptor> descriptorMap;
  private final List<AiKnowledgeBaseDescriptor> outwardDescriptors;

  public AiKnowledgeBaseRegistry(List<AiKnowledgeBaseDescriptorProvider> providers) {
    this.descriptorMap = Collections.unmodifiableMap(loadDescriptors(providers));
    this.outwardDescriptors = Collections.unmodifiableList(new ArrayList<>(descriptorMap.values().stream()
        .filter(AiKnowledgeBaseDescriptor::isEnabled)
        .collect(Collectors.toList())));
  }

  public Optional<AiKnowledgeBaseDescriptor> get(String knowledgeBaseId) {
    return Optional.ofNullable(descriptorMap.get(normalize(knowledgeBaseId)))
        .filter(AiKnowledgeBaseDescriptor::isEnabled);
  }

  public List<AiKnowledgeBaseDescriptor> getAll() {
    return outwardDescriptors;
  }

  private static Map<String, AiKnowledgeBaseDescriptor> loadDescriptors(List<AiKnowledgeBaseDescriptorProvider> providers) {
    var descriptors = new LinkedHashMap<String, AiKnowledgeBaseDescriptor>();
    orderedProviders(providers).stream()
        .map(AiKnowledgeBaseDescriptorProvider::getKnowledgeBaseDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(descriptor -> hasText(descriptor.getKnowledgeBaseId()))
        .forEach(descriptor -> descriptors.putIfAbsent(normalize(descriptor.getKnowledgeBaseId()), descriptor));
    return descriptors;
  }

  private static List<AiKnowledgeBaseDescriptorProvider> orderedProviders(List<AiKnowledgeBaseDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiKnowledgeBaseDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String knowledgeBaseId) {
    return knowledgeBaseId == null ? null : knowledgeBaseId.trim().toLowerCase(Locale.ROOT);
  }
}
