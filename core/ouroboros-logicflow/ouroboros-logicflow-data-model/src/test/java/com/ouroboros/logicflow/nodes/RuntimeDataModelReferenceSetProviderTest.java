package com.ouroboros.logicflow.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelCenter;

class RuntimeDataModelReferenceSetProviderTest {
  @AfterEach
  void resetDataModelSnapshot() throws Exception {
    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  void providerIsDiscoverableThroughTheOfficialSpi() {
    assertTrue(ServiceLoader.load(NodeReferenceSetProvider.class).stream()
        .map(ServiceLoader.Provider::type)
        .anyMatch(RuntimeDataModelReferenceSetProvider.class::equals));
  }

  @Test
  void providerReportsNotReadyBeforeTheFirstDataModelSnapshot() {
    var referenceSet = new RuntimeDataModelReferenceSetProvider().getReferenceSet();
    var catalog = NodeReferenceCatalog.of(java.util.List.of(referenceSet));

    var resolution = catalog.resolve(DataModelReferences.KIND, "Customer");
    var indeterminate = assertInstanceOf(NodeReferenceResolution.Indeterminate.class, resolution);
    assertEquals(NodeReferenceIndeterminateReason.NOT_READY, indeterminate.reason());
  }
}
