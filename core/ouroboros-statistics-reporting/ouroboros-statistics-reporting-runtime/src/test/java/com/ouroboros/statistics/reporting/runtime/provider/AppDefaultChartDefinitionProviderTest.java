package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDefaultChartDefinitionProviderTest {
  @TempDir
  Path appHome;

  @Test
  void shouldLoadEnabledViewsFromAppDefaultDirectories() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/charts/demo.report.chart.json"),
        DefinitionProviderTestSupport.chart("demo.report", "demo.sales", true)
    );
    AppDefaultChartDefinitionProvider provider = new AppDefaultChartDefinitionProvider(appHome);

    assertEquals(Collections.singletonList("demo.report"),
        provider.getDefinitions().stream()
            .map(view -> view.getName())
            .sorted()
            .collect(Collectors.toList()));
    assertEquals(100, provider.getOrder());
  }

  @Test
  void shouldLoadDisabledChartsSoTheCenterCanApplyPrecedence() throws Exception {
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/charts/demo.report.chart.json"),
        DefinitionProviderTestSupport.chart("demo.report", "demo.sales", true)
    );
    DefinitionProviderTestSupport.writeJson(
        appHome.resolve("statistics-reporting/charts/demo.disabled.chart.json"),
        DefinitionProviderTestSupport.chart("demo.draft", "demo.sales", false)
    );

    AppDefaultChartDefinitionProvider provider = new AppDefaultChartDefinitionProvider(appHome);

    assertEquals(java.util.List.of("demo.draft", "demo.report"),
        provider.getDefinitions().stream().map(view -> view.getName()).collect(Collectors.toList()));
  }

  @Test
  void shouldReturnEmptyWhenAppDefaultViewDirectoriesDoNotExist() {
    AppDefaultChartDefinitionProvider provider = new AppDefaultChartDefinitionProvider(appHome);

    assertTrue(provider.getDefinitions().isEmpty());
  }
}
