package com.ouroboros.organizationstructure.definition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.util.Json;

abstract class AbstractJsonDirectoryPositionDefinitionProvider {
  static final String POSITION_DEFINITION_SUFFIX = ".position.json";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final List<Path> definitionRoots;

  protected AbstractJsonDirectoryPositionDefinitionProvider(Collection<Path> definitionRoots) {
    this.definitionRoots = Collections.unmodifiableList(new ArrayList<Path>(
        definitionRoots == null ? Collections.<Path>emptyList() : definitionRoots));
  }

  protected List<PositionDefinition> loadDefinitions() {
    List<PositionDefinition> definitions = new ArrayList<PositionDefinition>();
    for (Path definitionRoot : definitionRoots) {
      if (definitionRoot == null || !Files.isDirectory(definitionRoot)) {
        continue;
      }
      try (Stream<Path> stream = Files.walk(definitionRoot)) {
        stream.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(POSITION_DEFINITION_SUFFIX))
            .sorted(Comparator.comparing(Path::toString))
            .forEach(path -> definitions.add(readDefinition(path)));
      } catch (Exception error) {
        throw new IllegalStateException("Failed to scan position definitions from " + definitionRoot, error);
      }
    }
    return definitions;
  }

  private PositionDefinition readDefinition(Path path) {
    try {
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      return readDefinition(content, path.toString());
    } catch (Exception error) {
      throw new IllegalStateException("Failed to load position definition file: " + path, error);
    }
  }

  protected PositionDefinition readDefinition(String content, String source) {
    PositionDefinition definition = Json.tryToBean(PositionDefinition.class, content)
        .getOrElseThrow(error -> new IllegalStateException("Failed to parse position definition file: " + source, error));
    if (definition == null || isBlank(definition.getKey()) || isBlank(definition.getName())) {
      throw new IllegalStateException("Invalid position definition: key and name are required, source=" + source);
    }
    return definition;
  }

  protected boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
