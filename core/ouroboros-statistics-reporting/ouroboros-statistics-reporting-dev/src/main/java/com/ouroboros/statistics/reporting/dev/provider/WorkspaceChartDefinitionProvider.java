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
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;
import com.ouroboros.util.Json;

public class WorkspaceChartDefinitionProvider implements ChartDefinitionProvider {
  private static final String CHART_DIRECTORY = "statistics-reporting/charts";
  private static final String CHART_FILE_EXTENSION = ".chart.json";

  private final Path rootPath;
  private final boolean scanAppDirectories;
  private final boolean enabledOnly;

  public WorkspaceChartDefinitionProvider(DevelopWorkspaceContext developWorkspaceContext) {
    this(Paths.get(developWorkspaceContext.getDevRootDir()), true, true);
  }

  public WorkspaceChartDefinitionProvider(DevelopWorkspaceContext developWorkspaceContext, boolean enabledOnly) {
    this(Paths.get(developWorkspaceContext.getDevRootDir()), true, enabledOnly);
  }

  public WorkspaceChartDefinitionProvider(Path rootPath) {
    this(rootPath, false, true);
  }

  public WorkspaceChartDefinitionProvider(Path rootPath, boolean enabledOnly) {
    this(rootPath, false, enabledOnly);
  }

  private WorkspaceChartDefinitionProvider(Path rootPath, boolean scanAppDirectories, boolean enabledOnly) {
    this.rootPath = rootPath;
    this.scanAppDirectories = scanAppDirectories;
    this.enabledOnly = enabledOnly;
  }

  @Override
  public List<ChartDefinition> getDefinitions() {
    List<ChartDefinition> definitions = new ArrayList<ChartDefinition>();
    for (Path appHome : appHomes()) {
      Path chartRoot = appHome.resolve(CHART_DIRECTORY);
      if (!Files.isDirectory(chartRoot)) {
        continue;
      }
      try (var stream = Files.walk(chartRoot)) {
        stream.filter(Files::isRegularFile)
            .filter(this::isChartDefinitionFile)
            .sorted(Comparator.comparing(Path::toString))
            .forEach(path -> readDefinition(path).ifPresent(definitions::add));
      } catch (IOException | UncheckedIOException walkFailure) {
        throw new InternalException("Failed to scan workspace charts: " + chartRoot, walkFailure);
      }
    }
    return definitions;
  }

  private boolean isChartDefinitionFile(Path path) {
    return path.getFileName().toString().endsWith(CHART_FILE_EXTENSION);
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

  private Optional<ChartDefinition> readDefinition(Path path) {
    String content;
    try {
      content = Files.readString(path);
    } catch (IOException error) {
      throw new InternalException("Failed to read workspace chart: " + path, error);
    }
    ChartDefinition definition = Json.tryToStrictBean(ChartDefinition.class, content)
        .getOrElseThrow(error -> new PlatformException("Failed to parse workspace chart: " + path, error));
    if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
      throw new PlatformException("Workspace chart name is required: " + path);
    }
    return Optional.of(definition)
        .filter(value -> !enabledOnly || Boolean.TRUE.equals(value.getIsEnabled()));
  }
}
