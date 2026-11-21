package com.ouroboros.devhelper.metadata.collector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import io.vavr.control.Either;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;

class FieldTypeDefinitionMetadataCollectorTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { contextBridge.destroy(); }

  @Test
  void getAllMetadata_readsFromFieldTypeCatalog() throws Exception {
    var catalog = new RecordingFieldTypeCatalog();
    var rawField = new LinkedHashMap<String, Object>();
    rawField.put("name", "测试字段");
    rawField.put("label", "测试字段");
    catalog.metadata = new FieldTypeDefinitionReader()
        .read(rawField, FieldTypeDefinitionSource.DEFAULT, "test")
        .get();
    bindFieldTypeCatalog(catalog);

    var collector = new FieldTypeDefinitionMetadataCollector();
    var metadata = collector.getAllMetadata();

    assertEquals(1, metadata.size());
    assertEquals("测试字段", metadata.get(0).getName());
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastListContext.getScene());
  }

  private static void bindFieldTypeCatalog(FieldTypeCatalog fieldTypeCatalog) throws Exception {
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", fieldTypeCatalog);
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static class RecordingFieldTypeCatalog implements FieldTypeCatalog {
    private FieldTypeCatalogContext lastListContext;
    private FieldTypeDefinition metadata;

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
    }

    @Override
    public java.util.List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      this.lastListContext = context;
      return Collections.singletonList(metadata);
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      return Either.left(new FieldTypeCompileException("not used", name, null, null, "test"));
    }
  }
}
