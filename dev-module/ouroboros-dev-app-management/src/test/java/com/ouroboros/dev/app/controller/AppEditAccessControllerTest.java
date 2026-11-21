package com.ouroboros.dev.app.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppEditAccessControllerTest {

  private final AppEditScopeService appEditScopeService = mock(AppEditScopeService.class);
  private final AppEditAccessEvaluator appEditAccessEvaluator = mock(AppEditAccessEvaluator.class);
  private final AppManagementService appManagementService = mock(AppManagementService.class);
  private final UserCenter userCenter = mock(UserCenter.class);
  private final RoleCenter roleCenter = mock(RoleCenter.class);
  private final OrganizationQueryFacade organizationQueryFacade = mock(OrganizationQueryFacade.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AppEditAccessController controller = new AppEditAccessController(
        appEditScopeService,
        appEditAccessEvaluator,
        appManagementService,
        userCenter,
        roleCenter,
        organizationQueryFacade
    );
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    when(appManagementService.appExists(anyString())).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("alice", "N/A", Collections.emptyList()));
    CurrentSecurityContextHolder.clearDelegate();
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(securityIdentity("alice")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void getReturnsGroupedScopesForEditAccessManager() throws Exception {
    OrganizationNode node = organizationNode("dept-rd-1", "研发一部", "department");
    User bob = user("bob", "Bob");

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canView("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canEdit("sales-app", "alice")).thenReturn(true);
    when(appEditScopeService.getByAppName("sales-app")).thenReturn(Arrays.asList(
        scope(AppEditScopeSubjectType.USER, "bob", "Bob"),
        scope(AppEditScopeSubjectType.ROLE, "dev-role", "Developer"),
        scope(AppEditScopeSubjectType.ORG, "dept-rd-1", "研发一部")
    ));
    when(userCenter.getUser("bob")).thenReturn(Optional.of(bob));
    when(organizationQueryFacade.getNodeById("dept-rd-1")).thenReturn(Optional.of(node));

    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.restricted").value(true))
        .andExpect(jsonPath("$.data.currentUserCanView").value(true))
        .andExpect(jsonPath("$.data.currentUserCanEdit").value(true))
        .andExpect(jsonPath("$.data.users[0].id").value("bob"))
        .andExpect(jsonPath("$.data.users[0].userIdentity").value("bob"))
        .andExpect(jsonPath("$.data.users[0].displayName").value("Bob"))
        .andExpect(jsonPath("$.data.roles[0].id").value("dev-role"))
        .andExpect(jsonPath("$.data.organizations[0].id").value("dept-rd-1"))
        .andExpect(jsonPath("$.data.organizations[0].type").value("department"));
  }

  @Test
  void getRejectsUserWithoutEditAccessManageAuthorityBeforeLeakingScopes() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(appEditScopeService);
  }

  @Test
  void getRejectsNonexistentAppBeforeReadingScopes() throws Exception {
    when(appManagementService.appExists("missing-app")).thenReturn(false);
    when(appEditAccessEvaluator.canManageEditAccess("missing-app", "alice")).thenReturn(true);

    mockMvc.perform(get("/api/app/missing-app/edit-access"))
        .andExpect(status().isNotFound());

    verify(appEditScopeService, never()).getByAppName(anyString());
  }

  @Test
  void putRejectsRequestsMissingExplicitLists() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);

    mockMvc.perform(put("/api/app/sales-app/edit-access")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.msg").value("users、roles、organizations 字段必须显式提供"));

    verifyNoInteractions(userCenter, roleCenter, organizationQueryFacade, appEditScopeService);
  }

  @Test
  void putValidatesSubjectsAndReplacesScopesWithCanonicalValues() throws Exception {
    User bob = user("bob", "Bob");
    Role devRole = role("dev-role", "Developer");
    OrganizationNode node = organizationNode("dept-rd-1", "研发一部", "department");

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);
    when(userCenter.getUser("bob")).thenReturn(Optional.of(bob));
    when(roleCenter.getRole("dev-role")).thenReturn(Optional.of(devRole));
    when(organizationQueryFacade.getNodeById("dept-rd-1"))
        .thenReturn(Optional.of(node));

    mockMvc.perform(put("/api/app/sales-app/edit-access")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"users\":[{\"userIdentity\":\"bob\",\"displayName\":\"Wrong\"}],\"roles\":[{\"id\":\"dev-role\",\"name\":\"Wrong\"}],\"organizations\":[{\"id\":\"dept-rd-1\",\"name\":\"Wrong\",\"type\":\"company\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.ouroboros.dev.app.model.AppEditAccessConfigRequest.class);
    verify(appEditScopeService).replaceScopes(eq("sales-app"), requestCaptor.capture(), eq("alice"));

    var request = requestCaptor.getValue();
    assertEquals("Bob", request.getUsers().get(0).getName());
    assertEquals("Developer", request.getRoles().get(0).getName());
    assertEquals("研发一部", request.getOrganizations().get(0).getName());
    assertEquals("department", request.getOrganizations().get(0).getType());
  }

  @Test
  void putRejectsUnknownSubjectIds() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);
    when(userCenter.getUser("ghost")).thenReturn(Optional.empty());

    mockMvc.perform(put("/api/app/sales-app/edit-access")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"users\":[{\"id\":\"ghost\"}],\"roles\":[],\"organizations\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.msg").value("用户不存在: ghost"));

    verify(appEditScopeService, never()).replaceScopes(anyString(), any(), anyString());
  }

  @Test
  void putRejectsNonexistentAppBeforeWritingScopes() throws Exception {
    when(appManagementService.appExists("missing-app")).thenReturn(false);
    when(appEditAccessEvaluator.canManageEditAccess("missing-app", "alice")).thenReturn(true);

    mockMvc.perform(put("/api/app/missing-app/edit-access")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"users\":[],\"roles\":[],\"organizations\":[]}"))
        .andExpect(status().isNotFound());

    verifyNoInteractions(userCenter, roleCenter, organizationQueryFacade);
    verify(appEditScopeService, never()).replaceScopes(anyString(), any(), anyString());
  }

  @Test
  void candidateApisExposeRolesForEditAccessManager() throws Exception {
    Role devRole = role("dev-role", "Developer");

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);
    when(roleCenter.getAllRoles()).thenReturn(Collections.singletonList(devRole));

    mockMvc.perform(get("/api/app/sales-app/edit-access/roles").param("perPage", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value("dev-role"))
        .andExpect(jsonPath("$.data.items[0].name").value("Developer"));
  }

  @Test
  void getEntryReturnsVisibilityFlagsWithoutScopeDetails() throws Exception {
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(false);
    when(appEditAccessEvaluator.canEdit("sales-app", "alice")).thenReturn(true);

    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.canManageEditAccess").value(false))
        .andExpect(jsonPath("$.data.currentUserCanEdit").value(true))
        .andExpect(jsonPath("$.data.users").doesNotExist());

    verify(appEditAccessEvaluator).canManageEditAccess("sales-app", "alice");
    verify(appEditAccessEvaluator).canEdit("sales-app", "alice");
  }

  @Test
  void getEntryViewsLegacyPrincipalWithoutSeamIdentity() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("legacy", "N/A", Collections.emptyList()));

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", null)).thenReturn(false);
    when(appEditAccessEvaluator.canEdit("sales-app", null)).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/edit-access-entry"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canManageEditAccess").value(false))
        .andExpect(jsonPath("$.data.currentUserCanEdit").value(false));

    verify(appEditAccessEvaluator).canManageEditAccess("sales-app", null);
    verify(appEditAccessEvaluator).canEdit("sales-app", null);
  }

  @Test
  void putCallsReplaceScopesWithNullIdentityWhenLegacyPrincipalSeamEmpty() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("legacy", "N/A", Collections.emptyList()));

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", null)).thenReturn(true);

    mockMvc.perform(put("/api/app/sales-app/edit-access")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"users\":[],\"roles\":[],\"organizations\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.ouroboros.dev.app.model.AppEditAccessConfigRequest.class);
    verify(appEditScopeService).replaceScopes(eq("sales-app"), requestCaptor.capture(), eq((String) null));
    verify(appEditAccessEvaluator).canManageEditAccess("sales-app", null);
  }

  @Test
  void getRejectsWhenCurrentSecurityIdentityMissingEvenWithLegacyPrincipal() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("legacy", "N/A", Collections.emptyList()));

    when(appEditAccessEvaluator.canManageEditAccess("sales-app", null)).thenReturn(false);

    mockMvc.perform(get("/api/app/sales-app/edit-access"))
        .andExpect(status().isForbidden());

    verify(appEditAccessEvaluator).canManageEditAccess("sales-app", null);
    verify(appEditScopeService, never()).getByAppName(anyString());
  }

  private static AppEditScopeEntity scope(AppEditScopeSubjectType subjectType, String subjectId, String subjectName) {
    AppEditScopeEntity entity = new AppEditScopeEntity();
    entity.setSubjectType(subjectType.name());
    entity.setSubjectId(subjectId);
    entity.setSubjectName(subjectName);
    return entity;
  }

  private static User user(String id, String displayName) {
    User user = mock(User.class);
    when(user.getUserIdentity()).thenReturn(id);
    when(user.getDisplayName()).thenReturn(displayName);
    return user;
  }

  private static Role role(String id, String name) {
    Role role = mock(Role.class);
    when(role.getId()).thenReturn(id);
    when(role.getName()).thenReturn(name);
    return role;
  }
  private static OrganizationNode organizationNode(String id, String name, String type) {
    OrganizationNode node = mock(OrganizationNode.class);
    when(node.getId()).thenReturn(id);
    when(node.getName()).thenReturn(name);
    when(node.getType()).thenReturn(type);
    return node;
  }

  private static SecurityIdentity securityIdentity(String userIdentity) {
    return new DefaultSecurityIdentity(securityUser(userIdentity));
  }

  private static User securityUser(String userIdentity) {
    return () -> userIdentity;
  }
}
