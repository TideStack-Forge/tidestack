package com.ouroboros.fieldtype.dev.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogException;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.util.Json;

@Component
public class JarDevFieldTypeDefinitionProvider implements FieldTypeDefinitionProvider {
  private static final String DEV_FIELD_TYPES_PATH = "dev/";
  private static final String DEV_FIELD_TYPE_SUFFIX = ".field-type.json";

  private final FieldTypeDefinitionReader reader;
  private final AtomicReference<List<FieldTypeDefinition>> cachedDefinitions = new AtomicReference<List<FieldTypeDefinition>>();

  public JarDevFieldTypeDefinitionProvider(FieldTypeDefinitionReader reader) {
    this.reader = reader;
  }

  @Override
  public boolean supports(FieldTypeCatalogContext context) {
    return context != null && context.getScene() == FieldTypeCatalogScene.DEV_APP;
  }

  @Override
  public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
    List<FieldTypeDefinition> definitions = cachedDefinitions.get();
    if (definitions != null) {
      return definitions;
    }
    var spec = TypedMetadataSpec.<FieldTypeDefinition>builder(FieldTypeDefinition.class)
        .typedSuffix(DEV_FIELD_TYPE_SUFFIX)
        .resourceNamePredicate(resourceName -> resourceName.contains(DEV_FIELD_TYPES_PATH))
        .resourceParser(resource -> Try.of(() -> readDefinition(resource)))
        .keyExtractor(FieldTypeDefinition::getName)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .blankKeyPolicy(TypedMetadataSpec.BlankKeyPolicy.ERROR)
        .build();
    Map<String, FieldTypeDefinition> definitionsByName = new TypedMetadataCatalog(new ClasspathMetadataSource())
        .load(spec)
        .values();
    List<FieldTypeDefinition> loaded = Collections.unmodifiableList(new ArrayList<FieldTypeDefinition>(definitionsByName.values()));
    cachedDefinitions.compareAndSet(null, loaded);
    return cachedDefinitions.get();
  }

  private FieldTypeDefinition readDefinition(MetadataResource resource) {
    var sourceLocation = resource.resourceName();
    return reader.read(Json.toMap(resource.content()), FieldTypeDefinitionSource.DEV_BUILTIN, sourceLocation)
        .fold(exception -> {
          throw new FieldTypeCatalogException(exception.getMessage() + " @ " + sourceLocation);
        }, value -> value);
  }
}
