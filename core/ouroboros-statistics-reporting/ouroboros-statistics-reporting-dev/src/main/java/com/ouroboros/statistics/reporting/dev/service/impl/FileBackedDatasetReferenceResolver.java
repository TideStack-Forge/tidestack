package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;


import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetReferenceSummary;
import com.ouroboros.statistics.reporting.dev.service.DatasetReferenceResolver;
import com.ouroboros.statistics.reporting.validation.ChartParameterRules;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
public class FileBackedDatasetReferenceResolver implements DatasetReferenceResolver {
  private static final String CHART_DIRECTORY = "statistics-reporting/charts";
  private static final String CHART_FILE_EXTENSION = ".chart.json";

  private final VCSManagerCenter vcsManagerCenter;

  public FileBackedDatasetReferenceResolver(VCSManagerCenter vcsManagerCenter) {
    this.vcsManagerCenter = vcsManagerCenter;
  }

  @Override
  public List<DatasetReferenceSummary> resolve(String datasetName, boolean enabledOnly) {
    if (isBlank(datasetName) || isBlank(DevAppHolder.getAppName())) {
      return Collections.emptyList();
    }
    List<DatasetReferenceSummary> references = new ArrayList<DatasetReferenceSummary>();
    references.addAll(loadCharts(datasetName, CHART_DIRECTORY, CHART_FILE_EXTENSION, enabledOnly));
    return references;
  }

  private List<DatasetReferenceSummary> loadCharts(String datasetName,
                                                  String directory,
                                                  String fileExtension,
                                                  boolean enabledOnly) {
    return vcsManagerCenter.getVcsManager(DevAppHolder.getAppName()).walkDirectory(directory, fileExtension)
        .map(file -> Json.tryToStrictBean(ChartDefinition.class, file.getContent()).getOrElseThrow(error ->
            new com.ouroboros.exception.PlatformException("Failed to read chart references: " + file.getPath(), error)))
        .filter(chart -> datasetName.equalsIgnoreCase(chart.getDatasetName()))
        .filter(chart -> !enabledOnly || Boolean.TRUE.equals(chart.getIsEnabled()))
        .map(this::toReferenceSummary)
        .collect(Collectors.toList());
  }

  private DatasetReferenceSummary toReferenceSummary(ChartDefinition chart) {
    DatasetReferenceSummary summary = new DatasetReferenceSummary();
    summary.setName(chart.getName());
    summary.setTitle(chart.getTitle());
    summary.setDatasetName(chart.getDatasetName());
    summary.setIsEnabled(chart.getIsEnabled());
    summary.setChartType(chart.getChartType());
    summary.setReferencedFields(ChartParameterRules.referencedFields(chart));
    summary.setChartDefinition(chart);
    return summary;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
