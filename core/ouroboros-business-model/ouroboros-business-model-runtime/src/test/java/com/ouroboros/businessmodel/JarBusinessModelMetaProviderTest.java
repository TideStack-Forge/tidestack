package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarBusinessModelMetaProviderTest {

  @Test
  void shouldLoadTypedMetadataAndIgnoreLegacyDirectory() {
    var provider = new JarBusinessModelMetaProvider();

    var model = provider.getBusinessModelMeta("jar.order");

    assertTrue(model.isPresent());
    assertEquals("Typed Order", model.get().getTitle());
    assertFalse(provider.getBusinessModelMetaMap().values().stream()
        .anyMatch(meta -> "Legacy Order".equals(meta.getTitle())));
    assertFalse(provider.getBusinessModelMetaMap().containsKey("modules"));
  }
}
