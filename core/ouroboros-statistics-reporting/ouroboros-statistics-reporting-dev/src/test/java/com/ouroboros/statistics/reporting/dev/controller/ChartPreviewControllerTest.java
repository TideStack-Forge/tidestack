package com.ouroboros.statistics.reporting.dev.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.statistics.reporting.runtime.chart.CartesianChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;
import com.ouroboros.statistics.reporting.runtime.chart.ChartOptionBuilderRegistry;
import com.ouroboros.statistics.reporting.runtime.chart.ChartParameterParser;
import com.ouroboros.statistics.reporting.runtime.chart.DataChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.PieChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.SankeyChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.ScatterChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChartPreviewControllerTest {
  @TempDir
  Path workspace;

  private static com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection platformConnection() {
    var connection = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    connection.setKind("platform");
    connection.setPlatformDataSourceName("preview");
    return connection;
  }

  @Test
  void shouldCompileInlineChartAgainstWorkspaceDataset() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, false);
    ChartPreviewController controller = controller();
    ChartPreviewRequest request = new ChartPreviewRequest();
    request.setChart(StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview", "demo.sales", false));
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "A-1"));

    WebApiResult<ChartExecutionResponse> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("metric-card", response.getData().getChartType());
    assertEquals("42", mapList(response.getData().getOption().get("title")).get(0).get("text"));
  }

  @Test
  void shouldPreviewComboSeriesRenderingOptions() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, false);
    LowcodeChartDefinition chart = StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview", "demo.sales", false);
    chart.setChartType("combo");
    Map<String, Object> line = new LinkedHashMap<String, Object>();
    line.put("type", "line");
    line.put("field", "total");
    line.put("smooth", Boolean.TRUE);
    line.put("stacked", Boolean.TRUE);
    Map<String, Object> bar = new LinkedHashMap<String, Object>();
    bar.put("type", "bar");
    bar.put("field", "total");
    bar.put("smooth", Boolean.TRUE);
    chart.setParams(Map.of("xField", "region", "series", List.of(line, bar)));
    ChartPreviewRequest request = new ChartPreviewRequest();
    request.setChart(chart);

    WebApiResult<ChartExecutionResponse> response = controller().preview("demo-app", request);

    assertEquals(0, response.getCode());
    List<Map<String, Object>> series = mapList(response.getData().getOption().get("series"));
    assertEquals(Boolean.TRUE, series.get(0).get("smooth"));
    assertEquals("total", series.get(0).get("stack"));
    assertFalse(series.get(1).containsKey("smooth"));
  }

  @Test
  void shouldRejectPreviewWithoutChartMetadata() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, true);

    PlatformException error = assertThrows(PlatformException.class,
        () -> controller().preview("demo-app", new ChartPreviewRequest()));

    assertEquals("chart definition is required", error.getMessage());
  }

  @Test
  void shouldIgnoreDevConfigWhenDatasetUsesAnotherDatasource() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    DatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset("demo.sales", true);
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setDatasource("current-datasource");
    writeDataset(appHome, dataset);

    DatasetDevConfigModel staleConfig = new DatasetDevConfigModel();
    staleConfig.setDatasource("previous-datasource");
    staleConfig.setParameters(Collections.<String, Object>singletonMap("stale", "value"));
    staleConfig.setTestConnection(platformConnection());
    CapturingDatasetExecutor executor = new CapturingDatasetExecutor();
    ChartPreviewController controller = controller(
        executor,
        new StaticDatasetDevConfigService(staleConfig)
    );
    ChartPreviewRequest request = new ChartPreviewRequest();
    request.setChart(StatisticsReportingDevTestSupport.minimalChart(
        "demo.sales.overview", "demo.sales", false));

    WebApiResult<ChartExecutionResponse> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertFalse(executor.request.getExecutionContext() != null
        && executor.request.getExecutionContext().containsKey("jdbcConnection"));
    assertFalse(executor.request.getRuntimeParameters() != null
        && executor.request.getRuntimeParameters().containsKey("stale"));
  }

  @Test
  void shouldLoadSavedChartFromWorkspaceForDesignTimePreview() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, false);
    String chartName = "demo.sales.saved-chart";
    Path chartFile = appHome.resolve("statistics-reporting/charts/" + chartName + ".chart.json");
    Files.createDirectories(chartFile.getParent());
    Files.write(chartFile, Json.toPrettyJsonString(
        StatisticsReportingDevTestSupport.minimalChart(chartName, "demo.sales", false))
        .getBytes(StandardCharsets.UTF_8));

    WebApiResult<ChartExecutionResponse> response = controller()
        .previewSavedChart("demo-app", chartName, null);

    assertEquals(0, response.getCode());
    assertEquals("metric-card", response.getData().getChartType());
    assertEquals("42", mapList(response.getData().getOption().get("title")).get(0).get("text"));
  }

  private ChartPreviewController controller() {
    return controller(new StubDatasetExecutor(), new EmptyDatasetDevConfigService());
  }

  private ChartPreviewController controller(DatasetExecutor executor,
                                            DatasetDevConfigService datasetDevConfigService) {
    return new ChartPreviewController(
        appName -> workspace.resolve(appName).toString(),
        Collections.singletonList(executor),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        datasetDevConfigService,
        compiler()
    );
  }

  private ChartCompiler compiler() {
    return new ChartCompiler(
        new ChartDefinitionValidator(),
        new ChartParameterParser(),
        new ChartProjector(),
        new ChartOptionBuilderRegistry(Arrays.asList(
            new CartesianChartOptionBuilder(),
            new PieChartOptionBuilder(),
            new ScatterChartOptionBuilder(),
            new SankeyChartOptionBuilder(),
            new DataChartOptionBuilder()
        ))
    );
  }

  private void writeDataset(Path appHome, boolean isEnabled) throws Exception {
    writeDataset(appHome, StatisticsReportingDevTestSupport.minimalDataset("demo.sales", isEnabled));
  }

  private void writeDataset(Path appHome, DatasetDefinition dataset) throws Exception {
    Path file = appHome.resolve("statistics-reporting/datasets/demo.sales.dataset.json");
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(dataset).getBytes(StandardCharsets.UTF_8));
  }

  private static List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?> source)) {
      throw new AssertionError("expected list but was: " + value);
    }
    List<Map<String, Object>> result = new java.util.ArrayList<Map<String, Object>>();
    for (Object item : source) {
      if (!(item instanceof Map<?, ?> sourceMap)) {
        throw new AssertionError("expected map but was: " + item);
      }
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new AssertionError("expected string key but was: " + entry.getKey());
        }
        map.put(key, entry.getValue());
      }
      result.add(map);
    }
    return result;
  }

  private static class StubDatasetExecutor implements DatasetExecutor {
    @Override
    public boolean supports(DatasetDefinition dataset) {
      return true;
    }

    @Override
    public DatasetResult execute(DatasetDefinition dataset, DatasetExecutionRequest request) {
      DatasetResult result = new DatasetResult();
      result.setResolvedSchema(dataset.getSchema());
      result.setRows(Collections.singletonList(Collections.<String, Object>singletonMap(
          "total", Integer.valueOf(42))));
      return result;
    }
  }

  private static class CapturingDatasetExecutor extends StubDatasetExecutor {
    private DatasetExecutionRequest request;

    @Override
    public DatasetResult execute(DatasetDefinition dataset, DatasetExecutionRequest request) {
      this.request = request.copy();
      return super.execute(dataset, request);
    }
  }

  private static class StaticDatasetDevConfigService extends EmptyDatasetDevConfigService {
    private final DatasetDevConfigModel config;

    private StaticDatasetDevConfigService(DatasetDevConfigModel config) {
      this.config = config;
    }

    @Override
    public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
      return java.util.Optional.of(config);
    }
  }

  private static class EmptyDatasetDevConfigService implements DatasetDevConfigService {
    @Override
    public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
      return io.vavr.control.Try.success(config);
    }

    @Override
    public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
      return java.util.Optional.empty();
    }

    @Override
    public List<DatasetDevConfigModel> findByAppName(String appName) {
      return Collections.emptyList();
    }
  }
}
