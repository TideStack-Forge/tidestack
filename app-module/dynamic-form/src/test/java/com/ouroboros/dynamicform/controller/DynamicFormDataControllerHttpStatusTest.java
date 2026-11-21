package com.ouroboros.dynamicform.controller;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DynamicFormDataControllerHttpStatusTest {

  @Test
  void missingOrUnsupportedFormShouldReturnMatchingHttpStatusCodes() throws Exception {
    DynamicFormDataController controller = new DynamicFormDataController();
    var defineService = new StubDynamicFormDefineService(Collections.singletonMap("draft", form("draft", "未部署")));
    controller.dynamicFormDefineService = defineService;
    controller.dynamicFormDataService = new StubDynamicFormDataService(null, defineService);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    assertError(
        mockMvc.perform(get("/api/dynamic-form/missing/data"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse(),
        404,
        "表单不存在");

    assertError(
        mockMvc.perform(post("/api/dynamic-form/missing/data")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse(),
        404,
        "表单不存在");

    assertError(
        mockMvc.perform(get("/api/dynamic-form/draft/data"))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse(),
        400,
        "当前表单不支持此操作");

    assertError(
        mockMvc.perform(post("/api/dynamic-form/draft/data")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse(),
        400,
        "当前表单不支持此操作");
  }

  @Test
  void missingDataRecordsShouldReturnHttp404() throws Exception {
    DynamicFormDataController controller = new DynamicFormDataController();
    controller.dynamicFormDefineService = new StubDynamicFormDefineService(Collections.singletonMap("customer", form("customer", "已部署")));
    controller.dynamicFormDataService = new StubDynamicFormDataService(missingDataModel());

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    assertError(
        mockMvc.perform(get("/api/dynamic-form/customer/data/row-1"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse(),
        404,
        "数据不存在");

    assertError(
        mockMvc.perform(put("/api/dynamic-form/customer/data/row-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse(),
        404,
        "数据不存在");

    assertError(
        mockMvc.perform(delete("/api/dynamic-form/customer/data/row-1"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse(),
        404,
        "数据不存在");
  }

  private static void assertError(MockHttpServletResponse response, int code, String message) throws Exception {
    Map<String, Object> body = Json.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
    Number actualCode = (Number) body.get("code");
    if (actualCode == null || actualCode.intValue() != code) {
      throw new AssertionError("Expected code " + code + " but was " + body.get("code"));
    }
    if (!Objects.equals(message, body.get("msg"))) {
      throw new AssertionError("Expected message " + message + " but was " + body.get("msg"));
    }
  }

  private static DynamicForm form(String name, String status) {
    DynamicForm form = new DynamicForm();
    form.setName(name);
    form.setStatus(status);
    form.setFields(Collections.emptyList());
    return form;
  }

  private static DataModel missingDataModel() {
    return (DataModel) Proxy.newProxyInstance(
        DataModel.class.getClassLoader(),
        new Class<?>[]{DataModel.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) {
              return "MissingDataModel";
            }
            if ("hashCode".equals(method.getName())) {
              return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
              return proxy == args[0];
            }
          }
          if ("get".equals(method.getName())) {
            return Try.success(null);
          }
          if ("update".equals(method.getName()) || "delete".equals(method.getName())) {
            return Try.success(0L);
          }
          return defaultValue(method.getReturnType());
        });
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      if (Optional.class.equals(type)) {
        return Optional.empty();
      }
      if (List.class.equals(type)) {
        return Collections.emptyList();
      }
      if (Map.class.equals(type)) {
        return Collections.emptyMap();
      }
      return null;
    }
    if (boolean.class.equals(type)) {
      return false;
    }
    if (byte.class.equals(type)) {
      return (byte) 0;
    }
    if (short.class.equals(type)) {
      return (short) 0;
    }
    if (int.class.equals(type)) {
      return 0;
    }
    if (long.class.equals(type)) {
      return 0L;
    }
    if (float.class.equals(type)) {
      return 0F;
    }
    if (double.class.equals(type)) {
      return 0D;
    }
    if (char.class.equals(type)) {
      return '\0';
    }
    return null;
  }

  private static class StubDynamicFormDataService implements DynamicFormDataService {
    private final DataModel dataModel;
    private final DynamicFormDefineService dynamicFormDefineService;

    private StubDynamicFormDataService(DataModel dataModel) {
      this(dataModel, null);
    }

    private StubDynamicFormDataService(DataModel dataModel, DynamicFormDefineService dynamicFormDefineService) {
      this.dataModel = dataModel;
      this.dynamicFormDefineService = dynamicFormDefineService;
    }

    @Override
    public Optional<DataModel> getDataModel(String name) {
      return Optional.ofNullable(dataModel);
    }

    @Override
    public Optional<StatusGuardFailure> validateStatus(String name, String... statuses) {
      if (dynamicFormDefineService == null) {
        return DynamicFormDataService.super.validateStatus(name, statuses);
      }
      var dynamicForm = dynamicFormDefineService.getDynamicFormByName(name);
      if (dynamicForm.isEmpty()) {
        return Optional.of(StatusGuardFailure.notFound());
      }
      var status = dynamicForm.get().getStatus();
      if (!Arrays.asList(statuses).contains(status)) {
        return Optional.of(StatusGuardFailure.unsupported(status, statuses));
      }
      return Optional.empty();
    }
  }

  private static class StubDynamicFormDefineService implements DynamicFormDefineService {
    private final Map<String, DynamicForm> formsByName;

    private StubDynamicFormDefineService(Map<String, DynamicForm> formsByName) {
      this.formsByName = formsByName;
    }

    @Override
    public Optional<DynamicForm> getDynamicFormByName(String name) {
      return Optional.ofNullable(formsByName.get(name));
    }

    @Override
    public Optional<DynamicForm> getDynamicFormById(Integer id) {
      return Optional.empty();
    }

    @Override
    public Long getTotal(Map<String, Object> where) {
      return 0L;
    }

    @Override
    public List<DynamicForm> search(Map<String, Object> query) {
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.data.model.DataModelMeta> getModelMetaList(Map<String, Object> where) {
      return Collections.emptyList();
    }

    @Override
    public Optional<DynamicForm> add(DynamicForm dynamicForm) {
      return Optional.empty();
    }

    @Override
    public Boolean update(DynamicForm dynamicForm) {
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
}
