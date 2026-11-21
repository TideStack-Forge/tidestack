package com.ouroboros.statistics.reporting.dev.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceChartDefinitionProviderTest {
  @TempDir
  Path workspace;

  @Test
  void shouldOnlyExposeEnabledViewsToRuntimeProvider() throws Exception {
    writeChart("demo.sales.overview", true);

    WorkspaceChartDefinitionProvider provider = new WorkspaceChartDefinitionProvider(workspace);

    assertEquals(
        java.util.Collections.singletonList("demo.sales.overview"),
        provider.getDefinitions().stream().map(view -> view.getName()).sorted().collect(Collectors.toList())
    );
  }

  @Test
  void shouldExposeDisabledViewsWhenPreviewProviderRequestsAllDefinitions() throws Exception {
    writeChart("demo.sales.overview", true);
    writeChart("demo.sales.disabled-report", false);

    WorkspaceChartDefinitionProvider provider = new WorkspaceChartDefinitionProvider(workspace, false);

    assertEquals(
        java.util.Arrays.asList("demo.sales.disabled-report", "demo.sales.overview"),
        provider.getDefinitions().stream().map(view -> view.getName()).sorted().collect(Collectors.toList())
    );
  }

  private void writeChart(String name, boolean isEnabled) throws Exception {
    Path file = workspace.resolve("statistics-reporting/charts/" + name + ".chart.json");
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(
        StatisticsReportingDevTestSupport.minimalChart(name, "demo.sales", isEnabled)
    ).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldReportInvalidJsonWithItsFileAndCause() throws Exception {
    writeChart("demo.valid", true);
    Path file = workspace.resolve("statistics-reporting/charts/demo.invalid.chart.json");
    WorkspaceChartDefinitionProvider provider = new WorkspaceChartDefinitionProvider(workspace);
    for (String content : new String[]{"{broken", "{\"name\":\"demo.invalid\",\"removedField\":true}"}) {
      Files.writeString(file, content);

      PlatformException error = assertThrows(PlatformException.class, provider::getDefinitions);

      assertTrue(error.getMessage().contains(file.toString()));
      assertNotNull(error.getCause());
    }
  }

  @Test
  void shouldRejectNullAndUnnamedDefinitions() throws Exception {
    Path file = workspace.resolve("statistics-reporting/charts/demo.invalid.chart.json");
    Files.createDirectories(file.getParent());
    WorkspaceChartDefinitionProvider provider = new WorkspaceChartDefinitionProvider(workspace, false);
    for (String content : new String[]{"null", "{}", "{\"name\":\" \"}"}) {
      Files.writeString(file, content);

      assertTrue(assertThrows(PlatformException.class, provider::getDefinitions)
          .getMessage().contains("name is required"));
    }
  }

}
