package com.ouroboros.devhelper.metadata.collector;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.ouroboros.devhelper.metadata.MetadataCollector;

import static org.junit.jupiter.api.Assertions.*;

class MetadataCollectorSpiContractTest {

  @Test
  void serviceLoaderFindsFieldTypeDefinitionMetadataCollector() {
    ServiceLoader<MetadataCollector> serviceLoader = ServiceLoader.load(MetadataCollector.class);

    boolean found = false;
    for (MetadataCollector collector : serviceLoader) {
      if (collector instanceof FieldTypeDefinitionMetadataCollector) {
        found = true;
        break;
      }
    }

    assertTrue(found);
  }
}
