package com.ouroboros.dev.projectgeneration.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryProjectTemplateCatalogReplayStateStore implements ProjectTemplateCatalogReplayStateStore {

  private final ConcurrentMap<String, ProjectTemplateCatalogReplayState> states;

  public InMemoryProjectTemplateCatalogReplayStateStore() {
    this(new ConcurrentHashMap<>());
  }

  public InMemoryProjectTemplateCatalogReplayStateStore(Map<String, ProjectTemplateCatalogReplayState> states) {
    this.states = states instanceof ConcurrentMap<String, ProjectTemplateCatalogReplayState> concurrentMap
        ? concurrentMap
        : new ConcurrentHashMap<>(states == null ? Map.of() : states);
  }

  @Override
  public ProjectTemplateCatalogReplayState accept(String catalogStateId, long sequence, String digest) {
    requireStateId(catalogStateId);
    return states.compute(catalogStateId, (ignored, current) -> nextState(current, sequence, digest));
  }

  @Override
  public Optional<ProjectTemplateCatalogReplayState> current(String catalogStateId) {
    requireStateId(catalogStateId);
    return Optional.ofNullable(states.get(catalogStateId));
  }

  private static ProjectTemplateCatalogReplayState nextState(
      ProjectTemplateCatalogReplayState current,
      long sequence,
      String digest
  ) {
    if (current == null) {
      return new ProjectTemplateCatalogReplayState(sequence, digest, 1);
    }
    if (sequence == current.sequence() && digest.equals(current.digest())) {
      return current;
    }
    if (sequence <= current.sequence()) {
      throw new IllegalArgumentException("Catalog replay sequence was not newer than shared high-water mark");
    }
    return new ProjectTemplateCatalogReplayState(sequence, digest, current.version() + 1);
  }

  private static void requireStateId(String catalogStateId) {
    if (catalogStateId == null || catalogStateId.isBlank()) {
      throw new IllegalArgumentException("Catalog state id is required");
    }
  }
}
