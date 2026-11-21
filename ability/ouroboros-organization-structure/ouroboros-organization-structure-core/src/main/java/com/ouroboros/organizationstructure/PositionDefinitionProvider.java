package com.ouroboros.organizationstructure;

import java.util.Collections;
import java.util.List;

public interface PositionDefinitionProvider {
  List<PositionDefinition> getDefinitions();

  default int getOrder() {
    return 0;
  }

  default List<PositionDefinition> getDefinitionsOrEmpty() {
    List<PositionDefinition> definitions = getDefinitions();
    return definitions == null ? Collections.emptyList() : definitions;
  }
}
