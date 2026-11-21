package com.ouroboros.enterprise.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.exception.InternalException;

/** The single provider-schema index, independent of any domain capability registry. */
public final class EnterpriseConnectionProviderRegistry {
  private final Map<String, EnterpriseConnectionProvider> providers;

  public EnterpriseConnectionProviderRegistry(List<EnterpriseConnectionProvider> contributions) {
    providers = contributions.stream().collect(Collectors.toUnmodifiableMap(
        EnterpriseConnectionProvider::provider, provider -> provider, (left, right) -> {
          throw new InternalException("企业连接 provider 重复: " + right.provider());
        }));
    providers.keySet().forEach(name -> {
      if (!name.matches("[a-z0-9-]+")) { throw new InternalException("企业连接 provider 标识无效"); }
    });
  }

  public Optional<EnterpriseConnectionProvider> schemaProviderOf(String provider) {
    return provider == null ? Optional.empty() : Optional.ofNullable(providers.get(provider));
  }

  public List<EnterpriseConnectionProvider> providers() { return List.copyOf(providers.values()); }

  public static EnterpriseConnectionProviderRegistry of(List<EnterpriseConnectionProvider> providers) {
    return new EnterpriseConnectionProviderRegistry(providers);
  }
}
