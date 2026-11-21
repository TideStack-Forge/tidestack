package com.ouroboros.organizationstructure.definition;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.PositionDefinitionProvider;

@Component
public class PositionDefinitionCenterImpl implements PositionDefinitionCenter {
  private final DefaultPositionDefinitionCenter delegate;

  public PositionDefinitionCenterImpl(ObjectProvider<PositionDefinitionProvider> providers) {
    this.delegate = new DefaultPositionDefinitionCenter(providers.orderedStream().collect(java.util.stream.Collectors.toList()));
    Proxy.INSTANCE = this;
  }

  @Override
  public Optional<PositionDefinition> getDefinition(String key) {
    return delegate.getDefinition(key);
  }

  @Override
  public List<PositionDefinition> getDefinitions() {
    return delegate.getDefinitions();
  }

  public static class Proxy implements PositionDefinitionCenter {
    private static PositionDefinitionCenter INSTANCE;

    @Override
    public Optional<PositionDefinition> getDefinition(String key) {
      return INSTANCE == null ? Optional.empty() : INSTANCE.getDefinition(key);
    }

    @Override
    public List<PositionDefinition> getDefinitions() {
      return INSTANCE == null ? java.util.Collections.emptyList() : INSTANCE.getDefinitions();
    }
  }
}
