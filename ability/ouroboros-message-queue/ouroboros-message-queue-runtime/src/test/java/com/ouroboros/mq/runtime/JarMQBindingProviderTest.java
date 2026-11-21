package com.ouroboros.mq.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JarMQBindingProviderTest {

  @Test
  void shouldLoadTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarMQBindingProvider();

    var bindings = provider.getAllBindings();

    assertEquals(1, bindings.size());
    assertEquals("jar-producer", bindings.get(0).getBindingName());
    assertEquals("orders.created", bindings.get(0).getDestination());
    assertTrue(bindings.get(0).getExtraProps().isEmpty());
  }
}
