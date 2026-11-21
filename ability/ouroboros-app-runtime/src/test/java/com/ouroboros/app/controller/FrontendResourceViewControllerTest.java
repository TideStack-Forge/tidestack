package com.ouroboros.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.AppContext;
import com.ouroboros.model.AppInfo;
import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceDescriptor;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.spi.ResourceService;
import com.ouroboros.security.annotation.Anonymous;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendResourceViewControllerTest {

  private MockMvc mockMvc;
  private RecordingResourceService resourceService;

  @BeforeEach
  void setUp() {
    resourceService = new RecordingResourceService();
    FrontendResourceViewController controller = new FrontendResourceViewController(resourceService, new TestAppContext());
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void runtimeViewReturnsEffectiveBytesInlineAndUsesCurrentAppContext() throws Exception {
    mockMvc.perform(get("/api/resource/view")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
        .andExpect(content().bytes("override-template".getBytes(StandardCharsets.UTF_8)));

    assertEquals("demo-app", resourceService.lastContext.getApplication());
    assertEquals(ResourceScope.FRONTEND, resourceService.lastContext.getScope());
    assertEquals("templates/default.txt", resourceService.lastContext.getKey());

    assertTrue(FrontendResourceViewController.class
        .getMethod("view", String.class)
        .isAnnotationPresent(Anonymous.class));
  }

  @Test
  void runtimeViewRejectsIllegalKeysWithHttp400() throws Exception {
    mockMvc.perform(get("/api/resource/view")
            .param("key", "../secrets.txt"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void runtimeViewReturnsHttp404WhenResourceMissing() throws Exception {
    resourceService.returnMissing();

    mockMvc.perform(get("/api/resource/view")
            .param("key", "templates/missing.txt"))
        .andExpect(status().isNotFound());
  }

  private static class TestAppContext implements AppContext {

    @Override
    public String getHomePath() {
      return ".";
    }

    @Override
    public Optional<AppInfo> getAppInfo() {
      return Optional.of(new AppInfo().setName("demo-app"));
    }
  }

  private static class RecordingResourceService implements ResourceService {
    private ResourceResolveContext lastContext;
    private ResourceReadResult nextResult = buildSuccessResult();

    @Override
    public ResourceReadResult read(ResourceResolveContext context) {
      lastContext = context;
      return nextResult;
    }

    private void returnMissing() {
      nextResult = new ResourceReadResult();
    }

    private static ResourceReadResult buildSuccessResult() {
      return new ResourceReadResult()
          .setEffectiveResource(Resource.builder()
              .descriptor(new ResourceDescriptor()
                  .setScope(ResourceScope.FRONTEND)
                  .setKey("templates/default.txt")
                  .setFileName("default.txt")
                  .setContentType("text/plain"))
              .content(new ByteArrayInputStream("override-template".getBytes(StandardCharsets.UTF_8)))
              .build());
    }

    @Override
    public Optional<Resource> getResource(String path) {
      return Optional.empty();
    }

    @Override
    public boolean exists(String path) {
      return false;
    }

    @Override
    public List<String> listResources(String directory) {
      return Collections.emptyList();
    }
  }
}
