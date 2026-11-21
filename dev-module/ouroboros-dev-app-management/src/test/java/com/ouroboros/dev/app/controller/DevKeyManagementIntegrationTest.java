package com.ouroboros.dev.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jayway.jsonpath.JsonPath;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.DevKeyEntity;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.runtime.AuthenticationPrincipalTestAppProfileResolver;
import com.ouroboros.dev.app.service.runtime.DatabaseDebugKeyMaterialProvider;
import com.ouroboros.dev.app.service.runtime.DebugKeyGenerationService;
import com.ouroboros.dev.app.service.runtime.DebugKeyManagementService;
import com.ouroboros.dev.app.service.runtime.DebugKeyMaterialProvider;
import com.ouroboros.dev.app.service.runtime.DebugProfileAuthorizationService;
import com.ouroboros.dev.app.service.runtime.DebugSessionWorkspaceStorage;
import com.ouroboros.dev.app.service.runtime.DefaultDebugProfileAuthorizationService;
import com.ouroboros.dev.app.service.runtime.DevKeyStore;
import com.ouroboros.dev.app.service.runtime.InMemoryDevKeyDataModel;
import com.ouroboros.dev.app.service.runtime.WorkspaceDebugSessionWorkspaceStorage;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

@WebMvcTest
@Import({DevKeyManagementController.class, RuntimeDebugProfileController.class, DevKeyManagementIntegrationTest.TestConfig.class,
    WebApiResultHttpStatusAdvice.class, DevKeyManagementIntegrationTest.BusinessExceptionAdvice.class})
class DevKeyManagementIntegrationTest {

  private static final Path TEST_ROOT = createTestRoot();
  private static final Path WORKSPACE_ROOT = TEST_ROOT.resolve("workspace");
  private static final Path DEBUG_SUPPORT_ROOT = TEST_ROOT.resolve("debug-support");
  private static final Path DEVELOP_SUPPORT_ROOT = TEST_ROOT.resolve("develop-support");

  static {
    try {
      Files.createDirectories(WORKSPACE_ROOT);
      Files.createDirectories(DEBUG_SUPPORT_ROOT);
      Files.createDirectories(DEVELOP_SUPPORT_ROOT);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("ouroboros.dev.workspaceRoot", () -> WORKSPACE_ROOT.toString());
    registry.add("ouroboros.dev.debugSupportFilesDir", () -> DEBUG_SUPPORT_ROOT.toString());
    registry.add("ouroboros.dev.developSupportFilesDir", () -> DEVELOP_SUPPORT_ROOT.toString());
    registry.add("ouroboros.dev.platformUrl", () -> "https://dev.ouroboros.example");
  }

  @AfterEach
  void tearDown() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
    deleteDirectoryContents(DEBUG_SUPPORT_ROOT);
    deleteDirectoryContents(DEVELOP_SUPPORT_ROOT);
  }

  @BeforeEach
  void setUp() {
    installSecurityContextBridge();
  }

