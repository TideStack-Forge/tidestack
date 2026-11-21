package com.ouroboros.statistics.reporting.dev.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.provider.WorkspaceChartDefinitionProvider;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.FileBackedDatasetReferenceResolver;
import com.ouroboros.statistics.reporting.dev.service.impl.ChartDevService;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.vcs.VCSManagerCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartDevServiceTest {
  @TempDir
  Path workspace;

  @AfterEach
  void cleanup() {
    StatisticsReportingDevTestSupport.clearTestContext();
  }

  @Test
  void shouldPersistChartJsonUnderWorkspaceDirectory() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    Try<LowcodeChartDefinition> result = chartService.create(
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales.overview",
            "demo.sales",
            false
        ),
        "create chart"
    );

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(workspace.resolve("statistics-reporting/charts/demo.sales.overview.chart.json")));
    assertEquals(1, chartService.search(Map.of("keyword", "SALES")).size());
    assertEquals(1, chartService.search(Map.of("keyword", "overview")).size());
    assertTrue(chartService.search(Map.of("keyword", "missing")).isEmpty());
    assertTrue(chartService.getOne("DEMO.SALES.OVERVIEW").isPresent());
  }

  @Test
  void shouldReadBackComboSeriesRenderingOptionsAfterCreateAndUpdate() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();
    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset");

    List<Map<String, Object>> originalSeries = List.of(
        Map.of("type", "line", "field", "total", "smooth", false, "stacked", false));
    LowcodeChartDefinition chart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview", "demo.sales", false);
    chart.setChartType("combo");
    chart.setParams(Map.of("xField", "region", "series", originalSeries));
    Try<LowcodeChartDefinition> created = chartService.create(chart, "create combo chart");
    assertTrue(created.isSuccess());

    List<Map<String, Object>> series = List.of(
        Map.of("type", "line", "field", "total", "smooth", true, "stacked", true),
        Map.of("type", "bar", "field", "total", "stacked", true));
    LowcodeChartDefinition updatedChart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview", "demo.sales", false);
    updatedChart.setChartType("combo");
    updatedChart.setParams(Map.of("xField", "region", "series", series));

    Try<LowcodeChartDefinition> saved = chartService.update("demo.sales.overview", updatedChart);

    assertTrue(saved.isSuccess());
    ChartDefinition loaded = new WorkspaceChartDefinitionProvider(workspace, false)
        .getDefinitions().stream()
        .filter(definition -> "demo.sales.overview".equals(definition.getName()))
        .findFirst()
        .orElseThrow();
    assertEquals("combo", loaded.getChartType());
    assertEquals(series, loaded.getParams().get("series"));
  }

  @Test
  void shouldRejectChartMetricFieldWithNonMetricRole() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    LowcodeChartDefinition chart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview",
        "demo.sales",
        false
    );
    chart.setParams(Collections.<String, Object>singletonMap(
        "valueFields", Collections.singletonList("region")));

    Try<LowcodeChartDefinition> result = chartService.create(chart, "invalid chart");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("valueFields must match roles"));
  }

  @Test
  void shouldRejectChartWithoutTypeSpecificParameters() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    LowcodeChartDefinition chart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview",
        "demo.sales",
        false
    );
    chart.setParams(Collections.emptyMap());

    Try<LowcodeChartDefinition> result = chartService.create(chart, "invalid chart");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("chart parameter is required"));
  }

  @Test
  void shouldRejectEnabledChartWhenDatasetIsDisabled() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    Try<LowcodeChartDefinition> result = chartService.create(
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales.overview",
            "demo.sales",
            true
        ),
        "create chart"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("enabled dataset"));
  }

  @Test
  void shouldRejectRenamingChartThroughUpdate() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    Try<LowcodeChartDefinition> created = chartService.create(
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales.overview",
            "demo.sales",
            false
        ),
        "create chart"
    );
    assertTrue(created.isSuccess());

    Try<LowcodeChartDefinition> result = chartService.update(
        "demo.sales.overview",
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales.summary",
            "demo.sales",
            false
        )
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("name"));
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
