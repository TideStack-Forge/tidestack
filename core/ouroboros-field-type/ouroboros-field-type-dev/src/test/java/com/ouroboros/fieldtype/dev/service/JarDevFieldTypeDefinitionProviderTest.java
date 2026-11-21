package com.ouroboros.fieldtype.dev.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.dev.service.JarDevFieldTypeDefinitionProvider;

class JarDevFieldTypeDefinitionProviderTest {

  @Test
  void sharedClasspathKeepsDevelopmentTypesOutOfRuntimeAndItsCache() {
    var reader = new FieldTypeDefinitionReader();
    var catalog = new DefaultFieldTypeCatalog(List.of(
        new JarFieldTypeDefinitionProvider(reader), new JarDevFieldTypeDefinitionProvider(reader)),
        new DefaultFieldTypeCompiler());
    var runtime = FieldTypeCatalogContext.runtime();
    var development = FieldTypeCatalogContext.devApp("crm");

    assertFalse(catalog.getMetadata(runtime, "builtinBeta").isPresent());
    assertEquals(FieldTypeDefinitionSource.JAR,
        catalog.getMetadata(runtime, "builtinAlpha").orElseThrow().getSource());
    assertEquals(24, catalog.getMetadata(runtime, "builtinAlpha").orElseThrow().getDefaultParams().get("size"));
    assertEquals(16, catalog.getMetadata(development, "builtinAlpha").orElseThrow().getDefaultParams().get("size"));
    assertEquals(FieldTypeDefinitionSource.DEV_BUILTIN,
        catalog.getMetadata(development, "builtinBeta").orElseThrow().getSource());
    assertFalse(catalog.getMetadata(runtime, "builtinBeta").isPresent());
    assertEquals(24, catalog.getMetadata(runtime, "builtinAlpha").orElseThrow().getDefaultParams().get("size"));
  }

  @Test
  void supports_onlyDevAppScene() {
    var provider = new JarDevFieldTypeDefinitionProvider(new FieldTypeDefinitionReader());

    assertTrue(provider.supports(FieldTypeCatalogContext.devApp("crm")));
    assertFalse(provider.supports(FieldTypeCatalogContext.runtime()));
    assertFalse(provider.supports(null));
  }

  @Test
  void load_readsDevBuiltinDefinitionsFromJarResources() {
    var provider = new JarDevFieldTypeDefinitionProvider(new FieldTypeDefinitionReader());

    List<FieldTypeDefinition> definitions = provider.load(FieldTypeCatalogContext.devApp("crm"));

    assertFalse(definitions.isEmpty());
    assertTrue(definitions.stream().allMatch(definition -> definition.getSource() == FieldTypeDefinitionSource.DEV_BUILTIN));
    var names = definitions.stream().map(FieldTypeDefinition::getName).collect(Collectors.toSet());
    assertTrue(names.contains("builtinAlpha"));
    assertTrue(names.contains("builtinBeta"));
    assertEquals(16, definitions.stream()
        .filter(definition -> "builtinAlpha".equals(definition.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("builtinAlpha should exist"))
        .getDefaultParams()
        .get("size"));
  }

  @Test
  void load_readsTypedDevMetadataResourcesOnly() {
    var provider = new JarDevFieldTypeDefinitionProvider(new FieldTypeDefinitionReader());

    FieldTypeDefinition definition = provider.load(FieldTypeCatalogContext.devApp("crm")).stream()
        .filter(item -> "legacyDevBuiltin".equals(item.getName()))
        .findFirst()
        .orElse(null);

    assertNotNull(definition);
    assertEquals(FieldTypeDefinitionSource.DEV_BUILTIN, definition.getSource());
    assertEquals(12, definition.getDefaultParams().get("size"));
  }
}
