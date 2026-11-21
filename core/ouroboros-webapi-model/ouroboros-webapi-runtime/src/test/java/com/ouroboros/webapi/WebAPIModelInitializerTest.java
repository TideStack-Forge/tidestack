package com.ouroboros.webapi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ExternalDocsModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.webapi.openapi.SecurityRequirementModel;
import com.ouroboros.webapi.openapi.ServerModel;

import static org.junit.jupiter.api.Assertions.*;

class WebAPIModelInitializerTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldInferStandardWebApiResultWrapperResponseSchema() throws Exception {
    Method method = WrapperController.class.getMethod("getUser");
    SimpleWebAPIModel model = extract("GET", "/users/current", method);

    SchemaModel responseSchema = responseSchema(model);
    assertEquals("object", responseSchema.getType());
    assertNotNull(responseSchema.getProperties().get("code"));
    assertNotNull(responseSchema.getProperties().get("msg"));
    assertNotNull(responseSchema.getProperties().get("data"));
    assertEquals("object", responseSchema.getProperties().get("data").getType());
    assertNotNull(responseSchema.getProperties().get("data").getProperties().get("id"));
  }

  @Test
  void shouldInferItemsAndPagedWrapperResponseSchema() throws Exception {
    SimpleWebAPIModel itemsModel = extract("GET", "/users", WrapperController.class.getMethod("listUsers"));
    SchemaModel itemsSchema = responseSchema(itemsModel);
    assertEquals("array", itemsSchema.getProperties().get("data").getProperties().get("items").getType());

    SimpleWebAPIModel pagedModel = extract("GET", "/users/page", WrapperController.class.getMethod("pageUsers"));
    SchemaModel pagedSchema = responseSchema(pagedModel);
    assertEquals("array", pagedSchema.getProperties().get("data").getProperties().get("items").getType());
    assertEquals("integer", pagedSchema.getProperties().get("data").getProperties().get("total").getType());
  }

  @Test
  void shouldPreserveOpenApiFieldsThroughNormalizer() throws Exception {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .api("GET", "/api/test/{itemId}")
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.REQUEST_MAPPING)
        .summary("Summary")
        .description("Description")
        .operationId("getTestItem")
        .tag("tag-a")
        .parameter(ParameterModel.builder()
            .name("itemId")
            .in(ParameterLocation.PATH)
            .required(true)
            .schema(SchemaModel.builder().stringType().build())
            .build())
        .requestBody(RequestBodyModel.builder()
            .required(true)
            .content(Collections.singletonMap(
                "application/json",
                MediaTypeModel.builder().schema(SchemaModel.builder().objectType().build()).build()
            ))
            .build())
        .response("200", ResponseModel.builder().description("OK").build())
        .deprecated(true)
        .securityRequirement(SecurityRequirementModel.of("bearerAuth", Collections.singletonList("read")))
        .server(ServerModel.builder().url("/api").description("Default").build())
        .externalDocs(ExternalDocsModel.builder().url("https://example.com/docs").description("Docs").build())
        .build();

    WebAPIModel normalized = newNormalizer(model);

    assertEquals("Summary", normalized.getSummary());
    assertEquals("Description", normalized.getDescription());
    assertEquals("getTestItem", normalized.getOperationId());
    assertEquals(Collections.singletonList("tag-a"), normalized.getTags());
    assertEquals(1, normalized.getParameters().size());
    assertNotNull(normalized.getRequestBody());
    assertEquals("OK", normalized.getResponses().get("200").getDescription());
    assertEquals(Boolean.TRUE, normalized.getDeprecated());
    assertEquals(1, normalized.getSecurity().size());
    assertEquals(1, normalized.getServers().size());
    assertEquals("https://example.com/docs", normalized.getExternalDocs().getUrl());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        RequestMappingInfo.paths(path)
            .methods(RequestMethod.GET)
            .produces("application/json")
            .build(),
        WrapperController.class,
        method
    ).build();
  }

  private SchemaModel responseSchema(SimpleWebAPIModel model) {
    return model.getResponses().get("200").getContent().get("application/json").getSchema();
  }

  private WebAPIModel newNormalizer(WebAPIModel model) throws Exception {
    Class<?> clazz = Class.forName("com.ouroboros.webapi.WebAPIModelInitializer$NormalizerWebAPIModel");
    Constructor<?> constructor = clazz.getDeclaredConstructor(WebAPIModel.class);
    constructor.setAccessible(true);
    return (WebAPIModel) constructor.newInstance(model);
  }

  static class WrapperController {
    @GetMapping("/users/current")
    public WebApiResult<UserDto> getUser() {
      return WebApiResult.ok();
    }

    @GetMapping("/users")
    public ItemsWebApiResult<UserDto> listUsers() {
      return ItemsWebApiResult.ok();
    }

    @GetMapping("/users/page")
    public PagedWebApiResult<UserDto> pageUsers() {
      return PagedWebApiResult.ok();
    }
  }

  static class UserDto {
    public String id;
  }
}
