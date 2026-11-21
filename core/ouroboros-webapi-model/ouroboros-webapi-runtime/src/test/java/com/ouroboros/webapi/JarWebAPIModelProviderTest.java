package com.ouroboros.webapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarWebAPIModelProviderTest {

  @Test
  void shouldLoadTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarWebAPIModelProvider();

    var models = provider.getWebAPIModelMap();

    assertTrue(models.containsKey("GET /jar/orders"));
    assertEquals("Jar Orders", models.get("GET /jar/orders").getSummary());
  }
}
