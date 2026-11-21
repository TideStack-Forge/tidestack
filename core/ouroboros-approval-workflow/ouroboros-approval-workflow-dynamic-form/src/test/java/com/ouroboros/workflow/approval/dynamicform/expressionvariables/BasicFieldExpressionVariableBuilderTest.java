package com.ouroboros.workflow.approval.dynamicform.expressionvariables;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import io.vavr.control.Either;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.workflow.approval.model.ExpressionVariable;

class BasicFieldExpressionVariableBuilderTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @AfterEach
  void clearApplicationContext() throws Exception {
    setApplicationContext(null);
  }

  @Test
  void supportAndBuild_useRuntimeFieldTypeCatalogAndOperators() throws Exception {
    var catalog = new RecordingFieldTypeCatalog(true);
    bindFieldTypeCatalog(catalog);

    var builder = new BasicFieldExpressionVariableBuilder();
    var field = new FormField();
    field.setName("title");
    field.setLabel("标题");
    field.setType("单行文本");
    var params = new LinkedHashMap<String, Object>();
    params.put("size", 64);
    field.setParams(params);

    assertTrue(builder.support(field));

    ExpressionVariable variable = builder.build(field);

    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastMetadataContext.getScene());
    assertEquals(FieldTypeCatalogContext.runtime().getScene(), catalog.lastCompileContext.getScene());
    assertEquals(params, catalog.lastCompileParams);
    assertEquals("title", variable.getName());
    assertEquals("标题", variable.getLabel());
    assertEquals("input-text", variable.getType());
    assertEquals("String", variable.getExtraProps().get("valueType"));
    assertEquals("请输入标题", variable.getExtraProps().get("placeholder"));
    assertFalse(variable.getExtraProps().containsKey("type"));

    @SuppressWarnings("unchecked")
    var operators = (Set<String>) variable.getExtraProps().get("operators");
    assertNotNull(operators);
    assertTrue(operators.contains("equal"));
    assertTrue(operators.contains("like"));
  }

  @Test
  void support_returnsFalseWhenFieldTypeMetadataIsMissing() throws Exception {
    bindFieldTypeCatalog(new RecordingFieldTypeCatalog(false));

    var builder = new BasicFieldExpressionVariableBuilder();
    var field = new FormField();
    field.setType("单行文本");

    assertFalse(builder.support(field));
  }

  @Test
  void buildReportsMissingContextAndUnsupportedConditions() throws Exception {
    setApplicationContext(null);
    var builder = new BasicFieldExpressionVariableBuilder();
    var field = new FormField();
    field.setName("attachment");
    field.setType("附件");
    assertThrows(com.ouroboros.exception.InternalException.class, () -> builder.build(field));

    bindFieldTypeCatalog(new RecordingFieldTypeCatalog(true));
    assertFalse(builder.support(field));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> builder.build(field));
  }

  @Test
  void buildKeepsCompiledValueTypeSeparateFromInputType() throws Exception {
    var catalog = new RecordingFieldTypeCatalog(true);
    catalog.valueType = "Decimal";
    bindFieldTypeCatalog(catalog);
    var field = new FormField();
    field.setName("amount");
    field.setType("货币");

    var variable = new BasicFieldExpressionVariableBuilder().build(field);

    assertEquals("input-text", variable.getType());
    assertEquals("Decimal", variable.getExtraProps().get("valueType"));
  }

  @Test
  void buildOmitsValueTypeWhenDataProjectionIsAbsent() throws Exception {
    var catalog = new RecordingFieldTypeCatalog(true);
    catalog.valueType = null;
    bindFieldTypeCatalog(catalog);
    var field = new FormField();
    field.setName("title");
    field.setType("单行文本");

    var variable = new BasicFieldExpressionVariableBuilder().build(field);

    assertEquals("input-text", variable.getType());
    assertFalse(variable.getExtraProps().containsKey("valueType"));
  }

  private static void bindFieldTypeCatalog(FieldTypeCatalog fieldTypeCatalog) throws Exception {
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", fieldTypeCatalog);
    setApplicationContext(applicationContext);
  }

  private static void setApplicationContext(Object applicationContext) throws Exception {
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static class RecordingFieldTypeCatalog implements FieldTypeCatalog {
    private final boolean hasMetadata;
    private FieldTypeCatalogContext lastMetadataContext;
    private FieldTypeCatalogContext lastCompileContext;
    private Map<String, Object> lastCompileParams = Collections.emptyMap();
    private String valueType = "String";

    private RecordingFieldTypeCatalog(boolean hasMetadata) {
      this.hasMetadata = hasMetadata;
    }

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      this.lastMetadataContext = context;
      if (!hasMetadata) {
        return Optional.empty();
      }
      var definition = new FieldTypeDefinition();
      definition.setName(name);
      definition.setLabel(name);
      return Optional.of(definition);
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      this.lastCompileContext = context;
      this.lastCompileParams = params == null
          ? Collections.<String, Object>emptyMap()
          : new LinkedHashMap<String, Object>(params);

      var input = new FormItemNode();
      input.setType("input-text");
      input.setPlaceholder("请输入标题");

      return Either.right(new CompiledFieldType() {
        @Override
        public com.ouroboros.data.model.DataModelFieldMeta toDataFieldMeta() {
          if (valueType == null) {
            return null;
          }
          var meta = new com.ouroboros.data.model.DataModelFieldMeta();
          meta.setType(valueType);
          return meta;
        }

        @Override
        public FormItemNode toInputFormItem() {
          return input;
        }

        @Override
        public FormItemNode toDisplayFormItem() {
          return input;
        }

        @Override
        public String getPrimaryKeyGenerator() {
          return null;
        }
      });
    }
  }
}
