package com.ouroboros.webapi.openapi;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAPIDocumentModelTest {

  @Test
  void shouldStoreDocumentInfoPathsAndComponents() {
    OperationModel operation = OperationModel.builder()
        .summary("List users")
        .response("200", ResponseModel.builder().description("ok").build())
        .build();

    OpenAPIDocumentModel model = OpenAPIDocumentModel.builder()
        .openapi("3.1.0")
        .info(InfoModel.builder().title("Demo").version("dev").build())
        .path("/users", PathItemModel.builder().get(operation).build())
        .components(ComponentsModel.builder()
            .schema("User", SchemaModel.builder().title("User").objectType().build())
            .build())
        .build();

    assertEquals("3.1.0", model.getOpenapi());
    assertEquals("Demo", model.getInfo().getTitle());
    assertEquals("List users", model.getPaths().get("/users").getGet().getSummary());
    assertTrue(model.getComponents().getSchemas().containsKey("User"));
  }

  @Test
  void shouldStoreOperationFieldsAndPathItemMethods() {
    ParameterModel parameter = ParameterModel.builder()
        .name("verbose")
        .in(ParameterLocation.QUERY)
        .schema(SchemaModel.builder().booleanType().build())
        .build();
    RequestBodyModel requestBody = RequestBodyModel.builder()
        .description("payload")
        .required(true)
        .build();
    ResponseModel response = ResponseModel.builder()
        .description("ok")
        .build();
    SecurityRequirementModel security = SecurityRequirementModel.of("bearerAuth", Collections.singletonList("users:read"));
    ServerModel server = ServerModel.builder()
        .url("https://api.example.com")
        .description("default")
        .build();
    ExternalDocsModel externalDocs = ExternalDocsModel.builder()
        .description("reference")
        .url("https://docs.example.com")
        .build();

    OperationModel getOperation = OperationModel.builder()
        .summary("List users")
        .description("Loads users")
        .operationId("listUsers")
        .tags(Arrays.asList("users", "admin"))
        .parameters(Collections.singletonList(parameter))
        .requestBody(requestBody)
        .response("200", response)
        .deprecated(true)
        .security(Collections.singletonList(security))
        .servers(Collections.singletonList(server))
        .externalDocs(externalDocs)
        .build();

    OperationModel postOperation = OperationModel.builder()
        .summary("Create user")
        .build();

    PathItemModel pathItem = PathItemModel.builder()
        .get(getOperation)
        .post(postOperation)
        .build();

    assertEquals("Loads users", pathItem.getGet().getDescription());
    assertEquals("listUsers", pathItem.getGet().getOperationId());
    assertEquals(Arrays.asList("users", "admin"), pathItem.getGet().getTags());
    assertEquals(Collections.singletonList(parameter), pathItem.getGet().getParameters());
    assertEquals(requestBody, pathItem.getGet().getRequestBody());
    assertEquals(response, pathItem.getGet().getResponses().get("200"));
    assertEquals(Boolean.TRUE, pathItem.getGet().getDeprecated());
    assertEquals(Collections.singletonList(security), pathItem.getGet().getSecurity());
    assertEquals(Collections.singletonList(server), pathItem.getGet().getServers());
    assertEquals(externalDocs, pathItem.getGet().getExternalDocs());
    assertEquals("Create user", pathItem.getPost().getSummary());
  }
}
