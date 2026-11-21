package com.ouroboros.dynamicform.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.dynamicform.schema.DynamicFormFieldSchemaBuilder;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.uischema.schema.CRUDTableNode;
import com.ouroboros.uischema.schema.CrudTableColumnNode;
import com.ouroboros.uischema.schema.FormItemNode;

class DynamicFormFieldSchemaBuilderTest {

  @Test
  void buildSchemas_useCompiledInputAndDisplayItems() {
    var catalog = new StubFieldTypeCatalog();
    var builder = new DynamicFormFieldSchemaBuilder(catalog);
    var form = form();

    var formSchema = builder.buildFormSchema(form);
    var input = (FormItemNode) formSchema.getBody().get(0);
    assertEquals("input-text", input.getType());
    assertEquals("title", input.getName());
    assertEquals("标题", input.getLabel());

    CRUDTableNode tableSchema = builder.buildCrudTableSchema(form);
    CrudTableColumnNode column = tableSchema.getColumns().get(1);
    assertEquals("static-text", column.getType());
    assertEquals("title", column.getName());
    assertEquals("标题", column.getLabel());

    assertEquals(2, catalog.contexts.size());
    assertTrue(catalog.contexts.stream().allMatch(context -> context.getScene() == FieldTypeCatalogScene.RUNTIME));
  }

  @Test
  void buildFormSchema_resolvesFieldTypeParameterExpressionsInGeneratedFormItem() {
    var builder = new DynamicFormFieldSchemaBuilder(new DefinitionBackedFieldTypeCatalog());
    var field = new FormField();
    field.setName("title");
    field.setLabel("抬头");
    field.setType("单行文本");
    field.setParams(mapOf(
        "size", 100,
        "regexp", "^[A-Z]+$",
        "regexpMsg", "只能输入大写字母"
    ));

    var form = new DynamicForm();
    form.setName("demo");
    form.setTitle("演示表单");
    form.setFields(Collections.singletonList(field));

    var formSchema = builder.buildFormSchema(form);

    var input = (FormItemNode) formSchema.getBody().get(0);
    assertEquals("input-text", input.getType());
    assertEquals(Integer.valueOf(100), input.getProp(Integer.class, "maxLength").orElse(null));
    assertEquals("^[A-Z]+$", input.getValidations().get("matchRegexp"));
    assertEquals("只能输入大写字母", input.getValidationErrors().get("matchRegexp"));
    assertEquals("title", input.getName());
    assertFalse(input.getRequired());
    assertEquals("抬头", input.getLabel());
  }

  private static DynamicForm form() {
    var field = new FormField();
    field.setName("title");
    field.setLabel("标题");
    field.setType("单行文本");
    field.setParams(Collections.singletonMap("size", 64));

    var form = new DynamicForm();
    form.setName("demo");
    form.setTitle("演示表单");
    form.setFields(Collections.singletonList(field));
    return form;
  }

  private static FieldTypeDefinition singleLineTextDefinition() {
    var definition = new FieldTypeDefinition();
    definition.setName("单行文本");
    definition.setLabel("单行文本");
    definition.setDataType(mapOf(
        "type", "String",
        "size", "${size}"
    ));
    definition.setUi(mapOf(
        "input", mapOf(
            "type", "input-text",
            "maxLength", "${size}"
        ),
        "display", mapOf("type", "static-text")
    ));
    definition.setValidations(mapOf("matchRegexp", "${regexp}"));
    definition.setValidationErrors(mapOf("matchRegexp", "${regexpMsg}"));
    definition.setDefaultParams(mapOf(
        "size", 100,
        "regexp", ".*",
        "regexpMsg", ""
    ));
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    return definition;
  }

  private static Map<String, Object> mapOf(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private static class DefinitionBackedFieldTypeCatalog implements FieldTypeCatalog {
    private final DefaultFieldTypeCompiler compiler = new DefaultFieldTypeCompiler();

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return "单行文本".equals(name) ? Optional.of(singleLineTextDefinition()) : Optional.empty();
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.singletonList(singleLineTextDefinition());
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      return getMetadata(context, name)
          .map(definition -> compiler.compile(definition, params, context))
          .orElseGet(() -> Either.left(new FieldTypeCompileException(
              "字段类型不存在",
              name,
              null,
              null,
              "compile")));
    }
  }

  private static class StubFieldTypeCatalog implements FieldTypeCatalog {
    private final List<FieldTypeCatalogContext> contexts = new ArrayList<FieldTypeCatalogContext>();

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
      contexts.add(context);

      var fieldMeta = new DataModelFieldMeta();
      fieldMeta.setType("String");
      fieldMeta.setSize((Integer) params.get("size"));

      var input = new FormItemNode();
      input.setType("input-text");

      var display = new FormItemNode();
      display.setType("static-text");

      return Either.right(new CompiledFieldType() {
        @Override
        public DataModelFieldMeta toDataFieldMeta() {
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
      });
    }
  }
}
