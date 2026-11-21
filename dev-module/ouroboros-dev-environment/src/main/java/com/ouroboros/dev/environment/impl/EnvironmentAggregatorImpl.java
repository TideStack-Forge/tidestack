package com.ouroboros.dev.environment.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.environment.Environment;
import com.ouroboros.dev.environment.EnvironmentAggregator;
import com.ouroboros.dev.environment.EnvironmentProvider;

@Component
public class EnvironmentAggregatorImpl implements EnvironmentAggregator {
  private final List<EnvironmentProvider> environmentProviders;
  public EnvironmentAggregatorImpl(ObjectProvider<EnvironmentProvider> environmentProviders) {
    this.environmentProviders = environmentProviders.orderedStream()
        .collect(Collectors.toList());
  }

  @Override
  public List<Environment> getAll() {
    return environmentProviders.stream()
        .map(EnvironmentProvider::getAll)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
