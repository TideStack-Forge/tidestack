package com.ouroboros.dev.app.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.UrlHandlerFilter;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.dev.app.service.AppTemplateService;
import com.ouroboros.dev.app.service.impl.AppManagementServiceImpl;
import com.ouroboros.model.AppInfo;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppManagementControllerTest {

  @TempDir
  Path workspace;

  private final DevelopWorkspaceContext developWorkspaceContext = mock(DevelopWorkspaceContext.class);
  private final VCSManagerCenter vcsManagerCenter = mock(VCSManagerCenter.class);
  private final AppCategoryService appCategoryService = mock(AppCategoryService.class);
  private final AppTemplateService appTemplateService = mock(AppTemplateService.class);
  private final AppEditScopeService appEditScopeService = mock(AppEditScopeService.class);
  private final AppEditAccessEvaluator appEditAccessEvaluator = mock(AppEditAccessEvaluator.class);
  private final org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    AppManagementServiceImpl appManagementService = new AppManagementServiceImpl();
    when(developWorkspaceContext.getDevRootDir()).thenReturn(workspace.toString());
    ReflectionTestUtils.setField(appManagementService, "developWorkspaceContext", developWorkspaceContext);
    ReflectionTestUtils.setField(appManagementService, "vcsManagerCenter", vcsManagerCenter);
    ReflectionTestUtils.setField(appManagementService, "appCategoryService", appCategoryService);
    ReflectionTestUtils.setField(appManagementService, "eventPublisher", eventPublisher);
    ReflectionTestUtils.setField(appManagementService, "appEditScopeService", appEditScopeService);
    ReflectionTestUtils.setField(appManagementService, "appEditAccessEvaluator", appEditAccessEvaluator);

    createAppDirectory("sales-app");
    createAppDirectory("hr-app");
    when(appEditAccessEvaluator.canView("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canView("hr-app", "alice")).thenReturn(false);
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);

    AppManagementController controller = new AppManagementController(appManagementService, appCategoryService, appTemplateService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .addFilters(UrlHandlerFilter.trailingSlashHandler("/api/**").wrapRequest().build())
        .build();

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("alice", "N/A", Collections.emptyList()));
    CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(new DefaultSecurityIdentity(() -> "alice")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void listAppsHttpEndpointOnlyReturnsAuthorizedApps() throws Exception {
    mockMvc.perform(get("/api/app"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].appInfo.name").value("sales-app"))
        .andExpect(jsonPath("$.data.items[0].canManageEditAccess").value(true));
  }

  @Test
  void listAppsHttpEndpointAcceptsTrailingSlash() throws Exception {
    mockMvc.perform(get("/api/app/"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].appInfo.name").value("sales-app"));
  }

  private void createAppDirectory(String appName) throws Exception {
    Path appPath = workspace.resolve(appName);
    Files.createDirectories(appPath);
    Files.write(appPath.resolve("app.metadata.json"),
        Json.toPrettyJsonString(new AppInfo().setName(appName)).getBytes(StandardCharsets.UTF_8));
  }
}
