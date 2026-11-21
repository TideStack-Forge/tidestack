package com.ouroboros.webapi.controller;

import java.util.Collections;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.AppContext;
import com.ouroboros.model.AppInfo;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.export.OpenAPIDocumentBuilder;
import com.ouroboros.webapi.export.OpenAPIDocumentService;
import com.ouroboros.webapi.WebAPIModelCatalog;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenAPIDocumentControllerTest {

  @Test
  void shouldReturnRawOpenApiJsonAndHonorQueryParameters() throws Exception {
    OpenAPIDocumentService service = new OpenAPIDocumentService(
        new WebAPIModelCatalog(() -> Collections.singletonList(
            SimpleWebAPIModel.builder()
                .get("/users")
                .scope(WebAPIScope.PUBLIC)
                .handlerType(HandlerType.REQUEST_MAPPING)
                .tag("users")
                .summary("List users")
                .response("200", ResponseModel.builder()
                    .description("ok")
                    .content(Collections.singletonMap(
                        "application/json",
                        MediaTypeModel.builder()
                            .schema(SchemaModel.builder().ref("#/components/schemas/User").build())
                            .build()
                    ))
                    .build())
                .build()
        )),
        new OpenAPIDocumentBuilder(),
        appContext("Demo", "crm")
    );

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
        new OpenAPIDocumentController(service, new ObjectMapper())
    ).build();

    mockMvc.perform(get("/api/sys/webapi/openapi.json")
            .param("scope", "public")
            .param("handlerType", "RequestMapping")
            .param("tag", "users")
            .param("pathPrefix", "/users")
            .param("api", "GET /users")
            .param("pretty", "true")
            .param("title", "Demo API")
            .param("version", "2026.03")
            .param("serverUrl", "https://api.example.com"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(Matchers.containsString("\"openapi\"")))
        .andExpect(content().string(Matchers.containsString("\"title\" : \"Demo API\"")))
        .andExpect(content().string(Matchers.containsString("\"url\" : \"https://api.example.com\"")))
        .andExpect(content().string(Matchers.containsString("\"$ref\" : \"#/components/schemas/User\"")))
        .andExpect(content().string(Matchers.containsString("\n  \"openapi\"")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("\"ref\" :"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString(" : null"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("\"code\""))));
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
