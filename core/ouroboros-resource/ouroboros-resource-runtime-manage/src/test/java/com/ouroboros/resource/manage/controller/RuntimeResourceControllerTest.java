package com.ouroboros.resource.manage.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.resource.manage.service.RuntimeResourceManageService;
import com.ouroboros.resource.model.ResourceMetadata;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.runtime.provider.AppDefaultResourceProvider;
import com.ouroboros.resource.runtime.provider.DefaultResourceService;
import com.ouroboros.resource.runtime.provider.RuntimeOverrideResourceProvider;
import com.ouroboros.util.Json;

class RuntimeResourceControllerTest {

  @TempDir
  Path tempDir;

  private MockMvc mockMvc;
  private RuntimeResourceManageService manageService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @BeforeEach
  void setUp() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "frontend/templates/default.txt", "default-template");
    writeSidecar(appDefaultRoot, "frontend/templates/default.txt", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/default.txt")
        .setContentType("text/plain")
        .setDisplayName("Default Template"));
    writeFile(appDefaultRoot, "frontend/templates/mail/welcome.html", "<h1>welcome</h1>");
    writeSidecar(appDefaultRoot, "frontend/templates/mail/welcome.html", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/mail/welcome.html")
        .setContentType("text/html")
        .setDisplayName("Welcome Template"));

    manageService = new RuntimeResourceManageService(
        new DefaultResourceService(
            new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
            new AppDefaultResourceProvider(appDefaultRoot)),
        new AppDefaultResourceProvider(appDefaultRoot),
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString())))
    );

    mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeResourceController(manageService)).build();
  }

  @Test
  void listAndRestoreDefaultUseTheRuntimeResourceContract() throws Exception {
    mockMvc.perform(multipart("/api/sys/resource/runtime/content")
            .file(new MockMultipartFile(
                "file",
                "default.txt",
                "text/plain",
                "override-template".getBytes(StandardCharsets.UTF_8)))
            .param("scope", "frontend")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.effectiveLayer").value("runtime_override"))
        .andExpect(jsonPath("$.data.hasOverride").value(true));

    mockMvc.perform(get("/api/sys/resource/runtime")
            .param("scope", "frontend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[*].key", hasItem("templates/default.txt")));

    mockMvc.perform(post("/api/sys/resource/runtime/restore")
            .param("scope", "frontend")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.effectiveLayer").value("app_default"))
        .andExpect(jsonPath("$.data.hasOverride").value(false))
        .andExpect(jsonPath("$.data.defaultExists").value(true));
  }

  @Test
  void downloadContentReturnsTheCurrentEffectiveBytes() throws Exception {
    mockMvc.perform(get("/api/sys/resource/runtime/content")
            .param("scope", "frontend")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk())
        .andExpect(content().bytes("default-template".getBytes(StandardCharsets.UTF_8)));

    mockMvc.perform(multipart("/api/sys/resource/runtime/content")
            .file(new MockMultipartFile(
                "file",
                "default.txt",
                "text/plain",
                "override-template".getBytes(StandardCharsets.UTF_8)))
            .param("scope", "frontend")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/sys/resource/runtime/content")
            .param("scope", "frontend")
            .param("key", "templates/default.txt"))
        .andExpect(status().isOk())
        .andExpect(content().bytes("override-template".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void directoryBrowsingProvidesTreeAndDirectoryScopedList() throws Exception {
    mockMvc.perform(get("/api/sys/resource/runtime/directories")
            .param("scope", "frontend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[*].value", hasItem("templates")))
        .andExpect(jsonPath("$.data.items[0].children[*].value", hasItem("templates/mail")));

    mockMvc.perform(get("/api/sys/resource/runtime")
            .param("scope", "frontend")
            .param("directory", "templates/mail"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[*].key", hasItem("templates/mail/welcome.html")))
        .andExpect(jsonPath("$.data.items[0].key").value("templates/mail/welcome.html"));
  }

  @Test
  void uploadIgnoresSpoofableHeaderWhenNoAuthenticationExists() throws Exception {
    mockMvc.perform(multipart("/api/sys/resource/runtime/content")
            .file(new MockMultipartFile(
                "file",
                "default.txt",
                "text/plain",
                "override-template".getBytes(StandardCharsets.UTF_8)))
            .param("scope", "frontend")
            .param("key", "templates/default.txt")
            .header("X-User-Id", "spoofed-user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.lastModifiedBy").value("system"));
  }

  @Test
  void uploadUsesAuthenticatedPrincipalInsteadOfSpoofableHeader() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("actual-user", "n/a", "ROLE_ADMIN"));

    mockMvc.perform(multipart("/api/sys/resource/runtime/content")
            .file(new MockMultipartFile(
                "file",
                "default.txt",
                "text/plain",
                "override-template".getBytes(StandardCharsets.UTF_8)))
            .param("scope", "frontend")
            .param("key", "templates/default.txt")
            .header("X-User-Id", "spoofed-user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.lastModifiedBy").value("actual-user"));
  }

  private static void writeFile(Path root, String scopedPath, String content) throws Exception {
    Path file = root.resolve(scopedPath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeSidecar(Path root, String scopedPath, ResourceMetadata metadata) throws Exception {
    Path sidecar = root.resolve(scopedPath + ".resource.json");
    Files.createDirectories(sidecar.getParent());
    Files.write(sidecar, Json.toPrettyJsonString(metadata).getBytes(StandardCharsets.UTF_8));
  }
}
