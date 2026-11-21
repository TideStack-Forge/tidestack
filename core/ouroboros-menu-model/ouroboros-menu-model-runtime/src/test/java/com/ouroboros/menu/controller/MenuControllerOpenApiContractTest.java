package com.ouroboros.menu.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractOperationAndResponsesForAuthorityMenus() throws Exception {
    Method method = MenuController.class.getMethod("authorityMenus");

    SimpleWebAPIModel model = extractor.extract(
        SimpleWebAPIModel.builder()
            .api("GET", "/api/authored-menu-tree")
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        RequestMappingInfo.paths("/api/authored-menu-tree")
            .methods(RequestMethod.GET)
            .produces("application/json")
            .build(),
        MenuController.class,
        method
    ).build();

    assertEquals("获取当前用户的授权菜单树", model.getSummary());
    assertEquals(Collections.singletonList("menu"), model.getTags());
    assertTrue(model.getResponses().containsKey("200"));
    assertEquals("成功返回授权菜单树", model.getResponses().get("200").getDescription());
  }
}
