package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.ouroboros.AppContext;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;

public class AppDefaultDatasetDefinitionProvider extends AbstractJsonDirectoryDefinitionProvider<DatasetDefinition>
    implements DatasetDefinitionProvider {
  static final int ORDER = 100;
  private static final String DATASET_DIRECTORY = "statistics-reporting/datasets";
  private static final String DATASET_FILE_EXTENSION = ".dataset.json";

  public AppDefaultDatasetDefinitionProvider(AppContext appContext) {
    this(Paths.get(Objects.requireNonNull(appContext, "appContext is required").getHomePath()));
  }

  public AppDefaultDatasetDefinitionProvider(Path appHome) {
    super(DatasetDefinition.class, Collections.singletonList(
        Objects.requireNonNull(appHome, "appHome is required").resolve(DATASET_DIRECTORY)));
  }

  @Override
  public List<DatasetDefinition> getDefinitions() {
    return loadDefinitions();
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldInclude(DatasetDefinition definition) {
    return definition != null;
  }

  @Override
  protected boolean isDefinitionFile(Path path) {
    return path.getFileName().toString().endsWith(DATASET_FILE_EXTENSION);
  }

  @Override
  protected String getDefinitionName(DatasetDefinition definition) {
    return definition == null ? null : definition.getName();
  }
}
