package com.ouroboros.webapi.dev.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.logicflow.dev.service.impl.LogicModelDevelopServiceImpl;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;
import com.ouroboros.util.Json;

class WebAPIDevControllerTest {

  @Test
  void treeShouldHandleApiNameCasingDifferences() {
    LogicModelDevelopServiceImpl logicModelDevelopService = new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    };

    WebAPIDevController controller = new WebAPIDevController(logicModelDevelopService, Collections.singletonList(webApiProvider()));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertFalse(apiNodes.isEmpty());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
  }

  @Test
  void treeShouldKeepApisWithMissingIntermediateConcreteParents() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        metadata("GET /api/ping", webApiDetails("get", "/api/ping", "健康检查")),
        metadata("POST /api/upload/file", webApiDetails("post", "/api/upload/file", "上传文件")),
        metadata("POST /api/upload/chunk", webApiDetails("post", "/api/upload/chunk", "上传分片")))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> nodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()));

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = nodes.stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertTrue(apiNodes.stream().anyMatch(node -> "GET /api/ping".equals(node.getApi())));
    assertTrue(apiNodes.stream().anyMatch(node -> "POST /api/upload/file".equals(node.getApi())));
    assertTrue(apiNodes.stream().anyMatch(node -> "POST /api/upload/chunk".equals(node.getApi())));
  }

  @Test
  void treeShouldSkipMetadataWithoutHandlerType() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(new MetadataProvider() {
      @Override
      public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
        if (!"webapi".equals(moduleName)) {
          return Collections.emptyList();
        }

        return Collections.singletonList(metadata("GET /api/dev/newenergy/webapi", invalidWebApiDetails()));
      }
    }));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());
    List<WebAPIDevController.TreeWebAPIModel> nodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()));
    assertTrue(nodes.stream().noneMatch(node -> "api".equals(node.getNodeType())));
  }

  @Test
  void treeShouldSkipNullMetadataItemInGroupedList() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        null,
        metadata("GET /api/dev/newenergy/webapi", webApiDetails()))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertEquals(1, apiNodes.size());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
  }

  @Test
  void treeShouldSkipMetadataWithNullDetailsAndKeepValidEntry() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        metadata("GET /api/dev/newenergy/webapi", nullWebApiDetails()),
        metadata("GET /api/dev/newenergy/webapi", webApiDetails()))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertEquals(1, apiNodes.size());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
  }

  @Test
  void treeShouldSkipMetadataWithMalformedDetailsAndKeepValidEntry() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        metadata("GET /api/dev/newenergy/webapi", malformedWebApiDetails()),
        metadata("GET /api/dev/newenergy/webapi", webApiDetails()))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertEquals(1, apiNodes.size());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
  }

  @Test
  void treeShouldKeepValidGroupedApiWhenInvalidMetadataComesLater() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        metadata("GET /api/dev/newenergy/webapi", webApiDetails()),
        metadata("GET /api/dev/newenergy/webapi", invalidWebApiDetails()))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertEquals(1, apiNodes.size());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
    assertTrue(apiNodes.stream().anyMatch(node -> "GET /api/dev/newenergy/webapi".equals(node.getApi())));
    assertTrue(apiNodes.stream().noneMatch(node -> "GET /api/dev/newenergy/invalid".equals(node.getApi())));
  }

  @Test
  void treeShouldKeepValidGroupedApiWhenInvalidMetadataComesFirst() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(groupedMetadataProvider(
        metadata("GET /api/dev/newenergy/webapi", invalidWebApiDetails()),
        metadata("GET /api/dev/newenergy/webapi", webApiDetails()))));

    ItemsWebApiResult<?> result = controller.tree("newenergy");

    assertEquals(0, result.getCode());

    List<WebAPIDevController.TreeWebAPIModel> apiNodes = flatten(result.getData().getItems().stream()
        .map(WebAPIDevController.TreeWebAPIModel.class::cast)
        .collect(Collectors.toList()))
        .stream()
        .filter(node -> "api".equals(node.getNodeType()))
        .collect(Collectors.toList());

    assertEquals(1, apiNodes.size());
    assertNotNull(apiNodes.get(0).getCurrentModel());
    assertEquals("后端接口", apiNodes.get(0).getCurrentModel().getSummary());
    assertTrue(apiNodes.stream().anyMatch(node -> "GET /api/dev/newenergy/webapi".equals(node.getApi())));
  }

  @Test
  void getUiModelShouldHandleApiNameCasingDifferences() {
    WebAPIDevController controller = new WebAPIDevController(new LogicModelDevelopServiceImpl() {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Collections.emptyList();
      }
    }, Collections.singletonList(webApiProvider()));

    WebApiResult<?> result = controller.getUiModel("newenergy", "get", "/api/dev/newenergy/webapi");

    assertEquals(0, result.getCode());
    assertTrue(result.getData() instanceof Map);
    Map<?, ?> data = (Map<?, ?>) result.getData();
    assertEquals("后端接口", data.get("summary"));
  }

  @Test
  void proCodeSimpleWebApiMetadataShouldDeserializeIntoLowcodeModel() {
    SimpleWebAPIModel producerModel = SimpleWebAPIModel.builder()
        .get("/api/dev/newenergy/webapi")
        .scope(WebAPIScope.PUBLIC)
        .handlerType(HandlerType.REQUEST_MAPPING)
        .summary("后端接口")
        .description("PRO_CODE webapi metadata payload")
        .operationId("webapi_create")
        .tag("webapi")
        .extraProp("className", "com.ouroboros.webapi.SampleController")
        .extraProp("methodName", "create(java.lang.String)")
        .build();

    Map<String, Object> details = Json.toMap(Json.toJsonString(producerModel));
    LowcodeWebAPIModel model = Json.toBean(LowcodeWebAPIModel.class, Json.toJsonString(details));

    assertNotNull(model);
    assertEquals("GET", model.getMethod());
    assertEquals("/api/dev/newenergy/webapi", model.getPath());
    assertEquals(WebAPIScope.PUBLIC, model.getScope());
    assertEquals(HandlerType.REQUEST_MAPPING, model.getHandlerType());
    assertEquals("后端接口", model.getSummary());
    assertEquals("com.ouroboros.webapi.SampleController", model.getExtraProp("className").orElse(null));
    assertEquals("create(java.lang.String)", model.getExtraProp("methodName").orElse(null));
    assertTrue(model.getExtraProp("tags").isPresent());
    assertTrue(model.getExtraProp("description").isPresent());
    assertTrue(model.getExtraProp("operationId").isPresent());
  }

  private static MetadataProvider webApiProvider() {
    return new MetadataProvider() {
      @Override
      public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
        if (!"webapi".equals(moduleName)) {
          return Collections.emptyList();
        }

        Metadata metadata = new Metadata();
        metadata.setName("GET /api/dev/newenergy/webapi");
        metadata.setLabel("后端接口");
        metadata.setPriority(1);
        metadata.setDetails(webApiDetails());
        return Collections.singletonList(metadata);
      }
    };
  }

  private static Map<String, Object> webApiDetails() {
    return webApiDetails("get", "/api/dev/newenergy/webapi", "后端接口");
  }

  private static Map<String, Object> webApiDetails(String method, String path, String summary) {
    LowcodeWebAPIModel model = new LowcodeWebAPIModel();
    model.setMethod(method);
    model.setPath(path);
    model.setScope(WebAPIScope.PUBLIC);
    model.setHandlerType(HandlerType.REQUEST_MAPPING);
    model.setSummary(summary);

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("method", model.getMethod());
    details.put("path", model.getPath());
    details.put("scope", model.getScope());
    details.put("handlerType", model.getHandlerType());
    details.put("summary", model.getSummary());
    return details;
  }

  private static Map<String, Object> invalidWebApiDetails() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("method", "get");
    details.put("path", "/api/dev/newenergy/invalid");
    details.put("scope", WebAPIScope.PUBLIC);
    details.put("handlerType", null);
    details.put("summary", "无效接口");
    return details;
  }

  private static Map<String, Object> malformedWebApiDetails() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("method", "get");
    details.put("path", "/api/dev/newenergy/webapi");
    details.put("scope", Collections.singletonMap("value", WebAPIScope.PUBLIC));
    details.put("handlerType", Collections.singletonMap("value", HandlerType.REQUEST_MAPPING));
    details.put("summary", "坏结构接口");
    return details;
  }

  private static Map<String, Object> nullWebApiDetails() {
    return null;
  }

  private static Metadata metadata(String name, Map<String, Object> details) {
    Metadata metadata = new Metadata();
    metadata.setName(name);
    metadata.setLabel("后端接口");
    metadata.setPriority(1);
    metadata.setDetails(details);
    return metadata;
  }

  private static MetadataProvider groupedMetadataProvider(Metadata... metadataList) {
    List<Metadata> groupedMetadata = new ArrayList<>();
    Collections.addAll(groupedMetadata, metadataList);
    return new MetadataProvider() {
      @Override
      public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
        if (!"webapi".equals(moduleName)) {
          return Collections.emptyList();
        }

        return groupedMetadata;
      }
    };
  }

  private static List<WebAPIDevController.TreeWebAPIModel> flatten(List<WebAPIDevController.TreeWebAPIModel> nodes) {
    List<WebAPIDevController.TreeWebAPIModel> result = new ArrayList<>();
    nodes.forEach(node -> flatten(node, result));
    return result;
  }

  private static void flatten(WebAPIDevController.TreeWebAPIModel node, List<WebAPIDevController.TreeWebAPIModel> result) {
    result.add(node);
    node.getChildren().forEach(child -> flatten(child, result));
  }
}
