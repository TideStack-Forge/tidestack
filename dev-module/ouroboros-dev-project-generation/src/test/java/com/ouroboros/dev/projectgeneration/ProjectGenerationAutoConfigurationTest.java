package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

class ProjectGenerationAutoConfigurationTest {

  @Test
  void shouldScanProjectGenerationComponents() {
    ComponentScan componentScan = ProjectGenerationAutoConfiguration.class.getAnnotation(ComponentScan.class);
    assertNotNull(componentScan);
  }

  @Test
  void shouldScanProjectGenerationDataModels() {
    DataModelScan dataModelScan = ProjectGenerationAutoConfiguration.class.getAnnotation(DataModelScan.class);
    assertNotNull(dataModelScan);
  }

  @Test
  void shouldBeRegisteredInAutoConfigurationImports() throws Exception {
    String imports = new String(
        getClass().getClassLoader()
            .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .readAllBytes()
    );
    assertTrue(imports.contains(ProjectGenerationAutoConfiguration.class.getName()));
  }
}
