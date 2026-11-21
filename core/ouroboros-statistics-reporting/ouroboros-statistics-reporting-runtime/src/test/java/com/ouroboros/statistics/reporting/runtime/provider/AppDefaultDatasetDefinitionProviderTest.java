package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDefaultDatasetDefinitionProviderTest {
  @TempDir
  Path appHome;

  @Test
  void shouldLoadEnabledDatasetsFromAppDefaultDirectory() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/datasets/demo.sales.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.sales", true)
    );

    AppDefaultDatasetDefinitionProvider provider = new AppDefaultDatasetDefinitionProvider(appHome);

    assertEquals(Collections.singletonList("demo.sales"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).collect(Collectors.toList()));
    assertEquals(100, provider.getOrder());
  }

  @Test
  void shouldLoadDisabledDatasetsSoTheCenterCanApplyPrecedence() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/datasets/demo.sales.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.sales", true)
    );
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/datasets/demo.disabled.dataset.json"),
        DefinitionProviderTestSupport.dataset("demo.draft", false)
    );
    AppDefaultDatasetDefinitionProvider provider = new AppDefaultDatasetDefinitionProvider(appHome);

    assertEquals(java.util.Arrays.asList("demo.draft", "demo.sales"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).collect(Collectors.toList()));
  }

  @Test
  void shouldReturnEmptyWhenAppDefaultDirectoryDoesNotExist() {
    AppDefaultDatasetDefinitionProvider provider = new AppDefaultDatasetDefinitionProvider(appHome);

    assertTrue(provider.getDefinitions().isEmpty());
  }
}
