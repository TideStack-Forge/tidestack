package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOverrideChartDefinitionProviderTest {
  @TempDir
  Path appHome;

  @Test
  void shouldLoadEnabledViewsFromRuntimeOverrideDirectories() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/charts/demo.report.chart.json"),
        DefinitionProviderTestSupport.chart("demo.report", "demo.sales", true)
    );
    RuntimeOverrideChartDefinitionProvider provider = new RuntimeOverrideChartDefinitionProvider(appHome);

    assertEquals(Collections.singletonList("demo.report"),
        provider.getDefinitions().stream()
            .map(view -> view.getName())
            .sorted()
            .collect(Collectors.toList()));
    assertEquals(200, provider.getOrder());
  }

  @Test
  void shouldLoadDisabledChartsSoTheCenterCanApplyPrecedence() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/charts/demo.report.chart.json"),
        DefinitionProviderTestSupport.chart("demo.report", "demo.sales", true)
    );
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("data/statistics-reporting/charts/demo.disabled.chart.json"),
        DefinitionProviderTestSupport.chart("demo.draft", "demo.sales", false)
    );

    RuntimeOverrideChartDefinitionProvider provider = new RuntimeOverrideChartDefinitionProvider(appHome);

    assertEquals(java.util.List.of("demo.draft", "demo.report"),
        provider.getDefinitions().stream().map(view -> view.getName()).collect(Collectors.toList()));
  }

  @Test
  void shouldReturnEmptyWhenRuntimeOverrideViewDirectoriesDoNotExist() {
    RuntimeOverrideChartDefinitionProvider provider = new RuntimeOverrideChartDefinitionProvider(appHome);

    assertTrue(provider.getDefinitions().isEmpty());
  }
}
