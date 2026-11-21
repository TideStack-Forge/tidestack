package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.ouroboros.AppContext;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

public class RuntimeOverrideChartDefinitionProvider extends AbstractJsonDirectoryDefinitionProvider<ChartDefinition>
    implements ChartDefinitionProvider {
  static final int ORDER = 200;
  private static final String CHART_DIRECTORY = "data/statistics-reporting/charts";
  private static final String CHART_FILE_EXTENSION = ".chart.json";

  public RuntimeOverrideChartDefinitionProvider(AppContext appContext) {
    this(Paths.get(Objects.requireNonNull(appContext, "appContext is required").getHomePath()));
  }

  public RuntimeOverrideChartDefinitionProvider(Path appHome) {
    super(ChartDefinition.class, Collections.singletonList(
        Objects.requireNonNull(appHome, "appHome is required").resolve(CHART_DIRECTORY)));
  }

  @Override
  public List<ChartDefinition> getDefinitions() {
    return loadDefinitions();
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldInclude(ChartDefinition definition) {
    return definition != null;
  }

  @Override
  protected boolean isDefinitionFile(Path path) {
    return path.getFileName().toString().endsWith(CHART_FILE_EXTENSION);
  }

  @Override
  protected String getDefinitionName(ChartDefinition definition) {
    return definition == null ? null : definition.getName();
  }
}
