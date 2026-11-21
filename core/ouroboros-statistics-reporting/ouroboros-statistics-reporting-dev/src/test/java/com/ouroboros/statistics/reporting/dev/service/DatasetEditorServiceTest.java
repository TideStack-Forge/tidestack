package com.ouroboros.statistics.reporting.dev.service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetEditorShell;
import com.ouroboros.statistics.reporting.dev.service.impl.DefaultDatasetEditorService;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.FileBackedDatasetReferenceResolver;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.web.SelectOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetEditorServiceTest {
  @TempDir
  Path workspace;

  @AfterEach
  void cleanup() {
    StatisticsReportingDevTestSupport.clearTestContext();
  }

  @Test
  void loadShell_shouldUseDatasetServiceAndExposeDatasourceOptions() {
    String appName = "demo-app";
    Path appHome = workspace.resolve(appName);
    DevAppHolder.setAppName(appName);
    VCSManagerCenter vcsManagerCenter = StatisticsReportingDevTestSupport.vcsManagerCenter(appHome);

    TestableDatasetDevService datasetService = new TestableDatasetDevService(vcsManagerCenter)
        .withNoopPublisher();
    Try<LowcodeDatasetDefinition> createdDataset = datasetService.create(
        StatisticsReportingDevTestSupport.minimalDataset("demo.sales", true),
        "create dataset"
    );
    assertTrue(createdDataset.isSuccess());

    DatasetEditorService editorService = new DefaultDatasetEditorService(
        appDataSourceService(appName, "reporting-main"),
        datasetService
    );

    DatasetEditorShell shell = editorService.loadShell(appName, "demo.sales").get();

    assertEquals("demo.sales", shell.getDataset().getName());
    assertFalse(shell.getDatasourceOptions().isEmpty());
    assertEquals(java.util.List.of("default", "reporting-main"), optionValues(shell.getDatasourceOptions()));
    String shellJson = com.ouroboros.util.Json.toJsonString(shell);
    assertFalse(shellJson.contains("kernelType"));
    assertFalse(shellJson.contains("sections"));
    assertFalse(shellJson.contains("references"));
  }

  private AppDataSourceService appDataSourceService(String appName, String datasource) {
    AppDataSourceModel model = new AppDataSourceModel();
    model.setAppName(appName);
    model.setDataStationName(datasource);
    model.setPlatformDataSourceName("platform-main");
    return new StubAppDataSourceService(Collections.singletonList(model));
  }

  private List<String> optionValues(List<SelectOption> options) {
    return options.stream().map(SelectOption::getValue).collect(java.util.stream.Collectors.toList());
  }

  private static class StubAppDataSourceService implements AppDataSourceService {
    private final List<AppDataSourceModel> dataSources;

    private StubAppDataSourceService(List<AppDataSourceModel> dataSources) {
      this.dataSources = dataSources;
    }

    @Override
    public Try<AppDataSourceModel> add(AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> delete(String primaryKey) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> update(String primaryKey, AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey) {
      return Optional.empty();
    }

    @Override
    public List<AppDataSourceModel> getByAppName(String appName) {
      return dataSources;
    }

    @Override
    public Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String appDataSourceName) {
      return Optional.empty();
    }

    @Override
    public Long count(Map<String, Object> where) {
      return Long.valueOf(dataSources.size());
    }

    @Override
    public List<AppDataSourceModel> query(Map<String, Object> statement) {
      return dataSources;
    }

    @Override
    public List<AppDataSourceModel> getAll() {
      return dataSources;
    }

    @Override
    public List<TableInfo> getTableInfo(String appName, String dataStationName, String envName) {
      return Collections.emptyList();
    }
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
}
