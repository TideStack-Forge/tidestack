package com.ouroboros.businessmodel.projection.uischema.prototypes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import io.vavr.control.Either;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.uischema.schema.FormItemNode;

class UiSchemaUtilsTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { contextBridge.destroy(); }

  @Test
  void buildFormItems_usesCompiledFieldFormItems() throws Exception {
    var catalog = new RecordingFieldTypeCatalog();
    catalog.compiledField = compiledField("input-number", "static-number");
    bindFieldTypeCatalog(catalog);

    var field = createField();

    var input = UiSchemaUtils.buildInputFormItem(field, "年龄");
    var display = UiSchemaUtils.buildDisplayFormItem(field, "年龄");

    assertEquals("input-number", input.getType());
    assertEquals("static-number", display.getType());
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastCompileContext.getScene());
  }

  @Test
  void fieldsValidator_usesRepositoryMetadataLookup() throws Exception {
    var catalog = new RecordingFieldTypeCatalog();
    var rawField = new LinkedHashMap<String, Object>();
    rawField.put("name", "测试字段");
    rawField.put("label", "测试字段");
    catalog.metadata = new FieldTypeDefinitionReader()
        .read(rawField, FieldTypeDefinitionSource.DEFAULT, "test")
        .get();
    bindFieldTypeCatalog(catalog);

    var context = new com.ouroboros.businessmodel.validation.ValidationContext<DefaultBusinessModel>(createModel("测试字段"));
    new com.ouroboros.businessmodel.validation.FieldsValidator().doValidate(context);

    assertTrue(context.getErrors().isEmpty());
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastMetadataContext.getScene());
  }

  private static DefaultBusinessModel createModel(String type) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName("id");
    fieldMeta.setTitle("编号");
    fieldMeta.setType(type);
    fieldMeta.setIsRequired(true);
    fieldMeta.setIsPrimaryKey(true);

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户");
    modelMeta.setFields(Collections.singletonList(fieldMeta));
    modelMeta.setRelations(Collections.emptyList());

    return new DefaultBusinessModel(modelMeta, new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String fullName) {
        return Optional.empty();
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return Collections.emptyMap();
      }
    });
  }

  private static BusinessModelField createField() {
    return createModel("测试字段").getFields().get(0);
  }

  private static CompiledFieldType compiledField(String inputType, String displayType) {
    return new CompiledFieldType() {
      @Override
      public DataModelFieldMeta toDataFieldMeta() {
        return new DataModelFieldMeta();
      }

      @Override
      public FormItemNode toInputFormItem() {
        var input = new FormItemNode();
        input.setType(inputType);
        return input;
      }

      @Override
      public FormItemNode toDisplayFormItem() {
        var display = new FormItemNode();
        display.setType(displayType);
        return display;
      }

      @Override
      public String getPrimaryKeyGenerator() {
        return null;
      }
    };
  }

  private static void bindFieldTypeCatalog(FieldTypeCatalog fieldTypeCatalog) throws Exception {
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", fieldTypeCatalog);
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static class RecordingFieldTypeCatalog implements FieldTypeCatalog {
    private FieldTypeCatalogContext lastCompileContext;
    private FieldTypeCatalogContext lastMetadataContext;
    private CompiledFieldType compiledField;
    private FieldTypeDefinition metadata;

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      this.lastMetadataContext = context;
      return Optional.ofNullable(metadata);
    }

    @Override
    public java.util.List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      this.lastCompileContext = context;
      return Either.right(compiledField);
    }
  }
}
