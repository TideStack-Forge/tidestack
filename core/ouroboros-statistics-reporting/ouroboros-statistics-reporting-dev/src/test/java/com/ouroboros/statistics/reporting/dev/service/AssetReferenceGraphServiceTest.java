package com.ouroboros.statistics.reporting.dev.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.governance.AssetReferenceGraph;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.FileBackedAssetReferenceGraphService;
import com.ouroboros.statistics.reporting.dev.service.impl.FileBackedDatasetReferenceResolver;
import com.ouroboros.statistics.reporting.dev.service.impl.ChartDevService;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetReferenceGraphServiceTest {
  private static final String CHART_DIRECTORY = "statistics-reporting/charts";

  @TempDir
  Path workspace;

  @AfterEach
  void cleanup() {
    StatisticsReportingDevTestSupport.clearTestContext();
  }

  @Test
  void resolve_shouldBuildDatasetChartGraph() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();
    Try<LowcodeDatasetDefinition> createdDataset = datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", true),
        "create dataset"
    );
    assertTrue(createdDataset.isSuccess());
    Try<LowcodeChartDefinition> createdChart = chartService.create(
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales-overview",
            "demo.sales",
            true
        ),
        "create chart"
    );
    assertTrue(createdChart.isSuccess());
    AssetReferenceGraph graph = new FileBackedAssetReferenceGraphService(vcsManagerCenter).resolve("demo-app");

    assertNode(graph, "dataset", "demo.sales", false);
    assertNode(graph, "chart", "demo.sales-overview", false);
    assertEdge(graph, "dataset", "demo.sales", "chart", "demo.sales-overview", "uses-dataset", false);
    assertEquals(0, graph.getDangling().size());
  }

  @Test
  void resolve_shouldPreserveDanglingReferencesWithoutFixingDefinitions() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    LowcodeChartDefinition danglingChart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.orphan-view",
        "demo.missing-dataset",
        false
    );
    writeDefinition(CHART_DIRECTORY, "demo.orphan-view", danglingChart);

    AssetReferenceGraph graph = new FileBackedAssetReferenceGraphService(vcsManagerCenter).resolve("demo-app");

    assertNode(graph, "dataset", "demo.missing-dataset", true);
    assertEdge(graph, "dataset", "demo.missing-dataset", "chart", "demo.orphan-view", "uses-dataset", true);
    assertEquals(1, graph.getDangling().size());
    assertEquals("demo.missing-dataset", danglingChart.getDatasetName());
  }

  @Test
  void resolve_shouldRecognizeReferencesIgnoringCase() {
    DevAppHolder.setAppName("demo-app");
    var manager = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    writeDefinition("statistics-reporting/datasets", "demo.sales.dataset",
        StatisticsReportingDevTestSupport.minimalDataset("demo.Sales", true));
    writeDefinition(CHART_DIRECTORY, "demo.chart.chart",
        StatisticsReportingDevTestSupport.minimalChart("demo.chart", "DEMO.sales", true));
    var graph = new FileBackedAssetReferenceGraphService(manager).resolve("demo-app");
    assertEquals(2, graph.getNodes().size());
    assertTrue(graph.getDangling().isEmpty());
    assertEquals(1, new FileBackedDatasetReferenceResolver(manager).resolve("DEMO.SALES", false).size());
  }

  @Test
  void resolve_shouldReportMalformedDefinitionsInsteadOfHidingTheirReferences() throws Exception {
    DevAppHolder.setAppName("demo-app");
    var manager = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    var file = workspace.resolve(CHART_DIRECTORY).resolve("bad.chart.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{invalid}");
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.exception.PlatformException.class,
        () -> new FileBackedAssetReferenceGraphService(manager).resolve("demo-app"));
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.exception.PlatformException.class,
        () -> new FileBackedDatasetReferenceResolver(manager).resolve("demo.sales", false));
  }

  private void writeDefinition(String directory, String name, Object definition) {
    Path definitionPath = workspace.resolve(Paths.get(directory, name + ".json"));
    try {
      Files.createDirectories(definitionPath.getParent());
      Files.write(definitionPath, Json.toPrettyJsonString(definition).getBytes(StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new AssertionError("failed to write definition fixture: " + definitionPath, error);
    }
  }

  private void assertNode(AssetReferenceGraph graph, String type, String name, boolean dangling) {
    assertTrue(graph.getNodes().stream()
        .anyMatch(node -> type.equals(node.getType())
            && name.equals(node.getName())
            && dangling == node.isDangling()));
  }

  private void assertEdge(AssetReferenceGraph graph,
                          String fromType,
                          String fromName,
                          String toType,
                          String toName,
                          String relation,
                          boolean dangling) {
    assertTrue(graph.getEdges().stream()
        .anyMatch(edge -> fromType.equals(edge.getFromType())
            && fromName.equals(edge.getFromName())
            && toType.equals(edge.getToType())
            && toName.equals(edge.getToName())
            && relation.equals(edge.getRelation())
            && dangling == edge.isDangling()));
  }

  private static class TestableDatasetDevService extends DatasetDevService {
    private TestableDatasetDevService(VCSManagerCenter vcsManagerCenter) {
      super(new FileBackedDatasetReferenceResolver(vcsManagerCenter));
      this.vcsManagerCenter = vcsManagerCenter;
    }

    private TestableDatasetDevService withNoopPublisher() {
      this.publisher = StatisticsReportingDevTestSupport.noopPublisher();
      return this;
    }
  }

  private static class TestableChartDevService extends ChartDevService {
    private TestableChartDevService(DatasetDevService datasetDevService) {
      super(datasetDevService);
    }

    private TestableChartDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private TestableChartDevService withNoopPublisher() {
      this.publisher = StatisticsReportingDevTestSupport.noopPublisher();
      return this;
    }
  }

}
