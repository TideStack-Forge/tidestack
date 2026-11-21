package com.ouroboros.dev.codeintelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.util.Json;

class CodeContextHttpTest {
  @Test void autoConfigurationExposesTheVersionedReadOnlyContextEndpoint() {
    var drafts = new java.util.ArrayList<String>();
    new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CodeIntelligenceAutoConfiguration.class))
        .withBean(com.ouroboros.devhelper.dev.DevHelperCommandService.class,
            () -> org.mockito.Mockito.mock(com.ouroboros.devhelper.dev.DevHelperCommandService.class))
        .withBean(CodeContextProvider.class, () -> new CodeContextProvider() {
          public Set<String> resourceTypes() { return Set.of("fixture"); }
          public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView view) {
            drafts.add(request.draft().path("script").asText());
            return new CodeContextDescription("spel", List.of(), TypeShape.scalar("string"), List.of(), false);
          }
        }).run(context -> {
          assertNull(context.getStartupFailure());
          var mvc = MockMvcBuilders.standaloneSetup(context.getBean(CodeContextController.class)).build();
          var request = new CodeContextRequest("inmemory://model", 4, 7, "fixture.script", "spel",
              new CodeContextRequest.Resource("fixture", "model"), new CodeContextRequest.Location(null, "/script"),
              Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, "{\"script\":\"$input.name\"}"));
          mvc.perform(post("/api/dev/app/code-intelligence/context").contentType(MediaType.APPLICATION_JSON).content(Json.toJsonString(request)))
              .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
              .andExpect(jsonPath("$.data.documentId").value("inmemory://model"))
              .andExpect(jsonPath("$.data.documentVersion").value(4))
              .andExpect(jsonPath("$.data.draftRevision").value(7))
              .andExpect(jsonPath("$.data.runtimeStatus").value("UNAVAILABLE"))
              .andExpect(jsonPath("$.data.expectedResult.schema.type").value("string"));
          assertEquals(List.of("$input.name"), drafts);
        });
  }
}
