package com.ouroboros.statistics.reporting.dev.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.ChartDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.FileBackedDatasetReferenceResolver;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.vcs.VCSManagerCenter;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetDevServiceTest {
  @TempDir
  Path workspace;

  @AfterEach
  void cleanup() {
    StatisticsReportingDevTestSupport.clearTestContext();
  }

  @Test
  void shouldSearchByNameOrTitleAndResolveTheSameIdentityIgnoringCase() {
    DevAppHolder.setAppName("demo-app");
    var manager = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    var service = new TestableDatasetDevService(manager).withNoopPublisher();
    var model = StatisticsReportingDevTestSupport.minimalDataset("demo.Sales", false);
    assertTrue(service.create(model, "create dataset").isSuccess());
    org.junit.jupiter.api.Assertions.assertEquals(1, service.search(java.util.Map.of("keyword", "SALES")).size());
    org.junit.jupiter.api.Assertions.assertEquals(1, service.search(java.util.Map.of("keyword", "dataset")).size());
    assertTrue(service.search(java.util.Map.of("keyword", "missing")).isEmpty());
    assertTrue(service.getOne("DEMO.sales").isPresent());
    assertTrue(service.create(StatisticsReportingDevTestSupport.minimalDataset("DEMO.SALES", false), "duplicate").isFailure());
  }

  @Test
  void shouldPersistDatasetJsonUnderWorkspaceDirectory() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    TestableDatasetDevService service = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();

    Try<LowcodeDatasetDefinition> result = service.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create dataset"
    );

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(workspace.resolve("statistics-reporting/datasets/demo.sales.dataset.json")));
  }

  @Test
  void shouldRejectDatasetWithoutSchema() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);
    TestableDatasetDevService service = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();

    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.invalid",
        false
    );
    dataset.setSchema(null);

    Try<LowcodeDatasetDefinition> result = service.create(dataset, "invalid dataset");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("schema"));
  }

  @Test
  void shouldRejectDeletingDraftDatasetWhileDraftViewStillReferencesIt() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    TestableChartDevService chartService = new TestableChartDevService(datasetService)
        .withVcsManagerCenter(vcsManagerCenter)
        .withNoopPublisher();

    Try<LowcodeDatasetDefinition> created = datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create draft dataset"
    );
    assertTrue(created.isSuccess());

    Try<LowcodeChartDefinition> createdChart = chartService.create(
        StatisticsReportingDevTestSupport.minimalChart(
            "demo.sales.overview",
            "demo.sales",
            false
        ),
        "create draft chart"
    );
    assertTrue(createdChart.isSuccess());

    Try<LowcodeDatasetDefinition> result = datasetService.delete("demo.sales", "delete draft dataset");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("charts still reference dataset"));
  }

  @Test
  void shouldRejectRenamingDatasetThroughUpdate() {
    DevAppHolder.setAppName("demo-app");
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(workspace);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();

    Try<LowcodeDatasetDefinition> created = datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false),
        "create draft dataset"
    );
    assertTrue(created.isSuccess());

    Try<LowcodeDatasetDefinition> result = datasetService.update(
        "demo.sales",
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales.v2", false)
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
