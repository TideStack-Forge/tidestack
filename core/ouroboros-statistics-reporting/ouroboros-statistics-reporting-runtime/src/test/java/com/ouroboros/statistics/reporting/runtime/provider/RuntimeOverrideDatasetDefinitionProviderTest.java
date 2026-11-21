package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOverrideDatasetDefinitionProviderTest {
  @TempDir
  Path appHome;

  @Test
  void shouldLoadEnabledDatasetsFromRuntimeOverrideDirectory() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/datasets/demo.sales.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.sales", true)
    );

    RuntimeOverrideDatasetDefinitionProvider provider = new RuntimeOverrideDatasetDefinitionProvider(appHome);

    assertEquals(Collections.singletonList("demo.sales"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).collect(Collectors.toList()));
    assertEquals(200, provider.getOrder());
  }

  @Test
  void shouldLoadDisabledDatasetsSoTheCenterCanApplyPrecedence() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/datasets/demo.sales.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.sales", true)
    );
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/datasets/demo.disabled.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.draft", false)
    );
    RuntimeOverrideDatasetDefinitionProvider provider = new RuntimeOverrideDatasetDefinitionProvider(appHome);

    assertEquals(java.util.Arrays.asList("demo.draft", "demo.sales"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).collect(Collectors.toList()));
  }

  @Test
  void shouldReturnEmptyWhenRuntimeOverrideDirectoryDoesNotExist() {
    RuntimeOverrideDatasetDefinitionProvider provider = new RuntimeOverrideDatasetDefinitionProvider(appHome);

    assertTrue(provider.getDefinitions().isEmpty());
  }
}
