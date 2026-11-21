package com.ouroboros.businessmodel.projection.data.basic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import io.vavr.control.Either;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.uischema.schema.FormItemNode;

class BasicMPSTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { contextBridge.destroy(); }

  @Test
  void process_usesCompiledFieldMetadataForBusinessModelFields() throws Exception {
    var catalog = new RecordingFieldTypeCatalog();
    catalog.compiledField = compiledField("String", 64, null, null);
    bindFieldTypeCatalog(catalog);

    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(createModel("测试字段", false));

    new BasicMPS().process(context);

    var dataModel = context.getModel(DataModelMeta.class, "demo.customer");
    assertTrue(dataModel.isPresent());
    assertEquals("String", dataModel.get().getFields().get(0).getType());
    assertEquals(Integer.valueOf(64), dataModel.get().getFields().get(0).getSize());
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastCompileContext.getScene());
    assertEquals("测试字段", catalog.lastCompileName);
  }

  private static DefaultBusinessModel createModel(String fieldType, boolean primaryKey) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName(primaryKey ? "id" : "name");
    fieldMeta.setTitle(primaryKey ? "编号" : "名称");
    fieldMeta.setType(fieldType);
    fieldMeta.setIsRequired(true);
    fieldMeta.setIsPrimaryKey(primaryKey);

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户");
    modelMeta.setFields(Collections.singletonList(fieldMeta));
    modelMeta.setRelations(Collections.emptyList());

    return new DefaultBusinessModel(modelMeta, emptyProvider());
  }

  private static BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String fullName) {
        return Optional.empty();
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return Collections.emptyMap();
      }
    };
  }

  private static CompiledFieldType compiledField(String dataType, Integer size, FormItemNode input, FormItemNode display) {
    return new CompiledFieldType() {
      @Override
      public DataModelFieldMeta toDataFieldMeta() {
        var fieldMeta = new DataModelFieldMeta();
        fieldMeta.setType(dataType);
        fieldMeta.setSize(size);
        return fieldMeta;
      }

      @Override
      public FormItemNode toInputFormItem() {
        return input;
      }

      @Override
      public FormItemNode toDisplayFormItem() {
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
    private String lastCompileName;
    private CompiledFieldType compiledField;

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
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
      this.lastCompileName = name;
      return Either.right(compiledField);
    }
  }
}
