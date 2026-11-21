package com.ouroboros.statistics.reporting.dev;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration;
import com.ouroboros.statistics.reporting.runtime.controller.DatasetExecutionController;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.web.WebApiResult;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatisticsReportingGlobalRuntimeIsolationTest {
  @TempDir
  Path workspace;

  @Test
  void shouldNotExposeWorkspaceDefinitionsToGlobalRuntimeController() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, StatisticsReportingDevTestSupport.minimalDataset("demo.sales", true));

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(StatisticsReportingRuntimeAutoConfiguration.class, StatisticsReportingDevAutoConfiguration.class);
      context.registerBean(AppHomeResolver.class, () -> (AppHomeResolver) appId -> workspace.resolve(appId).toString());
      context.registerBean(DevelopWorkspaceContext.class,
          () -> StatisticsReportingDevTestSupport.workspaceContext(workspace));
      context.registerBean(AppContext.class, () -> (AppContext) () -> workspace.resolve("runtime-app-home").toString());
      context.registerBean(AppDataSourceService.class, EmptyAppDataSourceService::new);
      context.registerBean(PlatformDataSourceService.class, () -> org.mockito.Mockito.mock(PlatformDataSourceService.class));
      context.registerBean(VCSManagerCenter.class, VCSManagerCenter::new);
      context.refresh();

      DatasetExecutionController controller = context.getBean(DatasetExecutionController.class);
      DatasetExecutionRequest request = new DatasetExecutionRequest();
      request.setDatasetName("demo.sales");

      PlatformException error = assertThrows(PlatformException.class, () -> controller.execute(request.getDatasetName(), request));

      assertEquals("dataset definition not found: demo.sales", error.getMessage());
    } finally {
      context.close();
    }
  }

  private void writeDataset(Path appHome, LowcodeDatasetDefinition dataset) throws Exception {
    Path file = appHome.resolve("statistics-reporting/datasets/" + dataset.getName() + ".dataset.json");
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(dataset).getBytes(StandardCharsets.UTF_8));
  }

  private static class EmptyAppDataSourceService implements AppDataSourceService {
    @Override
    public Try<AppDataSourceModel> add(AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException("not needed in test"));
    }

    @Override
    public Try<Boolean> delete(String primaryKey) {
      return Try.failure(new UnsupportedOperationException("not needed in test"));
    }

    @Override
    public Try<Boolean> update(String primaryKey, AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException("not needed in test"));
    }

    @Override
    public Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey) {
      return Optional.empty();
    }

    @Override
    public List<AppDataSourceModel> getByAppName(String appName) {
      return Collections.emptyList();
    }

    @Override
    public Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String appDataSourceName) {
      return Optional.empty();
    }

    @Override
    public Long count(Map<String, Object> where) {
      return 0L;
    }

    @Override
    public List<AppDataSourceModel> query(Map<String, Object> statement) {
      return Collections.emptyList();
    }

    @Override
    public List<AppDataSourceModel> getAll() {
      return Collections.emptyList();
    }

    @Override
    public List<TableInfo> getTableInfo(String appName, String dataStationName, String envName) {
      return Collections.emptyList();
    }
  }
}
