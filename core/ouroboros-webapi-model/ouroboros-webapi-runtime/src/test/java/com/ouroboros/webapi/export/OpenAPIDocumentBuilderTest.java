package com.ouroboros.webapi.export;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.OpenAPIDocumentModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;

import static org.junit.jupiter.api.Assertions.*;

class OpenAPIDocumentBuilderTest {

  private final OpenAPIDocumentBuilder builder = new OpenAPIDocumentBuilder();

  @Test
  void shouldBuildPathsFromRegisteredWebApiModels() {
    WebAPIModel listUsers = SimpleWebAPIModel.builder()
        .get("/users")
        .scope(WebAPIScope.PUBLIC)
        .summary("List users")
        .response("200", ResponseModel.builder().description("ok").build())
        .build();

    WebAPIModel createUser = SimpleWebAPIModel.builder()
        .post("/users")
        .scope(WebAPIScope.PUBLIC)
        .summary("Create user")
        .requestBody(RequestBodyModel.builder().required(true).build())
        .response("201", ResponseModel.builder().description("created").build())
        .build();

    OpenAPIDocumentModel document = builder.build(
        Arrays.asList(listUsers, createUser),
        OpenAPIExportRequest.builder().title("Demo").version("dev").build(),
        Collections.emptyList());

    assertEquals("3.1.0", document.getOpenapi());
    assertEquals("Demo", document.getInfo().getTitle());
    assertEquals("dev", document.getInfo().getVersion());
    assertEquals("List users", document.getPaths().get("/users").getGet().getSummary());
    assertEquals("Create user", document.getPaths().get("/users").getPost().getSummary());
  }

  @Test
  void shouldExtractResponseSchemasIntoComponentsAndRewriteRefs() {
    SchemaModel userSchema = SchemaModel.builder()
        .title("User")
        .objectType()
        .properties(new LinkedHashMap<String, SchemaModel>() {{
          put("id", SchemaModel.builder().stringType().build());
        }})
        .build();

    WebAPIModel listUsers = SimpleWebAPIModel.builder()
        .get("/users")
        .summary("List users")
        .response("200", jsonResponse(userSchema))
        .build();

    OpenAPIDocumentModel document = builder.build(
        Collections.singletonList(listUsers),
        OpenAPIExportRequest.builder().title("Demo").version("dev").build(),
        Collections.emptyList());

    assertTrue(document.getComponents().getSchemas().containsKey("User"));
    assertEquals(
        "#/components/schemas/User",
        document.getPaths().get("/users").getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema().getRef()
    );
    assertEquals(
        "User",
        document.getComponents().getSchemas().get("User").getTitle()
    );
  }

  @Test
  void shouldDeduplicateIdenticalSchemasRenameCollisionsAndPreserveExistingRefs() {
    SchemaModel sharedUser = titledObjectSchema("User", "id");
    SchemaModel sharedUserCopy = titledObjectSchema("User", "id");
    SchemaModel conflictingUser = titledObjectSchema("User", "email");
    SchemaModel existingRef = SchemaModel.builder()
        .ref("#/components/schemas/ExistingUser")
        .build();

    WebAPIModel first = SimpleWebAPIModel.builder()
        .get("/users/one")
        .summary("one")
        .response("200", jsonResponse(sharedUser))
        .build();
    WebAPIModel second = SimpleWebAPIModel.builder()
        .get("/users/two")
        .summary("two")
        .response("200", jsonResponse(sharedUserCopy))
        .build();
    WebAPIModel third = SimpleWebAPIModel.builder()
        .get("/users/three")
        .summary("three")
        .response("200", jsonResponse(conflictingUser))
        .build();
    WebAPIModel fourth = SimpleWebAPIModel.builder()
        .get("/users/four")
        .summary("four")
        .response("200", jsonResponse(existingRef))
        .build();

    OpenAPIDocumentModel document = builder.build(
        Arrays.asList(first, second, third, fourth),
        OpenAPIExportRequest.builder().title("Demo").version("dev").build(),
        Collections.emptyList());

    assertEquals(2, document.getComponents().getSchemas().size());
    assertTrue(document.getComponents().getSchemas().containsKey("User"));
    assertTrue(document.getComponents().getSchemas().containsKey("User2"));
    assertEquals(
        "#/components/schemas/User",
        document.getPaths().get("/users/one").getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema().getRef()
    );
    assertEquals(
        "#/components/schemas/User",
        document.getPaths().get("/users/two").getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema().getRef()
    );
    assertEquals(
        "#/components/schemas/User2",
        document.getPaths().get("/users/three").getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema().getRef()
    );
    assertEquals(
        "#/components/schemas/ExistingUser",
        document.getPaths().get("/users/four").getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema().getRef()
    );
  }

  @Test
  void shouldNormalizeNullableSchemasAndExposeJsonSchemaDialect() {
    LinkedHashMap<String, SchemaModel> properties = new LinkedHashMap<>();
    properties.put("msg", SchemaModel.builder().stringType().nullable(true).build());

    WebAPIModel getMessage = SimpleWebAPIModel.builder()
        .get("/messages/latest")
        .summary("Latest message")
        .response("200", jsonResponse(
            SchemaModel.builder()
                .title("MessageEnvelope")
                .objectType()
                .properties(properties)
                .build()))
        .build();

    OpenAPIDocumentModel document = builder.build(
        Collections.singletonList(getMessage),
        OpenAPIExportRequest.builder().title("Demo").version("dev").build(),
        Collections.emptyList());

    assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", document.getJsonSchemaDialect());

    SchemaModel envelope = document.getComponents().getSchemas().get("MessageEnvelope");
    assertNotNull(envelope);
    SchemaModel message = envelope.getProperties().get("msg");
    assertNotNull(message);
    assertNull(message.getNullable());
    assertEquals(Arrays.asList("string", "null"), message.getTypes());
  }

  private ResponseModel jsonResponse(SchemaModel schema) {
    return ResponseModel.builder()
        .description("ok")
        .content(Collections.singletonMap(
            "application/json",
            MediaTypeModel.builder().schema(schema).build()
        ))
        .build();
  }

  private SchemaModel titledObjectSchema(String title, String propertyName) {
    LinkedHashMap<String, SchemaModel> properties = new LinkedHashMap<>();
    properties.put(propertyName, SchemaModel.builder().stringType().build());
    return SchemaModel.builder()
        .title(title)
        .objectType()
        .properties(properties)
        .build();
  }
}
