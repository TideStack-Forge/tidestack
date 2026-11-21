package com.ouroboros.ability.all.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.uischema.JarUISchemaProvider;
import com.ouroboros.uischema.schema.SchemaNode;

class EnterpriseManagementSchemaAssemblyTest {
  @ParameterizedTest
  @ValueSource(strings = {"enterprise-directory", "enterprise-notifications", "enterprise-workbench"})
  void installedManagementPagesLoadThroughTheProductionSchemaProvider(String page) {
    var schema = new JarUISchemaProvider().getUISchema("/system-setting/integration-management/" + page);

    assertThat(schema).as("Installed management page %s must be served by the original metadata loader", page)
        .isPresent().get().isInstanceOfSatisfying(SchemaNode.class,
            node -> assertThat(node.getType()).isIn("page", "service"));
  }
}
