package com.ouroboros.webapi.export;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.AppContext;
import com.ouroboros.model.AppInfo;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModelCatalog;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.openapi.OpenAPIDocumentModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.ServerModel;

import static org.junit.jupiter.api.Assertions.*;

class OpenAPIDocumentServiceTest {

  @Test
  void shouldFilterModelsAndApplyDefaultInfoFallbacks() {
    OpenAPIDocumentService service = new OpenAPIDocumentService(
        new WebAPIModelCatalog(() -> Arrays.asList(
            SimpleWebAPIModel.builder()
                .get("/users")
                .scope(WebAPIScope.PUBLIC)
                .handlerType(HandlerType.REQUEST_MAPPING)
                .tag("users")
                .summary("List users")
                .response("200", ResponseModel.builder().description("ok").build())
                .build(),
            SimpleWebAPIModel.builder()
                .get("/internal/jobs")
                .scope(WebAPIScope.INTERNAL)
                .handlerType(HandlerType.LOGIC_FLOW)
                .tag("jobs")
                .summary("List jobs")
                .response("200", ResponseModel.builder().description("ok").build())
                .build()
        )),
        new OpenAPIDocumentBuilder(),
        appContext("CRM API", "crm")
    );

    OpenAPIDocumentModel document = service.export(OpenAPIExportRequest.builder()
        .scopes(Collections.singletonList(WebAPIScope.PUBLIC))
        .handlerTypes(Collections.singletonList(HandlerType.REQUEST_MAPPING))
        .tags(Collections.singletonList("users"))
        .pathPrefixes(Collections.singletonList("/users"))
        .apis(Collections.singletonList("GET /users"))
        .build());

    assertEquals("CRM API", document.getInfo().getTitle());
    assertEquals("dev", document.getInfo().getVersion());
    assertEquals(1, document.getPaths().size());
    assertTrue(document.getPaths().containsKey("/users"));
  }

  @Test
  void shouldAllowRequestOverridesAndMergeDocumentServers() {
    OpenAPIDocumentService service = new OpenAPIDocumentService(
        new WebAPIModelCatalog(() -> Collections.singletonList(
            SimpleWebAPIModel.builder()
                .get("/users")
                .scope(WebAPIScope.PUBLIC)
                .handlerType(HandlerType.REQUEST_MAPPING)
                .summary("List users")
                .server(ServerModel.builder().url("https://internal.example.com").description("internal").build())
                .response("200", ResponseModel.builder().description("ok").build())
                .build()
        )),
        new OpenAPIDocumentBuilder(),
        appContext("", "crm")
    );

    OpenAPIDocumentModel document = service.export(OpenAPIExportRequest.builder()
        .title("Public API")
        .version("2026.03")
        .serverUrls(Collections.singletonList("https://api.example.com"))
        .build());

    assertEquals("Public API", document.getInfo().getTitle());
    assertEquals("2026.03", document.getInfo().getVersion());
    assertEquals(2, document.getServers().size());
    assertEquals("https://api.example.com", document.getServers().get(0).getUrl());
    assertEquals("https://internal.example.com", document.getServers().get(1).getUrl());
  }

  private AppContext appContext(String title, String name) {
    return new AppContext() {
      @Override
      public String getHomePath() {
        return ".";
      }

      @Override
      public Optional<AppInfo> getAppInfo() {
        return Optional.of(new AppInfo()
            .setTitle(title)
            .setName(name));
      }
    };
  }
}
