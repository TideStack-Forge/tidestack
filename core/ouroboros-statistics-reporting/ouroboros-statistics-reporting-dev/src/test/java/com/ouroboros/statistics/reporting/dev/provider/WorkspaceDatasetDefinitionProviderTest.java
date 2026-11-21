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

class WorkspaceDatasetDefinitionProviderTest {
  @TempDir
  Path workspace;

  @Test
  void shouldOnlyExposeEnabledDatasetsToRuntimeProvider() throws Exception {
    writeDataset("demo.enabled", true);
    writeDataset("demo.disabled", false);

    WorkspaceDatasetDefinitionProvider provider = new WorkspaceDatasetDefinitionProvider(workspace);

    assertEquals(
        java.util.Collections.singletonList("demo.enabled"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).collect(Collectors.toList())
    );
  }

  @Test
  void shouldExposeDisabledDatasetsWhenPreviewProviderRequestsAllDefinitions() throws Exception {
    writeDataset("demo.enabled", true);
    writeDataset("demo.disabled", false);

    WorkspaceDatasetDefinitionProvider provider = new WorkspaceDatasetDefinitionProvider(workspace, false);

    assertEquals(
        java.util.Arrays.asList("demo.disabled", "demo.enabled"),
        provider.getDefinitions().stream().map(dataset -> dataset.getName()).sorted().collect(Collectors.toList())
    );
  }

  private void writeDataset(String name, boolean isEnabled) throws Exception {
    Path file = workspace.resolve("statistics-reporting/datasets/" + name + ".dataset.json");
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(
        StatisticsReportingDevTestSupport.minimalDataset(name, isEnabled)
    ).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldReportInvalidJsonWithItsFileAndCause() throws Exception {
    writeDataset("demo.valid", true);
    Path file = workspace.resolve("statistics-reporting/datasets/demo.invalid.dataset.json");
    WorkspaceDatasetDefinitionProvider provider = new WorkspaceDatasetDefinitionProvider(workspace);
    for (String content : new String[]{"{broken", "{\"name\":\"demo.invalid\",\"removedField\":true}"}) {
      Files.writeString(file, content);

      PlatformException error = assertThrows(PlatformException.class, provider::getDefinitions);

      assertTrue(error.getMessage().contains(file.toString()));
      assertNotNull(error.getCause());
    }
  }

  @Test
  void shouldRejectNullAndUnnamedDefinitions() throws Exception {
    Path file = workspace.resolve("statistics-reporting/datasets/demo.invalid.dataset.json");
    Files.createDirectories(file.getParent());
    WorkspaceDatasetDefinitionProvider provider = new WorkspaceDatasetDefinitionProvider(workspace, false);
    for (String content : new String[]{"null", "{}", "{\"name\":\" \"}"}) {
      Files.writeString(file, content);

      assertTrue(assertThrows(PlatformException.class, provider::getDefinitions)
          .getMessage().contains("name is required"));
    }
  }
}
