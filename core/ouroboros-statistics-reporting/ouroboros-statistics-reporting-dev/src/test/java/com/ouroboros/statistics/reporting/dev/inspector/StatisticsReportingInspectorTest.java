package com.ouroboros.statistics.reporting.dev.inspector;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsReportingInspectorTest {
  private org.springframework.context.annotation.AnnotationConfigApplicationContext context;

  @org.junit.jupiter.api.BeforeEach
  void openContext() {
    context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
    context.register(com.ouroboros.common.app.util.SpringContextUtil.class);
  }

  @org.junit.jupiter.api.AfterEach
  void closeContext() {
    context.close();
  }


  @Test
  void shouldDescribeDatasetFile() {
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    context.registerBean(DatasetInspector.class, () -> new DatasetInspector(new StubDatasetService(dataset)));
    context.refresh();

    var description = new DatasetInspector.Proxy()
        .inspect(vcsFile("statistics-reporting/datasets/demo.sales.dataset.json"))
        .orElseThrow(() -> new AssertionError("statistics description should exist"));

    assertEquals("demo.sales", description.getName());
    assertEquals("Sales Dataset", description.getTitle());
    assertEquals("dataset", description.getType());
  }

  @Test
  void shouldDescribeChartFiles() {
    LowcodeChartDefinition chart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview",
        "demo.sales",
        false
    );
    context.registerBean(ChartInspector.class, () -> new ChartInspector(new StubChartService(chart)));
    context.refresh();

    var reportDescription = new ChartInspector.Proxy()
        .inspect(vcsFile("statistics-reporting/charts/demo.sales.overview.chart.json"))
        .orElseThrow(() -> new AssertionError("statistics root dir should exist"));
    assertEquals("Overview", reportDescription.getTitle());
    assertEquals("chart", reportDescription.getType());
  }

  private static VCSFile vcsFile(String path) {
    return (VCSFile) Proxy.newProxyInstance(
        VCSFile.class.getClassLoader(),
        new Class<?>[]{VCSFile.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "exists":
              return true;
            case "getPath":
              return path;
            case "getFileName":
              return path.substring(path.lastIndexOf('/') + 1);
            case "getContent":
              return "";
            case "getInputStream":
              return null;
            case "getBytesArray":
              return new byte[0];
            case "getSize":
              return 1L;
            case "getCreationTime":
            case "getLastCommittedTime":
            case "getLastModifiedTime":
              return LocalDateTime.of(2026, 3, 15, 0, 0);
            case "getVersionState":
              return VersionState.NOTCHANGED;
            case "getLockState":
              return LockState.UNLOCKED;
            case "getLockOwner":
            case "getLockOwnerName":
            case "getRevision":
              return null;
            case "isHistorical":
              return false;
            case "lock":
            case "unlock":
            case "write":
            case "move":
            case "remove":
            case "commit":
            case "rollback":
            case "diffWith":
            case "diffWithWorkingCopy":
              return Try.failure(new UnsupportedOperationException(method.getName()));
            case "getRevisions":
              return Collections.emptyList();
            case "atRevision":
            case "atBranch":
            case "atTag":
              return proxy;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        }
    );
  }

  private static class StubDatasetService extends BaseDevelopService<LowcodeDatasetDefinition> {
    private final Map<String, LowcodeDatasetDefinition> models = new HashMap<String, LowcodeDatasetDefinition>();

    private StubDatasetService(LowcodeDatasetDefinition dataset) {
      models.put(dataset.getName(), dataset);
    }

    @Override
    protected String getModelFileDirectory() {
      return "statistics-reporting/datasets";
    }

    @Override
    protected String getModelDisplayName() {
      return "Dataset";
    }

    @Override
    protected String getFullName(LowcodeDatasetDefinition model) {
      return model.getName();
    }

    @Override
    public Optional<LowcodeDatasetDefinition> getOne(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }
  }

  private static class StubChartService extends BaseDevelopService<LowcodeChartDefinition> {
    private final Map<String, LowcodeChartDefinition> models = new HashMap<String, LowcodeChartDefinition>();

    private StubChartService(LowcodeChartDefinition chart) {
      models.put(chart.getName(), chart);
    }

    @Override
    protected String getModelFileDirectory() {
      return "statistics-reporting/charts";
    }

    @Override
    protected String getModelDisplayName() {
      return "Chart";
    }

    @Override
    protected String getFullName(LowcodeChartDefinition model) {
      return model.getName();
    }

    @Override
    public Optional<LowcodeChartDefinition> getOne(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }
  }

}
