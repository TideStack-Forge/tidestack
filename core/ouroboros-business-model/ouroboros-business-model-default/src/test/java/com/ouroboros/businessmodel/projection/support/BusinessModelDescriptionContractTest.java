package com.ouroboros.businessmodel.projection.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.webapi.AddMPS;
import com.ouroboros.businessmodel.projection.webapi.GetOneMPS;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.openapi.SchemaModel;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelDescriptionContractTest {
  private static final String FIELD_DESCRIPTION = "金额单位为分\n不包含运费；${ 1 / 0 } 仅为说明文本";
  private static final String BILLING_DESCRIPTION = "开票客户\n决定发票抬头，允许与收货客户不同";
  private static final String SHIPPING_DESCRIPTION = "收货客户\n用于送货与签收";
  private final SpringContextUtil contextBridge = new SpringContextUtil();

  @BeforeEach
  void bindRuntimeFieldTypes() {
    var context = new StaticApplicationContext();
    context.getBeanFactory().registerSingleton("fieldTypeCatalog", new DefaultFieldTypeCatalog(
        List.of(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())), new DefaultFieldTypeCompiler()));
    contextBridge.setApplicationContext(context);
  }

  @AfterEach
  void releaseRuntimeFieldTypes() { contextBridge.destroy(); }

  @Test
  void descriptionsKeepTheirBusinessRolesThroughRuntimeSchemasAndOpenApi() {
    for (String version : List.of("legacy", "1.0", "2.0")) {
      var model = model(version);
      var source = Json.toMap(Json.toJsonString(model.getMeta()));
      assertEquals(FIELD_DESCRIPTION, model.getField("amount").orElseThrow().getDescription());
      for (var relation : model.getRelations()) {
        if ("lines".equals(relation.getName())) { continue; }
        assertEquals("semantics.Customer", relation.getRelatedModelName());
        assertEquals("code", relation.getReferenceKey());
        assertEquals("billTo".equals(relation.getName()) ? BILLING_DESCRIPTION : SHIPPING_DESCRIPTION, relation.getDescription());
      }
      for (SchemaModel schema : List.of(BusinessModelSchemaSupport.createRequestSchema(model),
          BusinessModelSchemaSupport.updateRequestSchema(model), BusinessModelSchemaSupport.responseSchema(model))) {
        assertDescriptions(schema);
      }
      var context = new ModelProjectionPipelineContext<>(model);
      context.setAttribute("hasDataModel", true);
      new AddMPS().process(context);
      new GetOneMPS().process(context);
      var add = context.getModel(WebAPIModel.class, "POST /semantics/order").orElseThrow();
      assertDescriptions(add.getRequestBody().getContent().get("application/json").getSchema());
      var get = context.getModel(WebAPIModel.class, "GET /semantics/order/{id}").orElseThrow();
      assertDescriptions(get.getResponses().get("200").getContent().get("application/json").getSchema().getProperties().get("data"));
      assertEquals(source, Json.toMap(Json.toJsonString(model.getMeta())));
    }
  }

  @Test
  void missingDescriptionsStayOptionalAndSchemaTitleFallbackDoesNotChangeMetadata() {
    var model = model("legacy");
    model.getMeta().getFields().forEach(field -> field.setDescription(null));
    model.getMeta().getRelations().forEach(relation -> relation.setDescription(null));
    model = new DefaultBusinessModel(model);
    var original = Json.toMap(Json.toJsonString(model.getMeta()));
    assertNull(model.getField("amount").orElseThrow().getDescription());
    model.getRelations().forEach(relation -> assertNull(relation.getDescription()));
    assertNotNull(BusinessModelSchemaSupport.createRequestSchema(model).getProperties().get("amount"));
    assertNotNull(BusinessModelSchemaSupport.responseSchema(model).getProperties().get("billTo"));
    assertEquals(original, Json.toMap(Json.toJsonString(model.getMeta())));
    assertFalse(original.containsKey("formatVersion"));
  }

  private static void assertDescriptions(SchemaModel schema) {
    var fields = schema.getProperties();
    assertEquals(FIELD_DESCRIPTION, fields.get("amount").getDescription());
    assertEquals("integer", fields.get("amount").getType());
    assertEquals(BILLING_DESCRIPTION, fields.get("billTo").getDescription());
    assertEquals(SHIPPING_DESCRIPTION, fields.get("shipTo").getDescription());
    assertEquals("随订单提交的明细；数量以件计", fields.get("lines").getDescription());
    assertEquals("array", fields.get("lines").getType());
    assertEquals("明细数量（件）", fields.get("lines").getItems().getProperties().get("quantity").getDescription());
    assertEquals("integer", fields.get("lines").getItems().getProperties().get("quantity").getType());
  }

  private static DefaultBusinessModel model(String version) {
    Map<String, BusinessModel> models = new LinkedHashMap<>();
    var provider = new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.ofNullable(models.get(name)); }
      public Map<String, BusinessModel> getBusinessModelMap() { return models; }
    };
    for (String json : List.of("""
        {"modelType":"default","namespace":"semantics","name":"Customer","title":"客户","isEnabled":true,
         "description":"往来客户模型本身的说明",
         "fields":[{"name":"id","type":"单行文本","isPrimaryKey":true},{"name":"code","type":"单行文本"}]}
        """, """
        {"modelType":"default","namespace":"semantics","name":"Line","title":"明细","isEnabled":true,
         "fields":[{"name":"id","type":"单行文本","isPrimaryKey":true},{"name":"orderId","type":"单行文本"},
                   {"name":"quantity","type":"整数数字","description":"明细数量（件）"}]}
        """)) {
      var target = new DefaultBusinessModel((DefaultBusinessModelMeta) BusinessModelMetaParser.parse(json).get(), provider);
      models.put(target.getFullName(), target);
    }
    var meta = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse("""
        {"modelType":"default","namespace":"semantics","name":"Order","title":"订单","isEnabled":true,
         "fields":[{"name":"id","type":"单行文本","isPrimaryKey":true},{"name":"amount","type":"整数数字"}],
         "relations":[
           {"name":"billTo","title":"开票客户","type":"reference","model":"semantics.Customer","referenceKey":"code"},
           {"name":"shipTo","title":"收货客户","type":"reference","model":"semantics.Customer","referenceKey":"code"},
           {"name":"lines","title":"明细","type":"child","model":"semantics.Line","referenceKey":"orderId","key":"id",
            "submitTogether":true,"description":"随订单提交的明细；数量以件计"}],
         "apis":{"add":{"isEnabled":true,"isAuto":true},"getOne":{"isEnabled":true,"isAuto":true}}}
        """).get();
    if (!"legacy".equals(version)) { meta.setFormatVersion(version); }
    meta.getFields().get(1).setDescription(FIELD_DESCRIPTION);
    meta.getRelations().get(0).setDescription(BILLING_DESCRIPTION);
    meta.getRelations().get(1).setDescription(SHIPPING_DESCRIPTION);
    var model = new DefaultBusinessModel(meta, provider);
    models.put(model.getFullName(), model);
    return model;
  }
}
