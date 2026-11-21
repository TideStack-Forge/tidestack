package com.ouroboros.statistics.reporting.runtime.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;

abstract class AbstractJsonDirectoryDefinitionProvider<T> {
  private final Class<T> definitionType;
  private final List<Path> definitionRoots;

  protected AbstractJsonDirectoryDefinitionProvider(Class<T> definitionType, Collection<Path> definitionRoots) {
    this.definitionType = Objects.requireNonNull(definitionType, "definitionType is required");
    this.definitionRoots = Collections.unmodifiableList(new ArrayList<Path>(
        definitionRoots == null ? Collections.<Path>emptyList() : definitionRoots));
  }

  protected List<T> loadDefinitions() {
    List<T> definitions = new ArrayList<T>();
    for (Path definitionRoot : definitionRoots) {
      if (definitionRoot == null || !Files.isDirectory(definitionRoot)) {
        continue;
      }
      try (Stream<Path> stream = Files.walk(definitionRoot)) {
        stream.filter(Files::isRegularFile)
            .filter(this::isDefinitionFile)
            .sorted(Comparator.comparing(Path::toString))
            .forEach(path -> readDefinition(path).ifPresent(definitions::add));
      } catch (IOException | UncheckedIOException error) {
        throw new InternalException("Failed to scan statistics-reporting definitions from " + definitionRoot, error);
      }
    }
    return definitions;
  }

  protected abstract boolean shouldInclude(T definition);

  protected abstract boolean isDefinitionFile(Path path);

  protected abstract String getDefinitionName(T definition);

  private Optional<T> readDefinition(Path path) {
    String content;
    try {
      content = Files.readString(path);
    } catch (IOException error) {
      throw new InternalException("Failed to read statistics-reporting definition file: " + path, error);
    }
    T definition = Json.tryToStrictBean(definitionType, content)
        .getOrElseThrow(error -> new PlatformException(
            "Failed to parse statistics-reporting definition file: " + path, error));
    if (definition == null || isBlank(getDefinitionName(definition))) {
      throw new PlatformException("Statistics-reporting definition name is required: " + path);
    }
    return shouldInclude(definition) ? Optional.of(definition) : Optional.empty();
  }

  protected boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
