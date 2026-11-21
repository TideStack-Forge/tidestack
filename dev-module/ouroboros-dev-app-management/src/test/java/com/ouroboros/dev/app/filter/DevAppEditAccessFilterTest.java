package com.ouroboros.dev.app.filter;

import java.util.Collections;
import java.util.Optional;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ExtendWith(MockitoExtension.class)
class DevAppEditAccessFilterTest {

  @Mock
  private AppEditAccessEvaluator appEditAccessEvaluator;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Filter filter = new DevAppEditAccessFilterSupplier(appEditAccessEvaluator).getAuthorizeFilter();
    mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .addFilters(filter)
        .build();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("alice", "N/A", Collections.emptyList()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void protectsDevWriteApis() throws Exception {
    when(appEditAccessEvaluator.canEdit(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(put("/api/dev/sales-app/setting"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canEdit(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canView(anyString(), anyString());
  }

  @Test
  void protectsInitFormLockRequestEvenThoughItIsGet() throws Exception {
    when(appEditAccessEvaluator.canEdit(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/dev/sales-app/setting/init-form").param("lock", "true"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canEdit(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canView(anyString(), anyString());
  }

  @Test
  void rpcTestInitializationRequiresEditAccess() throws Exception {
    when(appEditAccessEvaluator.canEdit(eq("sales-app"), nullable(String.class))).thenReturn(false);
    mockMvc.perform(post("/api/dev/sales-app/rpc-model/Lookup/test/init-form"))
        .andExpect(status().isForbidden());
    verify(appEditAccessEvaluator).canEdit(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canView(anyString(), anyString());
  }

  @Test
  void protectsDevReadApisUsedByDirectRouteAccess() throws Exception {
    when(appEditAccessEvaluator.canView(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/dev/sales-app/version"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canView(eq("sales-app"), nullable(String.class));
  }

  @Test
  void protectsProjectGenerationCatalogWithViewAccess() throws Exception {
    when(appEditAccessEvaluator.canView(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/dev/sales-app/project-templates"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canView(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canEdit(anyString(), anyString());
  }

  @Test
  void protectsProjectGenerationDownloadWithEditAccess() throws Exception {
    when(appEditAccessEvaluator.canEdit(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(post("/api/dev/sales-app/project-generations"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canEdit(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canView(anyString(), anyString());
  }

  @Test
  void protectsAppExportAndAppWriteEndpointsOnly() throws Exception {
    when(appEditAccessEvaluator.canView(eq("sales-app"), nullable(String.class))).thenReturn(false);
    when(appEditAccessEvaluator.canManageApp(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/export"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/app/sales-app"))
        .andExpect(status().isForbidden());
  }

  @Test
  void skipsAnonymousLogoEndpointAndContainerEndpoints() throws Exception {
    mockMvc.perform(get("/api/dev/sales-app/setting/logo"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/app/container/base-images"))
        .andExpect(status().isOk());

    verifyNoInteractions(appEditAccessEvaluator);
  }

  @Test
  void returnsForbiddenWhenUserCannotViewOrEdit() throws Exception {
    when(appEditAccessEvaluator.canView(eq("sales-app"), nullable(String.class))).thenReturn(false);
    when(appEditAccessEvaluator.canEdit(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/app/sales-app/rename"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsUserWithoutEditAccessManageAuthorityOnEditAccessManagementApi() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isForbidden());
    mockMvc.perform(put("/api/app/sales-app/edit-access"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/app/sales-app/edit-access/users"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/app/sales-app/edit-access/roles"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/app/sales-app/edit-access/organizations"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator, times(5)).canManageEditAccess(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canView(anyString(), anyString());
    verify(appEditAccessEvaluator, never()).canEdit(anyString(), anyString());
  }

  @Test
  void allowsEditAccessEntryForUserWhoCanManageRulesButCannotViewApp() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess(eq("sales-app"), nullable(String.class))).thenReturn(true);

    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isOk());

    verify(appEditAccessEvaluator).canManageEditAccess(eq("sales-app"), nullable(String.class));
  }

  @Test
  void deleteAppRequiresManagePermissionRatherThanEditPermission() throws Exception {
    when(appEditAccessEvaluator.canManageApp(eq("sales-app"), nullable(String.class))).thenReturn(false);

    mockMvc.perform(delete("/api/app/sales-app"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canManageApp(eq("sales-app"), nullable(String.class));
    verify(appEditAccessEvaluator, never()).canEdit(eq("sales-app"), nullable(String.class));
  }

  @Test
  void allowsDeleteForAppManagerWithoutEditPermission() throws Exception {
    when(appEditAccessEvaluator.canManageApp(eq("sales-app"), nullable(String.class))).thenReturn(true);

    mockMvc.perform(delete("/api/app/sales-app"))
        .andExpect(status().isOk());

    verify(appEditAccessEvaluator).canManageApp(eq("sales-app"), nullable(String.class));
  }

  @Test
  void usesSeamIdentityAcrossEditAndManagementEndpoints() throws Exception {
    setSeamIdentity("seam-alice");

    when(appEditAccessEvaluator.canEdit("sales-app", "seam-alice")).thenReturn(true);
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "seam-alice")).thenReturn(true);
    when(appEditAccessEvaluator.canManageApp("sales-app", "seam-alice")).thenReturn(true);

    mockMvc.perform(put("/api/dev/sales-app/setting"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/app/sales-app"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isOk());

    verify(appEditAccessEvaluator).canEdit("sales-app", "seam-alice");
    verify(appEditAccessEvaluator, times(2)).canManageEditAccess("sales-app", "seam-alice");
    verify(appEditAccessEvaluator).canManageApp("sales-app", "seam-alice");
  }

  @Test
  void ignoresLegacySpringPrincipalWhenSeamIdentityMissing() throws Exception {
    setSpringAuthentication("legacy-principal");
    CurrentSecurityContextHolder.clearDelegate();

    when(appEditAccessEvaluator.canEdit(eq("sales-app"), isNull())).thenReturn(false);
    when(appEditAccessEvaluator.canManageEditAccess(eq("sales-app"), isNull())).thenReturn(false);
    when(appEditAccessEvaluator.canManageApp(eq("sales-app"), isNull())).thenReturn(false);
    when(appEditAccessEvaluator.canView(eq("sales-app"), isNull())).thenReturn(false);

    mockMvc.perform(put("/api/dev/sales-app/setting"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/app/sales-app"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canEdit(eq("sales-app"), isNull());
    verify(appEditAccessEvaluator, times(2)).canManageEditAccess(eq("sales-app"), isNull());
    verify(appEditAccessEvaluator).canManageApp(eq("sales-app"), isNull());
    verify(appEditAccessEvaluator).canView(eq("sales-app"), isNull());
  }

  @SuppressWarnings("SameParameterValue")
  private void setSpringAuthentication(String principal) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, "N/A", Collections.emptyList()));
  }

  private void setSeamIdentity(String identity) {
    CurrentSecurityContextHolder.setDelegate(
        () -> Optional.of(new DefaultSecurityIdentity(() -> identity)));
  }


  @RestController
  private static class TestController {

    @GetMapping("/api/dev/{appName}/setting/init-form")
    public ResponseEntity<String> initForm() {
      return ResponseEntity.ok("ok");
    }

    @PutMapping("/api/dev/{appName}/setting")
    public ResponseEntity<String> save() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/dev/{appName}/version")
    public ResponseEntity<String> version() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/dev/{appName}/project-templates")
    public ResponseEntity<String> projectTemplates() {
      return ResponseEntity.ok("ok");
    }

    @PostMapping("/api/dev/{appName}/project-generations")
    public ResponseEntity<String> projectGenerations() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/dev/{appName}/setting/logo")
    public ResponseEntity<String> logo() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/export")
    public ResponseEntity<String> export() {
      return ResponseEntity.ok("ok");
    }

    @DeleteMapping("/api/app/{appName}")
    public ResponseEntity<String> deleteApp() {
      return ResponseEntity.ok("ok");
    }

    @PostMapping("/api/app/{appName}/rename")
    public ResponseEntity<String> rename() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/edit-access")
    public ResponseEntity<String> getEditAccess() {
      return ResponseEntity.ok("ok");
    }

    @PutMapping("/api/app/{appName}/edit-access")
    public ResponseEntity<String> putEditAccess() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/edit-access/users")
    public ResponseEntity<String> editAccessUsers() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/edit-access/roles")
    public ResponseEntity<String> editAccessRoles() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/edit-access/organizations")
    public ResponseEntity<String> editAccessOrganizations() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/{appName}/edit-access-entry")
    public ResponseEntity<String> entry() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/app/container/base-images")
    public ResponseEntity<String> baseImages() {
      return ResponseEntity.ok("ok");
    }
  }
}
