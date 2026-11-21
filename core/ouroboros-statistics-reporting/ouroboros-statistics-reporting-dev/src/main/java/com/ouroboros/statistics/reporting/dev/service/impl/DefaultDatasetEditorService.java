package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetEditorShell;
import com.ouroboros.statistics.reporting.dev.service.DatasetEditorService;
import com.ouroboros.web.SelectOption;

@Service
public class DefaultDatasetEditorService implements DatasetEditorService {
  private final AppDataSourceService appDataSourceService;
  private final DatasetDevService datasetDevService;

  public DefaultDatasetEditorService(AppDataSourceService appDataSourceService,
                                     DatasetDevService datasetDevService) {
    this.appDataSourceService = Objects.requireNonNull(appDataSourceService, "appDataSourceService is required");
    this.datasetDevService = Objects.requireNonNull(datasetDevService, "datasetDevService is required");
  }

  @Override
  public Optional<DatasetEditorShell> loadShell(String appName, String datasetName) {
    if (isBlank(appName) || isBlank(datasetName)) {
      return Optional.empty();
    }
    Optional<LowcodeDatasetDefinition> dataset = withAppName(
        appName,
        () -> datasetDevService.getOne(datasetName)
    );
    if (!dataset.isPresent()) {
      return Optional.empty();
    }

    DatasetEditorShell shell = new DatasetEditorShell();
    shell.setDataset(dataset.get());
    shell.setDatasourceOptions(datasourceOptions(appName));
    return Optional.of(shell);
  }

  private List<SelectOption> datasourceOptions(String appName) {
    java.util.Map<String, SelectOption> options = new java.util.LinkedHashMap<>();
    options.put("default", new SelectOption("default", "默认数据源"));
    safeList(appDataSourceService.getByAppName(appName)).forEach(source ->
        options.putIfAbsent(source.getDataStationName(), toDatasourceOption(source)));
    return List.copyOf(options.values());
  }

  private SelectOption toDatasourceOption(AppDataSourceModel dataSourceModel) {
    SelectOption option = new SelectOption(dataSourceModel.getDataStationName(), dataSourceModel.getDataStationName());
    option.addExtraProp("platformDataSourceName", dataSourceModel.getPlatformDataSourceName());
    return option;
  }

  private <T> T withAppName(String appName, Supplier<T> supplier) {
    String previousAppName = DevAppHolder.getAppName();
    DevAppHolder.setAppName(appName);
    try {
      return supplier.get();
    } finally {
      if (previousAppName == null) {
        DevAppHolder.removeAppName();
      } else {
        DevAppHolder.setAppName(previousAppName);
      }
    }
  }

  private <T> List<T> safeList(List<T> values) {
    return values == null ? Collections.<T>emptyList() : values;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
