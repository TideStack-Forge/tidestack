package com.ouroboros.organizationstructure.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledDefaultPositionDefinitionProviderTest {

  @Test
  void shouldLoadDefaultPositionFromMetadataDirectory() {
    var provider = new BundledDefaultPositionDefinitionProvider();

    var definitions = provider.getDefinitions();

    assertTrue(definitions.stream().anyMatch(definition -> "member".equals(definition.getKey())));
  }
}
