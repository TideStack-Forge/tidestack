package com.ouroboros.notification.controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.notification.controller.req.ChannelBindingReq;
import com.ouroboros.notification.controller.req.SendNotificationReq;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.Command;
import com.ouroboros.notification.controller.NotificationTemplateController.CreateOverride;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForSend() throws Exception {
    Method method = NotificationController.class.getMethod("send", SendNotificationReq.class);
    SimpleWebAPIModel model = extract("POST", "/api/notification/send", mapping("/api/notification/send", RequestMethod.POST), NotificationController.class, method);

    assertEquals("发送通知", model.getSummary());
    assertEquals(Collections.singletonList("notification"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("通知发送请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("通知已进入异步发送流程", model.getResponses().get("200").getDescription());
    assertEquals("通知模板或接收方参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractPathParameterAndRequestBodyForChannelBind() throws Exception {
    Method method = NotificationChannelController.class.getMethod("bind", String.class, ChannelBindingReq.class);
    SimpleWebAPIModel model = extract("POST", "/api/notification/{channel}/employee/bind", mapping("/api/notification/{channel}/employee/bind", RequestMethod.POST), NotificationChannelController.class, method);

    assertEquals("绑定通知渠道地址", model.getSummary());
    assertEquals(Collections.singletonList("notification"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "channel").getIn());
    assertEquals("通知渠道名称", parameter(model, "channel").getDescription());
    assertNotNull(model.getRequestBody());
    assertEquals("渠道绑定请求", model.getRequestBody().getDescription());
  }

  @Test
  void shouldExtractResponsesForChannelUnbindAndBindInfo() throws Exception {
    Method unbind = NotificationChannelController.class.getMethod("unbind", String.class);
    SimpleWebAPIModel unbindModel = extract("POST", "/api/notification/{channel}/employee/unbind", mapping("/api/notification/{channel}/employee/unbind", RequestMethod.POST), NotificationChannelController.class, unbind);
    assertEquals("解绑通知渠道地址", unbindModel.getSummary());
    assertEquals("渠道地址解绑成功", unbindModel.getResponses().get("200").getDescription());
    assertEquals("解绑参数不合法", unbindModel.getResponses().get("400").getDescription());

    Method bindInfo = NotificationChannelController.class.getMethod("bindInfo", String.class);
    SimpleWebAPIModel bindInfoModel = extract("GET", "/api/notification/{channel}/employee/bind/info", mapping("/api/notification/{channel}/employee/bind/info", RequestMethod.GET), NotificationChannelController.class, bindInfo);
    assertEquals("获取通知渠道绑定信息", bindInfoModel.getSummary());
    assertEquals("成功返回绑定信息", bindInfoModel.getResponses().get("200").getDescription());
    assertEquals("查询参数不合法", bindInfoModel.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractTemplateContractAcrossCrudEndpoints() throws Exception {
    Method add = NotificationTemplateController.class.getMethod("add", CreateOverride.class);
    SimpleWebAPIModel addModel = extract("POST", "/api/sys/notification-template", mapping("/api/sys/notification-template", RequestMethod.POST), NotificationTemplateController.class, add);
    assertEquals("创建通知模板覆盖", addModel.getSummary());
    assertNotNull(addModel.getRequestBody());
    assertEquals("通知模板覆盖已保存", addModel.getResponses().get("200").getDescription());

    Method delete = NotificationTemplateController.class.getMethod("delete", String.class, Command.class);
    SimpleWebAPIModel deleteModel = extract("DELETE", "/api/sys/notification-template/{id}", mapping("/api/sys/notification-template/{id}", RequestMethod.DELETE), NotificationTemplateController.class, delete);
    assertEquals("撤销通知模板覆盖", deleteModel.getSummary());
    assertEquals("覆盖已撤销", deleteModel.getResponses().get("200").getDescription());
    assertEquals("通知模板不存在", deleteModel.getResponses().get("404").getDescription());

    Method putUpdate = NotificationTemplateController.class.getMethod("update", String.class, Command.class);
    SimpleWebAPIModel putModel = extract("PUT", "/api/sys/notification-template/{id}", mapping("/api/sys/notification-template/{id}", RequestMethod.PUT), NotificationTemplateController.class, putUpdate);
    assertEquals("更新通知模板覆盖", putModel.getSummary());
    assertEquals("预期版本和修改字段", putModel.getRequestBody().getDescription());
    assertEquals("通知模板覆盖已保存", putModel.getResponses().get("200").getDescription());
    assertEquals("通知模板参数不合法", putModel.getResponses().get("400").getDescription());

    Method patchUpdate = NotificationTemplateController.class.getMethod("partialUpdate", String.class, Command.class);
    SimpleWebAPIModel patchModel = extract("PATCH", "/api/sys/notification-template/{id}", mapping("/api/sys/notification-template/{id}", RequestMethod.PATCH), NotificationTemplateController.class, patchUpdate);
    assertEquals("部分更新通知模板覆盖", patchModel.getSummary());
    assertEquals("预期版本和修改字段", patchModel.getRequestBody().getDescription());

    Method get = NotificationTemplateController.class.getMethod("get", String.class);
    SimpleWebAPIModel getModel = extract("GET", "/api/sys/notification-template/{name}", mapping("/api/sys/notification-template/{name}", RequestMethod.GET), NotificationTemplateController.class, get);
    assertEquals("获取通知模板详情", getModel.getSummary());
    assertEquals("通知模板名称", parameter(getModel, "name").getDescription());

    Method search = NotificationTemplateController.class.getMethod("search", String.class, String.class, Boolean.class, Boolean.class, Integer.class, Integer.class, String.class, String.class, Boolean.class);
    SimpleWebAPIModel searchModel = extract("GET", "/api/sys/notification-template", mapping("/api/sys/notification-template", RequestMethod.GET), NotificationTemplateController.class, search);
    assertEquals("分页查询通知模板", searchModel.getSummary());
    assertEquals(ParameterLocation.QUERY, parameter(searchModel, "page").getIn());
    assertEquals("页码", parameter(searchModel, "page").getDescription());
    assertEquals("成功返回通知模板分页结果", searchModel.getResponses().get("200").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Class<?> controllerClass, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder().api(httpMethod, path).scope(WebAPIScope.INTERNAL).handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        controllerClass,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path).methods(method).consumes("application/json").produces("application/json").build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
