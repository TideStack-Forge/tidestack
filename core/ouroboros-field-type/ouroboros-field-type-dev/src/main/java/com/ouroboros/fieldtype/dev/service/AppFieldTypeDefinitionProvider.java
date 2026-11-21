package com.ouroboros.fieldtype.dev.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogException;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;

@Component
public class AppFieldTypeDefinitionProvider implements FieldTypeDefinitionProvider {
  private static final String MODEL_FILE_DIRECTORY = "field-types";
  private static final String MODEL_FILE_SUFFIX = ".field-type.json";

  private final VCSManagerCenter vcsManagerCenter;
  private final FieldTypeDefinitionReader reader;
  private final Map<String, List<FieldTypeDefinition>> cachedDefinitions = new ConcurrentHashMap<String, List<FieldTypeDefinition>>();

  public AppFieldTypeDefinitionProvider(VCSManagerCenter vcsManagerCenter, FieldTypeDefinitionReader reader) {
    this.vcsManagerCenter = vcsManagerCenter;
    this.reader = reader;
  }

  @Override
  public boolean supports(FieldTypeCatalogContext context) {
    return context != null
        && context.getScene() == FieldTypeCatalogScene.DEV_APP
        && context.getAppName() != null
        && !context.getAppName().trim().isEmpty();
  }

  @Override
  public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
    if (!supports(context)) {
      return Collections.emptyList();
    }

    return cachedDefinitions.computeIfAbsent(context.getAppName(), this::loadAppDefinitions);
  }

  private List<FieldTypeDefinition> loadAppDefinitions(String appName) {
    var vcsManager = vcsManagerCenter.getVcsManager(appName);
    if (vcsManager == null) {
      return Collections.emptyList();
    }

    Map<String, FieldTypeDefinition> definitionsByName = new TreeMap<String, FieldTypeDefinition>(String::compareToIgnoreCase);
    loadDirectory(vcsManager, MODEL_FILE_DIRECTORY)
        .forEach(definition -> definitionsByName.put(definition.getName(), definition));
    return Collections.unmodifiableList(new ArrayList<FieldTypeDefinition>(definitionsByName.values()));
  }

  private List<FieldTypeDefinition> loadDirectory(com.ouroboros.vcs.VCSManager vcsManager, String directory) {
    List<FieldTypeDefinition> definitions = new ArrayList<FieldTypeDefinition>();
    vcsManager.walkDirectory(directory, MODEL_FILE_SUFFIX)
        .forEach(vcsFile -> {
          var sourceLocation = vcsFile.getPath();
          var definition = reader.read(Json.toMap(vcsFile.getContent()), FieldTypeDefinitionSource.APP, sourceLocation)
              .fold(exception -> {
                throw new FieldTypeCatalogException(exception.getMessage() + " @ " + sourceLocation);
              }, value -> value);
          definitions.add(definition);
        });
    return definitions;
  }
}
