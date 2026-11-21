package com.ouroboros.notification.internal.message.controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalMessageNotificationControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractSearchContract() throws Exception {
    Method method = InternalMessageNotificationController.class.getMethod("search", String.class, String.class, String.class, Integer.class, Integer.class);
    SimpleWebAPIModel model = extract("GET", "/api/notification", mapping("/api/notification", RequestMethod.GET), method);

    assertEquals("分页查询站内信", model.getSummary());
    assertEquals(Collections.singletonList("notification"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "platform").getIn());
    assertEquals("站内信平台标识", parameter(model, "platform").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "platform").getRequired());
    assertEquals("页码", parameter(model, "page").getDescription());
    assertEquals("成功返回站内信分页结果", model.getResponses().get("200").getDescription());
    assertEquals("平台或消息状态参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractBatchMarkReadContract() throws Exception {
    Method method = InternalMessageNotificationController.class.getMethod("markRead", Map.class);
    SimpleWebAPIModel model = extract("PATCH", "/api/notification/mark-read", mapping("/api/notification/mark-read", RequestMethod.PATCH), method);

    assertEquals("批量标记站内信为已读", model.getSummary());
    assertNotNull(model.getRequestBody());
    assertEquals("待标记为已读的消息 ID 列表", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("站内信已批量标记为已读", model.getResponses().get("204").getDescription());
    assertEquals("messageIds 参数缺失", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractSingleMarkReadAndUnreadCountContracts() throws Exception {
    Method markOne = InternalMessageNotificationController.class.getMethod("markRead", String.class);
    SimpleWebAPIModel markOneModel = extract("PATCH", "/api/notification/{id}/read", mapping("/api/notification/{id}/read", RequestMethod.PATCH), markOne);
    assertEquals("标记单条站内信为已读", markOneModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(markOneModel, "id").getIn());
    assertEquals("站内信 ID", parameter(markOneModel, "id").getDescription());
    assertEquals("站内信已标记为已读", markOneModel.getResponses().get("204").getDescription());
    assertEquals("站内信不存在、已读或不属于当前用户", markOneModel.getResponses().get("404").getDescription());

    Method unread = InternalMessageNotificationController.class.getMethod("unreadCount");
    SimpleWebAPIModel unreadModel = extract("GET", "/api/notification/unread-count", mapping("/api/notification/unread-count", RequestMethod.GET), unread);
    assertEquals("获取未读站内信数量", unreadModel.getSummary());
    assertEquals(Collections.singletonList("notification"), unreadModel.getTags());
    assertEquals("成功返回未读站内信数量", unreadModel.getResponses().get("200").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder().api(httpMethod, path).scope(WebAPIScope.INTERNAL).handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        InternalMessageNotificationController.class,
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
