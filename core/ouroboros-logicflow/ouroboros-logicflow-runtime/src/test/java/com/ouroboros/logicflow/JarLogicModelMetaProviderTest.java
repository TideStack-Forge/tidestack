package com.ouroboros.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarLogicModelMetaProviderTest {

  @Test
  void shouldLoadTypedMetadataAndIgnoreLegacyDirectory() {
    var provider = new JarLogicModelMetaProvider();

    var model = provider.getFlowModelMeta("jar.flow");

    assertTrue(model.isPresent());
    assertEquals("typed flow", model.get().getDescription());
    assertTrue(provider.getFlowModelMetas().values().stream()
        .noneMatch(meta -> "legacy flow".equals(meta.getDescription())));
  }
}
