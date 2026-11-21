package com.ouroboros.dynamicform.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Either;

import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DynamicFormDefineControllerTest {

  @Test
  void searchShouldPreserveFiltersPaginationOrderAndFieldOmission() {
    var service = new EmptyDynamicFormDefineService();
    var controller = new DynamicFormDefineController();
    controller.dynamicFormDefineService = service;

    controller.search(Map.of("page", 3, "pageSize", 5, "sort", "name desc", "status", "已部署"));

    assertEquals(Map.of("status", "已部署"), service.lastWhere);
    assertEquals("DynamicForm", service.lastQuery.get("FROM"));
    assertEquals(service.lastWhere, service.lastQuery.get("WHERE"));
    assertEquals(Map.of("name", "desc"), service.lastQuery.get("ORDER"));
    assertEquals(10, service.lastQuery.get("OFFSET"));
    assertEquals(5, service.lastQuery.get("LIMIT"));
    assertEquals("fields", service.lastQuery.get("OMIT"));
  }

  @Test
  void fieldTypeEndpointsUsePluralHttpRoutes() throws Exception {
    var controller = new DynamicFormDefineController();
    controller.dynamicFormDefineService = new EmptyDynamicFormDefineService();
    controller.fieldTypeCatalog = new StubFieldTypeCatalog();
    var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var fieldsResponse = readResponse(mockMvc.perform(get("/api/dynamic-form/field-types"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse());
    assertEquals(0, fieldsResponse.get("code"));
    assertEquals(1, getItems(fieldsResponse).size());
    assertEquals("单行文本", getItems(fieldsResponse).get(0).get("label"));

    var paramsResponse = readResponse(mockMvc.perform(get("/api/dynamic-form/field-types/{fieldName}/params-schema", "单行文本"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse());
    assertEquals(0, paramsResponse.get("code"));
    assertEquals(1, getDataList(paramsResponse).size());
    assertEquals("size", getDataList(paramsResponse).get(0).get("name"));
  }

  @Test
  void documentedErrorResponsesShouldUseMatchingHttpStatusCodes() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));
    try {
      var controller = new DynamicFormDefineController();
      controller.dynamicFormDefineService = new EmptyDynamicFormDefineService();
      controller.fieldTypeCatalog = new StubFieldTypeCatalog();
      var mockMvc = MockMvcBuilders.standaloneSetup(controller)
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      mockMvc.perform(post("/api/dynamic-form/{id}/deploy", 12))
          .andExpect(status().isNotFound());

      mockMvc.perform(post("/api/dynamic-form/validate-field-name")
              .contentType("application/json")
              .content("{}"))
          .andExpect(status().isUnprocessableEntity());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void updateShouldReturnHttp404WhenTargetDisappearsBeforePersist() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));
    try {
      var controller = new DynamicFormDefineController();
      controller.dynamicFormDefineService = new MissingOnUpdateDynamicFormDefineService();
      controller.fieldTypeCatalog = new StubFieldTypeCatalog();
      var mockMvc = MockMvcBuilders.standaloneSetup(controller)
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      assertError(
          mockMvc.perform(put("/api/dynamic-form/{id}", 12)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"customer\",\"title\":\"Customer\",\"fields\":[]}"))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse(),
          404,
          "动态表单定义不存在");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readResponse(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
    return Json.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getItems(Map<String, Object> response) {
    return (List<Map<String, Object>>) ((Map<String, Object>) response.get("data")).get("items");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getDataList(Map<String, Object> response) {
    return (List<Map<String, Object>>) response.get("data");
  }

  private static void assertError(MockHttpServletResponse response, int code, String message) throws Exception {
    Map<String, Object> body = readResponse(response);
    Number actualCode = (Number) body.get("code");
    if (actualCode == null || actualCode.intValue() != code) {
      throw new AssertionError("Expected code " + code + " but was " + body.get("code"));
    }
    if (!Objects.equals(message, body.get("msg"))) {
      throw new AssertionError("Expected message " + message + " but was " + body.get("msg"));
    }
  }

  private static com.ouroboros.dynamicform.model.DynamicForm form(Integer id, String name, String status) {
    com.ouroboros.dynamicform.model.DynamicForm form = new com.ouroboros.dynamicform.model.DynamicForm();
    form.setId(id);
    form.setName(name);
    form.setStatus(status);
    form.setFields(Collections.emptyList());
    return form;
  }

  private static class StubFieldTypeCatalog implements FieldTypeCatalog {
    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      var metadata = new FieldTypeDefinition();
      metadata.setName("单行文本");
      metadata.setLabel("单行文本");
      metadata.setParamsUiSchema(Collections.singletonList(new LinkedHashMap<String, Object>() {{
        put("name", "size");
      }}));
      return Optional.of(metadata);
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      var field = new FieldTypeDefinition();
      field.setName("单行文本");
      field.setLabel("单行文本");
      field.setOrder(10);
      field.setIsPrimaryKeyType(false);

      var primaryKey = new FieldTypeDefinition();
      primaryKey.setName("编码主键");
      primaryKey.setLabel("编码主键");
      primaryKey.setOrder(20);
      primaryKey.setIsPrimaryKeyType(true);
      return Arrays.asList(field, primaryKey);
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      throw new UnsupportedOperationException();
    }
  }

  private static class EmptyDynamicFormDefineService implements DynamicFormDefineService {
    private Map<String, Object> lastWhere;
    private Map<String, Object> lastQuery;

    @Override
    public Optional<com.ouroboros.dynamicform.model.DynamicForm> getDynamicFormByName(String name) {
      return Optional.empty();
    }

    @Override
    public Optional<com.ouroboros.dynamicform.model.DynamicForm> getDynamicFormById(Integer id) {
      return Optional.empty();
    }

    @Override
    public Long getTotal(Map<String, Object> where) {
      lastWhere = where;
      return 0L;
    }

    @Override
    public List<com.ouroboros.dynamicform.model.DynamicForm> search(Map<String, Object> query) {
      lastQuery = query;
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.data.model.DataModelMeta> getModelMetaList(Map<String, Object> where) {
      return Collections.emptyList();
    }

    @Override
    public Optional<com.ouroboros.dynamicform.model.DynamicForm> add(com.ouroboros.dynamicform.model.DynamicForm dynamicForm) {
      return Optional.empty();
    }

    @Override
    public Boolean update(com.ouroboros.dynamicform.model.DynamicForm dynamicForm) {
      return false;
    }

    @Override
    public Boolean delete(Integer id) {
      return false;
    }

    @Override
    public Boolean deploy(Integer id) {
      return false;
    }

    @Override
    public Boolean takeDown(Integer id) {
      return false;
    }

    @Override
    public Boolean reDeploy(Integer id) {
      return false;
    }

    @Override
    public Boolean archive(Integer id) {
      return false;
    }

    @Override
    public void refreshModels() {
    }
  }

  private static class MissingOnUpdateDynamicFormDefineService extends EmptyDynamicFormDefineService {
    @Override
    public Optional<com.ouroboros.dynamicform.model.DynamicForm> getDynamicFormById(Integer id) {
      if (Objects.equals(id, 12)) {
        return Optional.of(form(12, "customer", "未部署"));
      }
      return Optional.empty();
    }

    @Override
    public Boolean update(com.ouroboros.dynamicform.model.DynamicForm dynamicForm) {
      return false;
    }
  }
}
