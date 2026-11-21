package com.ouroboros.statistics.reporting.runtime.center;

import java.util.List;
import java.util.Optional;

/** Definition lookup and refresh boundary shared by runtime and design-time consumers. */
public interface DefinitionCenter<T> {
  Optional<T> getDefinition(String name);

  List<T> getDefinitions();

  void refresh();
}
