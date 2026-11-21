package com.ouroboros.configuration.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;

class RuntimeConfigurationValueModelMetadataTest {

  @Test
  void revisionColumnAllowsLegacyNullValues() {
    var metadata = TypedModelMetaHelper.scanModelMate("com.ouroboros.configuration.model")
        .get(RuntimeConfigurationValueModel.class.getSimpleName());
    var revision = metadata.getFields().stream()
        .filter(field -> "revision".equals(field.getName()))
        .findFirst()
        .orElseThrow();

    assertTrue(revision.getIsNullable());
  }
}
