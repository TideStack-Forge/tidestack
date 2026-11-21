package com.ouroboros.statistics.reporting.dev.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.util.Json;

public class WorkspaceDatasetDefinitionProvider implements DatasetDefinitionProvider {
  private static final String DATASET_DIRECTORY = "statistics-reporting/datasets";
  private static final String DATASET_FILE_EXTENSION = ".dataset.json";

  private final Path rootPath;
  private final boolean scanAppDirectories;
  private final boolean enabledOnly;

  public WorkspaceDatasetDefinitionProvider(DevelopWorkspaceContext developWorkspaceContext) {
    this(Paths.get(developWorkspaceContext.getDevRootDir()), true, true);
  }

  public WorkspaceDatasetDefinitionProvider(DevelopWorkspaceContext developWorkspaceContext, boolean enabledOnly) {
    this(Paths.get(developWorkspaceContext.getDevRootDir()), true, enabledOnly);
  }

  public WorkspaceDatasetDefinitionProvider(Path rootPath) {
    this(rootPath, false, true);
  }

  public WorkspaceDatasetDefinitionProvider(Path rootPath, boolean enabledOnly) {
    this(rootPath, false, enabledOnly);
  }

  private WorkspaceDatasetDefinitionProvider(Path rootPath, boolean scanAppDirectories, boolean enabledOnly) {
    this.rootPath = rootPath;
    this.scanAppDirectories = scanAppDirectories;
    this.enabledOnly = enabledOnly;
  }

  @Override
  public List<DatasetDefinition> getDefinitions() {
    List<DatasetDefinition> definitions = new ArrayList<DatasetDefinition>();
    for (Path appHome : appHomes()) {
      Path datasetRoot = appHome.resolve(DATASET_DIRECTORY);
      if (!Files.isDirectory(datasetRoot)) {
        continue;
      }
      try (var stream = Files.walk(datasetRoot)) {
        stream.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(DATASET_FILE_EXTENSION))
            .sorted(Comparator.comparing(Path::toString))
            .forEach(path -> readDefinition(path).ifPresent(definitions::add));
      } catch (IOException | UncheckedIOException walkFailure) {
        throw new InternalException("Failed to scan workspace datasets: " + datasetRoot, walkFailure);
      }
    }
    return definitions;
  }

  private List<Path> appHomes() {
    if (!scanAppDirectories) {
      return Collections.singletonList(rootPath);
    }
    if (!Files.isDirectory(rootPath)) {
      return Collections.emptyList();
    }
    try (var stream = Files.list(rootPath)) {
      return stream.filter(Files::isDirectory)
          .sorted(Comparator.comparing(Path::toString))
          .collect(Collectors.toList());
    } catch (IOException | UncheckedIOException listFailure) {
      throw new InternalException("Failed to list workspace applications: " + rootPath, listFailure);
    }
  }

  private Optional<DatasetDefinition> readDefinition(Path path) {
    String content;
    try {
      content = Files.readString(path);
    } catch (IOException error) {
      throw new InternalException("Failed to read workspace dataset: " + path, error);
    }
    DatasetDefinition definition = Json.tryToStrictBean(DatasetDefinition.class, content)
        .getOrElseThrow(error -> new PlatformException("Failed to parse workspace dataset: " + path, error));
    if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
      throw new PlatformException("Workspace dataset name is required: " + path);
    }
    return Optional.of(definition)
        .filter(value -> !enabledOnly || Boolean.TRUE.equals(value.getIsEnabled()));
  }
}