  @Test
  @WithMockUser(username = "alice")
  void shouldCompleteDevKeyFlowWithAppScopedEndpoints() throws Exception {
    String appName = "crm";

    String generatedBody = mockMvc.perform(post("/api/dev/{appName}/dev-keys", appName)
            .with(csrf())
            .contentType("application/json")
            .content("{\"description\":\"integration\",\"expiryDays\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.appName").value(appName))
        .andExpect(jsonPath("$.data.userIdentity").value("alice"))
        .andExpect(jsonPath("$.data.profileId").value("alice"))
        .andExpect(jsonPath("$.data.devKey").value(matchesPattern("[0-9a-f]{32}")))
        .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
        .andReturn()
        .getResponse()
        .getContentAsString();

    String generatedKey = jsonValue(generatedBody, "$.data.devKey");

    mockMvc.perform(get("/api/dev/{appName}/dev-keys", appName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.devKey").value(generatedKey.substring(0, 8) + "****"));

    mockMvc.perform(get("/api/dev/{appName}/dev-keys/plugin-config", appName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.schemaVersion").value(1))
        .andExpect(jsonPath("$.data.developmentServerUrl").value("https://dev.ouroboros.example"))
        .andExpect(jsonPath("$.data.appName").value(appName))
        .andExpect(jsonPath("$.data.devKey").value(generatedKey));

    mockMvc.perform(get("/api/dev/{appName}/dev-keys/plugin-config/download", appName))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(jsonPath("$.schemaVersion").value(1))
        .andExpect(jsonPath("$.developmentServerUrl").value("https://dev.ouroboros.example"))
        .andExpect(jsonPath("$.appName").value(appName))
        .andExpect(jsonPath("$.devKey").value(generatedKey));

    mockMvc.perform(post("/api/dev/{appName}/authorize", appName)
            .with(csrf())
            .contentType("application/json")
            .content("{\"devKey\":\"" + generatedKey + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.profileId").value("alice"))
        .andExpect(jsonPath("$.data.authorized").value(true));

    mockMvc.perform(post("/api/dev/{appName}/uploads", appName)
            .with(csrf())
            .contentType("application/json")
            .content("{\"devKey\":\"" + generatedKey + "\",\"files\":[{\"fileName\":\"agent.jar\",\"contentBase64\":\"aGVsbG8=\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.uploadBatchId").isNotEmpty())
        .andExpect(jsonPath("$.data.artifacts[0].fileName").value("agent.jar"));

    String rotatedBody = mockMvc.perform(put("/api/dev/{appName}/dev-keys", appName)
            .with(csrf())
            .contentType("application/json")
            .content("{\"description\":\"rotated\",\"expiryDays\":14}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.appName").value(appName))
        .andExpect(jsonPath("$.data.userIdentity").value("alice"))
        .andExpect(jsonPath("$.data.devKey").value(matchesPattern("[0-9a-f]{32}")))
        .andReturn()
        .getResponse()
        .getContentAsString();

    String rotatedKey = jsonValue(rotatedBody, "$.data.devKey");
    assertTrue(!generatedKey.equals(rotatedKey));

    mockMvc.perform(delete("/api/dev/{appName}/dev-keys", appName)
            .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/dev/{appName}/dev-keys", appName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  @WithMockUser(username = "alice")
  void shouldRejectAppScopedEndpointsWhenAppDoesNotExist() throws Exception {
    mockMvc.perform(get("/api/dev/{appName}/dev-keys", "missing-app"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("应用不存在: missing-app"));

    mockMvc.perform(post("/api/dev/{appName}/authorize", "missing-app")
            .with(csrf())
            .contentType("application/json")
            .content("{\"devKey\":\"dev-key-001\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("应用不存在: missing-app"));
  }

  @Test
  void shouldRejectUnauthenticatedManagementRequestsWithUnauthorized() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();

    mockMvc.perform(get("/api/dev/{appName}/dev-keys", "crm"))
        .andExpect(status().isUnauthorized());
  }

  private void installSecurityContextBridge() {
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(OuroborosAuthenticationHolder::adapt);
      }
    });
  }

  private static String jsonValue(String body, String jsonPath) {
    return JsonPath.read(body, jsonPath);
  }

  private static Path createTestRoot() {
    try {
      return Files.createTempDirectory("dev-key-management-it");
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void deleteDirectoryContents(Path directory) throws IOException {
    if (directory == null || !Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(directory))
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException exception) {
              throw new IllegalStateException(exception);
            }
      });
    }
  }

  private static DevelopWorkspaceContext workspaceContext(Path debugSupportDir, Path developSupportDir) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return developSupportDir.getParent().toString();
      }

      @Override
      public String getDevRootDir() {
        return developSupportDir.getParent().toString();
      }

      @Override
      public String getRunRootDir() {
        return developSupportDir.getParent().toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupportDir.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupportDir.toString();
      }
    };
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }

  @SpringBootConfiguration
  static class TestApplication {
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public DevelopWorkspaceContext developWorkspaceContext() {
      return DevKeyManagementIntegrationTest.workspaceContext(DEBUG_SUPPORT_ROOT, DEVELOP_SUPPORT_ROOT);
    }

    @Bean
    public DebugKeyGenerationService debugKeyGenerationService() {
      return new DebugKeyGenerationService(Duration.ofDays(90));
    }

    @Bean
    public TypedDataModel<String, DevKeyEntity> devKeyDataModel() {
      return new InMemoryDevKeyDataModel();
    }

    @Bean
    public DevKeyStore devKeyStore(TypedDataModel<String, DevKeyEntity> devKeyDataModel) {
      return new DevKeyStore(devKeyDataModel);
    }

    @Bean
    public DebugKeyMaterialProvider debugKeyMaterialProvider(DevKeyStore devKeyStore,
                                                             AuthenticationPrincipalTestAppProfileResolver profileResolver) {
      return new DatabaseDebugKeyMaterialProvider(devKeyStore, profileResolver);
    }

    @Bean
    public AuthenticationPrincipalTestAppProfileResolver authenticationPrincipalTestAppProfileResolver() {
      return new AuthenticationPrincipalTestAppProfileResolver();
    }

    @Bean
    public AppManagementService appManagementService() {
      AppManagementService appManagementService = org.mockito.Mockito.mock(AppManagementService.class);
      org.mockito.Mockito.when(appManagementService.appExists("crm")).thenReturn(true);
      return appManagementService;
    }

    @Bean
    public DebugKeyManagementService debugKeyManagementService(DebugKeyGenerationService debugKeyGenerationService,
                                                               DevKeyStore devKeyStore,
                                                               AuthenticationPrincipalTestAppProfileResolver profileResolver,
                                                               AppManagementService appManagementService,
                                                               @org.springframework.beans.factory.annotation.Value("${ouroboros.dev.platformUrl:}") String platformUrl) {
      return new DebugKeyManagementService(debugKeyGenerationService, devKeyStore, profileResolver, appManagementService, platformUrl);
    }

    @Bean
    public DebugProfileAuthorizationService debugProfileAuthorizationService(DebugKeyMaterialProvider debugKeyMaterialProvider,
                                                                             AppManagementService appManagementService) {
      return new DefaultDebugProfileAuthorizationService(debugKeyMaterialProvider, appManagementService);
    }

    @Bean
    public DebugSessionWorkspaceStorage debugSessionWorkspaceStorage(DevelopWorkspaceContext developWorkspaceContext) {
      return new WorkspaceDebugSessionWorkspaceStorage(developWorkspaceContext);
    }
  }
}
