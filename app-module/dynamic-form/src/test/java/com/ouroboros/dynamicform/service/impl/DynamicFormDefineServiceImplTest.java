package com.ouroboros.dynamicform.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.exception.InternalException;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.uischema.schema.FormItemNode;

class DynamicFormDefineServiceImplTest {

  @Test
  void buildFields_compilesFieldTypeThroughCatalog() {
    var service = new DynamicFormDefineServiceImpl(null, new StubFieldTypeCatalog());
    var form = new DynamicForm();
    var field = new FormField();
    field.setName("title");
    field.setLabel("标题");
    field.setType("单行文本");
    field.setParams(Collections.singletonMap("size", 64));
    form.setFields(Collections.singletonList(field));

    var fields = service.buildFields(form);

    assertEquals("title", fields.get(1).getName());
    assertEquals("标题", fields.get(1).getLabel());
    assertEquals("String", fields.get(1).getType());
    assertEquals(Integer.valueOf(64), fields.get(1).getSize());
    assertTrue(fields.get(1).getIsNullable());
  }

  @Test
  void buildModelMeta_usesGeneratedIdAsFirstRuntimePrimaryKey() {
    var service = new DynamicFormDefineServiceImpl(null, new StubFieldTypeCatalog());
    var form = new DynamicForm();
    form.setName("awf-expense-request");
    form.setTitle("AWF Expense Request");
    var field = new FormField();
    field.setName("requestNo");
    field.setLabel("Request Number");
    field.setType("单行文本");
    field.setParams(Collections.singletonMap("size", 64));
    form.setFields(Collections.singletonList(field));

    var modelMeta = service.buildModelMeta(form);

    assertEquals("DynamicFormAwf_expense_request", modelMeta.getName());
    assertEquals("t_dynamic_form_awf_expense_request", modelMeta.getRawName());
    assertEquals(Collections.singletonList("id"), modelMeta.getPrimaryKeys());
    assertEquals("snowflake", modelMeta.getPrimaryKeyGenerator());
    assertEquals("id", modelMeta.getFields().get(0).getName());
    assertEquals("BasicAudit", modelMeta.getPluginDescriptors().get(0).getName());
    assertEquals(true, modelMeta.getPluginDescriptors().get(0).getConfig().get("insertInitUpdatedAt"));
  }

  @Test
  void buildFields_failsWhenFieldTypeCannotCompile() {
    var service = new DynamicFormDefineServiceImpl(null, new FailingFieldTypeCatalog());
    var form = new DynamicForm();
    var field = new FormField();
    field.setName("amount");
    field.setLabel("金额");
    field.setType("缺失字段类型");
    form.setFields(Collections.singletonList(field));

    var exception = assertThrows(InternalException.class, () -> service.buildFields(form));

    assertTrue(exception.getMessage().contains("amount"));
  }

  private static class StubFieldTypeCatalog implements FieldTypeCatalog {
    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      return Either.right(new CompiledFieldType() {
        @Override
        public DataModelFieldMeta toDataFieldMeta() {
          var meta = new DataModelFieldMeta();
          meta.setType("String");
          meta.setSize((Integer) params.get("size"));
          return meta;
        }

        @Override
        public FormItemNode toInputFormItem() {
          return new FormItemNode();
        }

        @Override
        public FormItemNode toDisplayFormItem() {
          return new FormItemNode();
        }

        @Override
        public String getPrimaryKeyGenerator() {
          return null;
        }
      });
    }
  }

  private static class FailingFieldTypeCatalog implements FieldTypeCatalog {
    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      return Either.left(new FieldTypeCompileException("missing field type", name, null, null, "runtime"));
    }
  }
}
