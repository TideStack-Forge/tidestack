package com.ouroboros.webapi;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleWebAPIModelTest {

  @Test
  void builderStoresMethodAndPathViaApiString() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .api("POST /users")
        .build();

    assertEquals("POST", model.getMethod());
    assertEquals("/users", model.getPath());
  }

  @Test
  void builderStoresMethodAndPathViaSeparateArgs() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .api("GET", "/orders")
        .build();

    assertEquals("GET", model.getMethod());
    assertEquals("/orders", model.getPath());
  }

  @Test
  void builderNormalisesPathWithoutLeadingSlash() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .api("DELETE", "items/1")
        .build();

    assertEquals("/items/1", model.getPath());
  }

  @Test
  void builderProvidesConvenienceMethodShortcuts() {
    SimpleWebAPIModel get    = SimpleWebAPIModel.builder().get("/a").build();
    SimpleWebAPIModel post   = SimpleWebAPIModel.builder().post("/b").build();
    SimpleWebAPIModel put    = SimpleWebAPIModel.builder().put("/c").build();
    SimpleWebAPIModel delete = SimpleWebAPIModel.builder().delete("/d").build();
    SimpleWebAPIModel patch  = SimpleWebAPIModel.builder().patch("/e").build();

    assertEquals("GET",    get.getMethod());
    assertEquals("POST",   post.getMethod());
    assertEquals("PUT",    put.getMethod());
    assertEquals("DELETE", delete.getMethod());
    assertEquals("PATCH",  patch.getMethod());
  }

  @Test
  void getApiComposesMethodAndRawPath() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .api("GET", "/products")
        .build();

    assertEquals("GET /products", model.getApi());
  }

  @Test
  void scopeIsNullWhenNotSet() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .build();

    assertNull(model.getScope());
  }

  @Test
  void builderStoresScope() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .scope(WebAPIScope.PUBLIC)
        .build();

    assertEquals(WebAPIScope.PUBLIC, model.getScope());
  }

  @Test
  void allWebAPIScopeValuesCanBeRoundTripped() {
    for (WebAPIScope scope : WebAPIScope.values()) {
      SimpleWebAPIModel model = SimpleWebAPIModel.builder()
          .get("/x")
          .scope(scope)
          .build();
      assertEquals(scope, model.getScope());
    }
  }

  @Test
  void handlerTypeIsNullWhenNotSet() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .build();

    assertNull(model.getHandlerType());
  }

  @Test
  void builderStoresHandlerType() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/flow")
        .handlerType(HandlerType.LOGIC_FLOW)
        .build();

    assertEquals(HandlerType.LOGIC_FLOW, model.getHandlerType());
  }

  @Test
  void allHandlerTypeValuesCanBeRoundTripped() {
    for (HandlerType ht : HandlerType.values()) {
      SimpleWebAPIModel model = SimpleWebAPIModel.builder()
          .get("/x")
          .handlerType(ht)
          .build();
      assertEquals(ht, model.getHandlerType());
    }
  }

  @Test
  void builderStoresSummary() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/items")
        .summary("List all items")
        .build();

    assertEquals("List all items", model.getSummary());
  }

  @Test
  void builderStoresDescriptionAndOperationId() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/items")
        .description("Returns paginated items")
        .operationId("listItems")
        .build();

    assertEquals("Returns paginated items", model.getDescription());
    assertEquals("listItems", model.getOperationId());
  }

  @Test
  void deprecatedDefaultsToFalseWhenNotSet() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .build();

    assertEquals(Boolean.FALSE, model.getDeprecated());
  }

  @Test
  void builderCanMarkDeprecated() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .delete("/legacy")
        .deprecated(Boolean.TRUE)
        .build();

    assertEquals(Boolean.TRUE, model.getDeprecated());
  }

  @Test
  void tagsDefaultToEmptyListWhenNotSet() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .build();

    assertNotNull(model.getTags());
    assertTrue(model.getTags().isEmpty());
  }

  @Test
  void builderAccumulatesTagsViaTagMethod() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/users")
        .tag("users")
        .tag("admin")
        .build();

    assertEquals(Arrays.asList("users", "admin"), model.getTags());
  }

  @Test
  void builderReplacesTagsViaTagsMethod() {
    List<String> tags = Arrays.asList("a", "b");
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/x")
        .tags(tags)
        .build();

    assertEquals(tags, model.getTags());
  }

  @Test
  void parametersDefaultToEmptyListWhenNotSet() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder()
        .get("/ping")
        .build();

    assertNotNull(model.getParameters());
    assertTrue(model.getParameters().isEmpty());
  }

  @Test
  void twoIndependentBuildersProduceIsolatedModels() {
    SimpleWebAPIModel a = SimpleWebAPIModel.builder()
        .post("/a")
        .scope(WebAPIScope.PUBLIC)
        .build();

    SimpleWebAPIModel b = SimpleWebAPIModel.builder()
        .get("/b")
        .scope(WebAPIScope.INTERNAL)
        .build();

    assertEquals("POST", a.getMethod());
    assertEquals("GET",  b.getMethod());
    assertNotSame(a, b);
    assertNotEquals(a.getScope(), b.getScope());
  }
}
