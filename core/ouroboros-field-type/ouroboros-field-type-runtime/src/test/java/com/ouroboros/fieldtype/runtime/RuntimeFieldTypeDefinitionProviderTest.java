package com.ouroboros.fieldtype.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;

class RuntimeFieldTypeDefinitionProviderTest {

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  void load_readsRuntimeExtendFieldsAndTagsSource() throws Exception {
    var fieldFile = tempDir.resolve("自定义字段.field-type.json");
    Files.write(fieldFile, Collections.singletonList("{\"name\":\"自定义字段\",\"label\":\"自定义字段\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":64}]}"), StandardCharsets.UTF_8);

    var provider = new RuntimeFieldTypeDefinitionProvider(tempDir.toString(), new FieldTypeDefinitionReader());

    var definitions = provider.load(FieldTypeCatalogContext.runtime());
    assertEquals(1, definitions.size());
    assertEquals(FieldTypeDefinitionSource.RUNTIME_EXTEND, definitions.get(0).getSource());
    assertEquals(64, definitions.get(0).getDefaultParams().get("size"));
    assertTrue(definitions.get(0).getSourceLocation().contains("自定义字段.field-type.json"));
  }

  @Test
  void load_ignoresUntypedJsonWhenTypedSuffixRequired() throws Exception {
    Files.write(
        tempDir.resolve("自定义字段.json"),
        Collections.singletonList(
            "{\"name\":\"自定义字段\",\"label\":\"旧字段\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":32}]}"),
        StandardCharsets.UTF_8
    );
    Files.write(
        tempDir.resolve("自定义字段.field-type.json"),
        Collections.singletonList(
            "{\"name\":\"自定义字段\",\"label\":\"新字段\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":96}]}"),
        StandardCharsets.UTF_8
    );

    var provider = new RuntimeFieldTypeDefinitionProvider(tempDir.toString(), new FieldTypeDefinitionReader());

    var definitions = provider.load(FieldTypeCatalogContext.runtime());
    assertEquals(1, definitions.size());
    assertEquals("新字段", definitions.get(0).getLabel());
    assertEquals(96, definitions.get(0).getDefaultParams().get("size"));
    assertTrue(definitions.get(0).getSourceLocation().contains("自定义字段.field-type.json"));
  }

  @Test
  void supports_runtimeAndDevAppContexts() {
    var provider = new RuntimeFieldTypeDefinitionProvider("unused", new FieldTypeDefinitionReader());

    assertTrue(provider.supports(FieldTypeCatalogContext.runtime()));
    assertTrue(provider.supports(FieldTypeCatalogContext.devApp("crm")));
  }
}
