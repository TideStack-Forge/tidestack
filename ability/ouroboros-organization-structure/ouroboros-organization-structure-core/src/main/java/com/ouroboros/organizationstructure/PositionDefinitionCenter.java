package com.ouroboros.organizationstructure;

import java.util.List;
import java.util.Optional;

import com.ouroboros.util.SPI;

public interface PositionDefinitionCenter {
  Optional<PositionDefinition> getDefinition(String key);

  List<PositionDefinition> getDefinitions();

  static PositionDefinitionCenter instance() {
    return SPI.getCachedPrimaryService(PositionDefinitionCenter.class);
  }
}
