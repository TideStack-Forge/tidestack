package com.ouroboros.statistics.reporting.dev.support;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.vcs.LocalGitVCSManager;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.lock.LocalFileLockManager;

public final class StatisticsReportingDevTestSupport {
  private static final String TEST_VCS_USER = "statistics-dev-test";

  private StatisticsReportingDevTestSupport() {
  }

  public static void clearTestContext() {
    DevAppHolder.removeAppName();
    CurrentSecurityContextHolder.clearDelegate();
  }

  public static ApplicationEventPublisher noopPublisher() {
    return event -> {
    };
  }

  public static AppHomeResolver fixedAppHomeResolver(Path appHome) {
    return appName -> appHome.toString();
  }

  public static DevelopWorkspaceContext workspaceContext(Path devRoot) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getDevRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getRunRootDir() {
        return devRoot.resolve("run").toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return devRoot.resolve("debug-support").toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return devRoot.resolve("develop-support").toString();
      }
    };
  }

  public static VCSManagerCenter vcsManagerCenter(Path repositoryPath) {
    return new FixedVCSManagerCenter(vcsManager(repositoryPath));
  }

  public static VCSManager vcsManager(Path repositoryPath) {
    CurrentSecurityContextHolder.setDelegate(
        () -> Optional.of(new DefaultSecurityIdentity(() -> TEST_VCS_USER))
    );
    return new LocalGitVCSManager(
        new VCSConfig(repositoryPath.toString()),
        new LocalFileLockManager(repositoryPath.toString() + ".lock")
    );
  }

  public static LowcodeDatasetDefinition minimalDataset(String name, boolean isEnabled) {
    LowcodeDatasetDefinition dataset = new LowcodeDatasetDefinition();
    dataset.setName(name);
    dataset.setTitle("Sales Dataset");
    dataset.setIsEnabled(Boolean.valueOf(isEnabled));
    dataset.setQueryDefinition(sqlQuery("select ${keyword} as id, 'Phone' as name, 'East' as region, 1 as total"));
    dataset.setParameters(Collections.singletonList(parameter("keyword")));
    dataset.setSchema(schema(
        field("id", "ID", "detail", false, false),
        field("name", "Name", "detail", true, true),
        field("region", "Region", "dimension", true, true),
        field("total", "Total", "metric", false, true)
    ));
    return dataset;
  }

  public static LowcodeChartDefinition minimalChart(String name,
                                                     String datasetName,
                                                     boolean isEnabled) {
    LowcodeChartDefinition chart = new LowcodeChartDefinition();
    chart.setName(name);
    chart.setDatasetName(datasetName);
    chart.setTitle("Overview");
    chart.setIsEnabled(Boolean.valueOf(isEnabled));
    chart.setChartType("metric-card");
    chart.setParams(Collections.<String, Object>singletonMap(
        "valueFields", Collections.singletonList("total")));
    return chart;
  }

  private static SqlQueryDefinition sqlQuery(String sql) {
    SqlQueryDefinition queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql(sql);
    return queryDefinition;
  }

  private static ParameterDefinition parameter(String name) {
    ParameterDefinition parameter = new ParameterDefinition();
    parameter.setName(name);
    parameter.setTitle("Keyword");
    parameter.setType("string");
    return parameter;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name,
                                              String title,
                                              String semanticRole,
                                              boolean visibleByDefault,
                                              boolean exportable) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(title);
    field.setDataType("string");
    field.setSemanticRole(semanticRole);
    field.setVisibleByDefault(Boolean.valueOf(visibleByDefault));
    field.setExportable(Boolean.valueOf(exportable));
    return field;
  }

  private static class FixedVCSManagerCenter extends VCSManagerCenter {
    private final VCSManager vcsManager;

    private FixedVCSManagerCenter(VCSManager vcsManager) {
      this.vcsManager = vcsManager;
    }

    @Override
    public VCSManager getVcsManager(String appName) {
      return vcsManager;
    }
  }
}
