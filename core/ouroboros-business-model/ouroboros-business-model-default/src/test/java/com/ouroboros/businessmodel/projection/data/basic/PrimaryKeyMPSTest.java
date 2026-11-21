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

class PrimaryKeyMPSTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { contextBridge.destroy(); }

  @Test
  void process_usesCompiledFieldPrimaryKeyGenerator() throws Exception {
    var catalog = new RecordingFieldTypeCatalog();
    catalog.compiledField = compiledField("coding:${name}");
    bindFieldTypeCatalog(catalog);

    var model = createModel();
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    var dataModel = new DataModelMeta();
    var field = new DataModelFieldMeta();
    field.setName("id");
    dataModel.setFields(Collections.singletonList(field));
    context.putModel(DataModelMeta.class, "demo.customer", dataModel);

    new PrimaryKeyMPS().process(context);

    var primaryField = context.getModel(DataModelMeta.class, "demo.customer").get().getFields().get(0);
    assertEquals(Boolean.TRUE, primaryField.getExtraProp(Boolean.class, "isPrimaryKey").orElse(null));
    assertEquals("coding:${name}", primaryField.getExtraProp(String.class, "primaryKeyGenerator").orElse(null));
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastCompileContext.getScene());
  }

  private static DefaultBusinessModel createModel() {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName("id");
    fieldMeta.setTitle("编号");
    fieldMeta.setType("测试主键");
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

  private static CompiledFieldType compiledField(String primaryKeyGenerator) {
    return new CompiledFieldType() {
      @Override
      public DataModelFieldMeta toDataFieldMeta() {
        return new DataModelFieldMeta();
      }

      @Override
      public FormItemNode toInputFormItem() {
        return null;
      }

      @Override
      public FormItemNode toDisplayFormItem() {
        return null;
      }

      @Override
      public String getPrimaryKeyGenerator() {
        return primaryKeyGenerator;
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
      return Either.right(compiledField);
    }
  }
}
