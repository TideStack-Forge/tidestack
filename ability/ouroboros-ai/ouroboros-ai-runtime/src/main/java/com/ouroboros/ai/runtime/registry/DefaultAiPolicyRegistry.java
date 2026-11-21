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

import com.ouroboros.ai.descriptor.AiPolicyDescriptor;
import com.ouroboros.ai.runtime.provider.AiPolicyDescriptorProvider;

public class DefaultAiPolicyRegistry implements AiPolicyRegistry {
  private final Map<String, AiPolicyDescriptor> descriptorMap;
  private final List<AiPolicyDescriptor> outwardDescriptors;

  public DefaultAiPolicyRegistry(List<AiPolicyDescriptorProvider> providers) {
    this.descriptorMap = Collections.unmodifiableMap(loadDescriptors(providers));
    this.outwardDescriptors = Collections.unmodifiableList(new ArrayList<>(descriptorMap.values()));
  }

  @Override
  public Optional<AiPolicyDescriptor> get(String policyId) {
    return Optional.ofNullable(descriptorMap.get(normalize(policyId)));
  }

  @Override
  public List<AiPolicyDescriptor> getAll() {
    return outwardDescriptors;
  }

  private static Map<String, AiPolicyDescriptor> loadDescriptors(List<AiPolicyDescriptorProvider> providers) {
    var descriptors = new LinkedHashMap<String, AiPolicyDescriptor>();
    orderedProviders(providers).stream()
        .map(AiPolicyDescriptorProvider::getPolicyDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(descriptor -> hasText(descriptor.getPolicyId()))
        .forEach(descriptor -> descriptors.putIfAbsent(normalize(descriptor.getPolicyId()), descriptor));
    return descriptors;
  }

  private static List<AiPolicyDescriptorProvider> orderedProviders(List<AiPolicyDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiPolicyDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String policyId) {
    return policyId == null ? null : policyId.trim().toLowerCase(Locale.ROOT);
  }
}
