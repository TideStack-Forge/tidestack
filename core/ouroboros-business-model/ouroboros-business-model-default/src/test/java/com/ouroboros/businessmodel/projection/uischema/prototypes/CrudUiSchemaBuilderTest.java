package com.ouroboros.businessmodel.projection.uischema.prototypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.uischema.schema.SchemaNode;

class CrudUiSchemaBuilderTest {

  @Test
  void buildFieldFilter_supportsAuditFieldsWhenAuditFeatureEnabled() throws Exception {
    var model = createModel(true);
    var config = new LinkedHashMap<String, Object>();
    var builder = new CrudUiSchemaBuilder(
        model,
        config,
        new CrudUiSchemaColumnProcessor(model, config),
        new CrudUiSchemaActionProcessor(model, config)
    );

    var filterConfig = new LinkedHashMap<String, Object>();
    filterConfig.put("type", "field");
    filterConfig.put("fieldName", "createdAt");
    filterConfig.put("filterType", "input-datetime-range");
    filterConfig.put("filterOperator", "BETWEEN");

    Method method = CrudUiSchemaBuilder.class.getDeclaredMethod("buildFieldFilter", Map.class);
    method.setAccessible(true);
    var schema = (SchemaNode) method.invoke(builder, filterConfig);

    assertNotNull(schema);
    assertEquals("创建时间", schema.getProp(String.class, "label").orElse(null));
    assertEquals("createdAt.BETWEEN", schema.getProp(String.class, "name").orElse(null));
    assertEquals("input-datetime-range", schema.getProp(String.class, "type").orElse(null));
  }

  private static DefaultBusinessModel createModel(boolean auditEnabled) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName("id");
    fieldMeta.setTitle("编号");
    fieldMeta.setType("单行文本");
    fieldMeta.setIsRequired(true);
    fieldMeta.setIsPrimaryKey(true);

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户");
    modelMeta.setFields(Collections.singletonList(fieldMeta));
    modelMeta.setRelations(Collections.emptyList());

    var features = new LinkedHashMap<String, Object>();
    features.put("isEnabledAuditFields", auditEnabled);
    modelMeta.setExtraProp("features", features);

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
}
