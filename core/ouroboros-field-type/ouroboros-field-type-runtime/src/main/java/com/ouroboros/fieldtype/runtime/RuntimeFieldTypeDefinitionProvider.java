package com.ouroboros.fieldtype.runtime;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.vavr.control.Try;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogException;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.util.Json;

public class RuntimeFieldTypeDefinitionProvider implements FieldTypeDefinitionProvider {
  private static final String MODEL_FILE_SUFFIX = ".field-type.json";

  private final List<FieldTypeDefinition> fieldTypeDefinitions;

  public RuntimeFieldTypeDefinitionProvider(String extendFieldsPath, FieldTypeDefinitionReader reader) {
    this(Collections.singletonList(extendFieldsPath), reader);
  }

  public RuntimeFieldTypeDefinitionProvider(List<String> extendFieldsPaths, FieldTypeDefinitionReader reader) {
    this.fieldTypeDefinitions = loadDefinitions(extendFieldsPaths, reader);
  }

  @Override
  public boolean supports(FieldTypeCatalogContext context) {
    if (context == null || context.getScene() == null) {
      return false;
    }
    return context.getScene() == FieldTypeCatalogScene.RUNTIME
        || context.getScene() == FieldTypeCatalogScene.DEV_APP;
  }

  @Override
  public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
    return fieldTypeDefinitions;
  }

  private List<FieldTypeDefinition> loadDefinitions(List<String> extendFieldsPaths, FieldTypeDefinitionReader reader) {
    Map<String, FieldTypeDefinition> definitionsByName = new TreeMap<String, FieldTypeDefinition>(String::compareToIgnoreCase);
    for (String extendFieldsPath : Optional.ofNullable(extendFieldsPaths).orElseGet(Collections::emptyList)) {
      loadDirectory(extendFieldsPath, reader)
          .forEach(definition -> definitionsByName.put(definition.getName(), definition));
    }
    return new ArrayList<FieldTypeDefinition>(definitionsByName.values());
  }

  private List<FieldTypeDefinition> loadDirectory(String extendFieldsPath, FieldTypeDefinitionReader reader) {
    var folder = new File(extendFieldsPath);
    if (!folder.isDirectory()) {
      return Collections.emptyList();
    }

    var children = folder.listFiles((dir, name) -> name != null && name.endsWith(MODEL_FILE_SUFFIX));
    if (children == null || children.length == 0) {
      return Collections.emptyList();
    }

    var definitions = new ArrayList<FieldTypeDefinition>();
    Arrays.stream(children)
        .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
        .forEach(child -> {
          if (!child.exists()) {
            return;
          }

          var content = Try.of(() -> Files.readAllLines(child.toPath()))
              .map(lines -> String.join("", lines))
              .getOrElse("");
          if (content.isEmpty()) {
            return;
          }

          var definition = reader.read(Json.toMap(content), FieldTypeDefinitionSource.RUNTIME_EXTEND, child.getAbsolutePath())
              .fold(exception -> {
                throw new FieldTypeCatalogException(exception.getMessage() + " @ " + child.getAbsolutePath());
              }, value -> value);
          definitions.add(definition);
        });
    return definitions;
  }
}
