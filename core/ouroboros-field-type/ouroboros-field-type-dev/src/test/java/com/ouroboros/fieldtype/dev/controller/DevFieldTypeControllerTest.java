package com.ouroboros.fieldtype.dev.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Either;

import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.dev.controller.DevFieldTypeController;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.util.Json;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevFieldTypeControllerTest {

  @Test
  void httpRoutesExposeFieldTypeEndpoints() throws Exception {
    var definition = definition("单行文本", "单行文本", false, 10);
    definition.setParamsUiSchema(Collections.singletonList(new LinkedHashMap<String, Object>() {{
      put("name", "size");
      put("label", "长度");
      put("type", "input-number");
      put("value", 100);
    }}));
    var catalog = new CapturingFieldTypeCatalog(
        Arrays.asList(
            definition("应用字段", "应用字段", false, 10),
            definition("主键字段", "主键字段", true, 20)
        ),
        Optional.of(definition)
    );
    var mockMvc = MockMvcBuilders.standaloneSetup(new DevFieldTypeController(catalog)).build();

    var normalResponse = readResponse(mockMvc.perform(get("/api/app/{appName}/field-types/normal", "crm"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse());
    assertEquals("应用字段", getItems(normalResponse).get(0).get("label"));

    var primaryResponse = readResponse(mockMvc.perform(get("/api/app/{appName}/field-types/primary", "crm"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse());
    assertEquals("主键字段", getItems(primaryResponse).get(0).get("label"));

    var schemaResponse = readResponse(mockMvc.perform(get("/api/app/{appName}/field-types/{fieldName}/ui-schema", "crm", "单行文本"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse());
    assertEquals("container", ((Map<?, ?>) schemaResponse.get("data")).get("type"));
    assertEquals("size", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) schemaResponse.get("data")).get("body")).get(0)).get("name"));

    assertEquals(2, catalog.listContexts.size());
    assertTrue(catalog.listContexts.stream().allMatch(context -> context.getScene() == FieldTypeCatalogScene.DEV_APP));
    assertEquals(FieldTypeCatalogScene.DEV_APP, catalog.lastGetContext.getScene());
    assertEquals("crm", catalog.lastGetContext.getAppName());
  }

  @Test
  void normalAndPrimaryEndpointsUseDevAppRepositoryContext() {
    var catalog = new CapturingFieldTypeCatalog(
        Arrays.asList(
            definition("应用字段", "应用字段", false, 10),
            definition("主键字段", "主键字段", true, 20)
        ),
        Optional.empty()
    );
    var controller = new DevFieldTypeController(catalog);

    var normalResult = controller.getNormalFieldTypes("crm");
    assertEquals("应用字段", normalResult.getData().getItems().iterator().next().getLabel());

    var primaryResult = controller.getPrimaryFieldTypes("crm");
    assertEquals("主键字段", primaryResult.getData().getItems().iterator().next().getLabel());

    assertEquals(2, catalog.listContexts.size());
    assertTrue(catalog.listContexts.stream().allMatch(context -> context.getScene() == FieldTypeCatalogScene.DEV_APP));
    assertTrue(catalog.listContexts.stream().allMatch(context -> "crm".equals(context.getAppName())));
  }

  @Test
  void uiSchemaEndpointReturnsParamsUiSchemaFromMetadata() {
    var definition = definition("单行文本", "单行文本", false, 10);
    definition.setParamsUiSchema(Collections.singletonList(new LinkedHashMap<String, Object>() {{
      put("name", "size");
      put("label", "长度");
      put("type", "input-number");
      put("value", 100);
    }}));

    var catalog = new CapturingFieldTypeCatalog(Collections.emptyList(), Optional.of(definition));
    var controller = new DevFieldTypeController(catalog);

    var result = controller.getUiSchema("crm", "单行文本");
    var container = (CommonContainer) result.getData();
    var body = (List<?>) container.getProps().get("body");

    assertEquals("container", container.getType());
    assertEquals(1, body.size());
    assertEquals("size", ((Map<?, ?>) body.get(0)).get("name"));
    assertNotNull(catalog.lastGetContext);
    assertEquals(FieldTypeCatalogScene.DEV_APP, catalog.lastGetContext.getScene());
    assertEquals("crm", catalog.lastGetContext.getAppName());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readResponse(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
    return Json.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getItems(Map<String, Object> response) {
    return (List<Map<String, Object>>) ((Map<String, Object>) response.get("data")).get("items");
  }

  private static FieldTypeDefinition definition(String name, String label, boolean primaryKeyType, int order) {
    var definition = new FieldTypeDefinition();
    definition.setName(name);
    definition.setLabel(label);
    definition.setIsPrimaryKeyType(primaryKeyType);
    definition.setOrder(order);
    return definition;
  }

  private static class CapturingFieldTypeCatalog implements FieldTypeCatalog {
    private final List<FieldTypeDefinition> definitions;
    private final Optional<FieldTypeDefinition> definition;
    private final List<FieldTypeCatalogContext> listContexts = new ArrayList<FieldTypeCatalogContext>();
    private FieldTypeCatalogContext lastGetContext;

    private CapturingFieldTypeCatalog(List<FieldTypeDefinition> definitions, Optional<FieldTypeDefinition> definition) {
      this.definitions = definitions;
      this.definition = definition;
    }

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      this.lastGetContext = context;
      return definition;
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      this.listContexts.add(context);
      return definitions;
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      throw new UnsupportedOperationException();
    }
  }
}
