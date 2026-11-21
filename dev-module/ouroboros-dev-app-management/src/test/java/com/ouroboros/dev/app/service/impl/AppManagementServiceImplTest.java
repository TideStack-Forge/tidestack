package com.ouroboros.dev.app.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Optional;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditAccessSubject;
import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.model.AppOwnerEntity;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.dev.app.service.AppOwnerService;
import com.ouroboros.model.AppInfo;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppManagementServiceImplTest {

  @TempDir
  Path workspace;

  @Mock
  private DevelopWorkspaceContext developWorkspaceContext;

  @Mock
  private VCSManagerCenter vcsManagerCenter;

  @Mock
  private AppCategoryService appCategoryService;

  @Mock
  private org.springframework.context.ApplicationEventPublisher eventPublisher;

  @Mock
  private AppEditScopeService appEditScopeService;

  @Mock
  private AppEditAccessEvaluator appEditAccessEvaluator;

  @Mock
  private AppOwnerService appOwnerService;

  private AppManagementServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AppManagementServiceImpl();
    lenient().when(developWorkspaceContext.getDevRootDir()).thenReturn(workspace.toString());
    ReflectionTestUtils.setField(service, "developWorkspaceContext", developWorkspaceContext);
    ReflectionTestUtils.setField(service, "vcsManagerCenter", vcsManagerCenter);
    ReflectionTestUtils.setField(service, "appCategoryService", appCategoryService);
    ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
    ReflectionTestUtils.setField(service, "appEditScopeService", appEditScopeService);
    ReflectionTestUtils.setField(service, "appEditAccessEvaluator", appEditAccessEvaluator);
    ReflectionTestUtils.setField(service, "appOwnerService", appOwnerService);
    setSeamIdentity("alice");
    setSpringAuthentication("alice");
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void listAppsHidesUnauthorizedAppsForCurrentUser() throws Exception {
    createAppDirectory("sales-app");
    createAppDirectory("hr-app");
    when(appEditAccessEvaluator.canView("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canView("hr-app", "alice")).thenReturn(false);

    List<String> appNames = service.listApps().get().stream()
        .map(app -> app.getAppInfo().getName())
        .collect(Collectors.toList());

    assertEquals(Collections.singletonList("sales-app"), appNames);
  }

  @Test
  void listAppsIncludesEditAccessManagementFlag() throws Exception {
    createAppDirectory("sales-app");
    createAppDirectory("hr-app");
    when(appEditAccessEvaluator.canView("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canView("hr-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canManageEditAccess("hr-app", "alice")).thenReturn(false);

    Map<String, Boolean> manageFlags = service.listApps().get().stream()
        .collect(Collectors.toMap(app -> app.getAppInfo().getName(), app -> app.getCanManageEditAccess()));

    assertEquals(Boolean.TRUE, manageFlags.get("sales-app"));
    assertEquals(Boolean.FALSE, manageFlags.get("hr-app"));
  }

  @Test
  void listAppsUsesSeamIdentityForAccessChecks() throws Exception {
    createAppDirectory("sales-app");
    when(appEditAccessEvaluator.canView("sales-app", "alice")).thenReturn(true);
    when(appEditAccessEvaluator.canManageEditAccess("sales-app", "alice")).thenReturn(true);

    service.listApps();

    verify(appEditAccessEvaluator).canView("sales-app", "alice");
    verify(appEditAccessEvaluator).canManageEditAccess("sales-app", "alice");
  }

  @Test
  void listAppsDoesNotUseLegacyPrincipalWhenSeamMissing() throws Exception {
    setSeamIdentity(null);
    setSpringAuthentication("legacy-principal");
    createAppDirectory("sales-app");
    when(appEditAccessEvaluator.canView(eq("sales-app"), isNull())).thenReturn(true);
    when(appEditAccessEvaluator.canManageEditAccess(eq("sales-app"), isNull())).thenReturn(false);

    service.listApps();

    verify(appEditAccessEvaluator).canView(eq("sales-app"), isNull());
    verify(appEditAccessEvaluator).canManageEditAccess(eq("sales-app"), isNull());
  }

  @Test
  void renameAppRestoresWorkspaceAndScopesWhenScopeRenameFails() throws Exception {
    Path oldPath = createAppDirectory("sales-app");
    Path newPath = workspace.resolve("sales-app-v2");

    VCSManager oldVcsManager = mock(VCSManager.class);
    VCSManager newVcsManager = mock(VCSManager.class);
    VCSFile oldVcsFile = mock(VCSFile.class);
    VCSFile newVcsFile = mock(VCSFile.class);
    VCSAction.UpdateFileAction updateAction = mock(VCSAction.UpdateFileAction.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(oldVcsManager);
    when(vcsManagerCenter.getVcsManager("sales-app-v2")).thenReturn(newVcsManager);
    when(oldVcsManager.getFile("app.metadata.json")).thenReturn(oldVcsFile);
    when(newVcsManager.getFile("app.metadata.json")).thenReturn(newVcsFile);
    when(oldVcsFile.getContent()).thenReturn(Json.toPrettyJsonString(new AppInfo().setName("sales-app")));
    when(newVcsFile.getContent()).thenReturn(
        Json.toPrettyJsonString(new AppInfo().setName("sales-app")),
        Json.toPrettyJsonString(new AppInfo().setName("sales-app-v2"))
    );
    when(newVcsManager.updateFile("app.metadata.json")).thenReturn(updateAction);
    when(updateAction.content(anyString())).thenReturn(updateAction);
    when(updateAction.unlockWhenCommit()).thenReturn(updateAction);
    when(updateAction.commit(anyString())).thenReturn(Try.success(true));
    doThrow(new RuntimeException("scope rename failed"))
        .when(appEditScopeService).renameScopes("sales-app", "sales-app-v2");

    Try<Void> result = service.renameApp("sales-app", "sales-app-v2");

    assertTrue(result.isFailure());
    assertTrue(Files.exists(oldPath));
    assertFalse(Files.exists(newPath));
    verify(appEditScopeService).renameScopes("sales-app", "sales-app-v2");

    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    verify(updateAction, times(2)).content(contentCaptor.capture());
    assertTrue(contentCaptor.getAllValues().stream().anyMatch(content -> content.contains("\"name\" : \"sales-app-v2\"")));
    assertTrue(contentCaptor.getAllValues().stream().anyMatch(content -> content.contains("\"name\" : \"sales-app\"")));
  }

  @Test
  void deleteAppRemovesScopesAndRestoresThemIfWorkspaceDeleteFails() {
    when(appEditAccessEvaluator.canManageApp("sales-app", "alice")).thenReturn(true);
    when(appEditScopeService.getByAppName("sales-app")).thenReturn(Arrays.asList(
        scope(AppEditScopeSubjectType.USER, "alice", "Alice"),
        scope(AppEditScopeSubjectType.ROLE, "dev-role", "Developer")
    ));
    when(appOwnerService.getByAppName("sales-app")).thenReturn(Optional.of(owner("sales-app", "alice")));

    Try<Void> result = service.deleteApp("sales-app");

    assertTrue(result.isFailure());
    verify(appEditScopeService).deleteScopes("sales-app");
    verify(appOwnerService).deleteOwner("sales-app");

    ArgumentCaptor<AppEditAccessConfigRequest> requestCaptor = ArgumentCaptor.forClass(AppEditAccessConfigRequest.class);
    verify(appEditScopeService).replaceScopes(eq("sales-app"), requestCaptor.capture(), eq("alice"));
    verify(appOwnerService).assignOwner("sales-app", "alice", "alice");

    AppEditAccessConfigRequest restoredRequest = requestCaptor.getValue();
    assertEquals(Collections.singletonList("alice"),
        restoredRequest.getUsers().stream().map(AppEditAccessSubject::getId).collect(Collectors.toList()));
    assertEquals(Collections.singletonList("dev-role"),
        restoredRequest.getRoles().stream().map(AppEditAccessSubject::getId).collect(Collectors.toList()));
    assertTrue(restoredRequest.getOrganizations().isEmpty());
  }

  @Test
  void deleteAppUsesNullOperatorWhenSeamMissing() {
    setSeamIdentity(null);
    setSpringAuthentication("legacy-principal");
    when(appEditAccessEvaluator.canManageApp("sales-app", null)).thenReturn(true);
    when(appEditScopeService.getByAppName("sales-app")).thenReturn(Arrays.asList(
        scope(AppEditScopeSubjectType.USER, "alice", "Alice"),
        scope(AppEditScopeSubjectType.ROLE, "dev-role", "Developer")
    ));
    when(appOwnerService.getByAppName("sales-app")).thenReturn(Optional.of(owner("sales-app", "alice")));

    Try<Void> result = service.deleteApp("sales-app");

    assertTrue(result.isFailure());

    ArgumentCaptor<String> replaceOperatorCaptor = ArgumentCaptor.forClass(String.class);
    verify(appEditScopeService).replaceScopes(eq("sales-app"), any(), replaceOperatorCaptor.capture());
    assertNull(replaceOperatorCaptor.getValue());

    ArgumentCaptor<String> ownerOperatorCaptor = ArgumentCaptor.forClass(String.class);
    verify(appOwnerService).assignOwner(eq("sales-app"), eq("alice"), ownerOperatorCaptor.capture());
    assertNull(ownerOperatorCaptor.getValue());
  }

  @Test
  void deleteAppDoesNotRestoreScopesAfterAppAlreadyMovedToTrash() throws Exception {
    createAppDirectory("sales-app");
    when(appEditAccessEvaluator.canManageApp("sales-app", "alice")).thenReturn(true);
    when(appEditScopeService.getByAppName("sales-app")).thenReturn(Collections.singletonList(
        scope(AppEditScopeSubjectType.USER, "alice", "Alice")
    ));
    when(appOwnerService.getByAppName("sales-app")).thenReturn(Optional.of(owner("sales-app", "alice")));
    doThrow(new RuntimeException("event listener failed"))
        .when(eventPublisher).publishEvent(any());

    Try<Void> result = service.deleteApp("sales-app");

    assertTrue(result.isFailure());
    assertFalse(Files.exists(workspace.resolve("sales-app")));
    assertTrue(Files.exists(workspace.resolve("_trash")));
    try (var trashEntries = Files.list(workspace.resolve("_trash"))) {
      assertEquals(1L, trashEntries.count());
    }
    verify(appEditScopeService).deleteScopes("sales-app");
    verify(appOwnerService).deleteOwner("sales-app");
    verify(appEditScopeService, never()).replaceScopes(anyString(), any(), anyString());
    verify(appOwnerService, never()).assignOwner(anyString(), anyString(), anyString());
  }

  @Test
  void deleteAppRejectsUserWithoutManagePermission() {
    when(appEditAccessEvaluator.canManageApp("sales-app", "alice")).thenReturn(false);

    Try<Void> result = service.deleteApp("sales-app");

    assertTrue(result.isFailure());
    verify(appEditScopeService, never()).deleteScopes(anyString());
    verify(appOwnerService, never()).deleteOwner(anyString());
  }

  @Test
  void createAppAssignsCurrentUserAsOwner() {
    VCSManager vcsManager = mock(VCSManager.class);
    VCSAction.CreateFileAction createAction = mock(VCSAction.CreateFileAction.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(vcsManager);
    when(vcsManager.createFile("app.metadata.json")).thenReturn(createAction);
    when(createAction.content(anyString())).thenReturn(createAction);
    when(createAction.unlockWhenCommit()).thenReturn(createAction);
    when(createAction.commit(anyString())).thenReturn(Try.success(true));

    Try<Void> result = service.createApp(new AppInfo().setName("sales-app"));

    assertTrue(result.isSuccess());
    verify(appOwnerService).assignOwner("sales-app", "alice", "alice");
  }

  @Test
  void createAppDoesNotPropagateLegacyPrincipalWhenSeamMissing() {
    setSeamIdentity(null);
    setSpringAuthentication("legacy-principal");

    VCSManager vcsManager = mock(VCSManager.class);
    VCSAction.CreateFileAction createAction = mock(VCSAction.CreateFileAction.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(vcsManager);
    when(vcsManager.createFile("app.metadata.json")).thenReturn(createAction);
    when(createAction.content(anyString())).thenReturn(createAction);
    when(createAction.unlockWhenCommit()).thenReturn(createAction);
    when(createAction.commit(anyString())).thenReturn(Try.success(true));

    Try<Void> result = service.createApp(new AppInfo().setName("sales-app"));

    assertTrue(result.isSuccess());
    ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> operatorCaptor = ArgumentCaptor.forClass(String.class);
    verify(appOwnerService).assignOwner(eq("sales-app"), ownerCaptor.capture(), operatorCaptor.capture());
    assertNull(ownerCaptor.getValue());
    assertNull(operatorCaptor.getValue());
  }

  @Test
  void createAppRemovesWorkspaceWhenOwnerAssignmentFails() {
    VCSManager vcsManager = mock(VCSManager.class);
    VCSAction.CreateFileAction createAction = mock(VCSAction.CreateFileAction.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(vcsManager);
    when(vcsManager.createFile("app.metadata.json")).thenReturn(createAction);
    when(createAction.content(anyString())).thenReturn(createAction);
    when(createAction.unlockWhenCommit()).thenReturn(createAction);
    when(createAction.commit(anyString())).thenReturn(Try.success(true));
    doThrow(new RuntimeException("owner save failed"))
        .when(appOwnerService).assignOwner("sales-app", "alice", "alice");

    Try<Void> result = service.createApp(new AppInfo().setName("sales-app"));

    assertTrue(result.isFailure());
    assertFalse(Files.exists(workspace.resolve("sales-app")));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void importAppRemovesWorkspaceWhenOwnerAssignmentFails() throws Exception {
    Path zipFile = createImportZip("sales-app");
    VCSManager vcsManager = mock(VCSManager.class);
    VCSFile vcsFile = mock(VCSFile.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(vcsManager);
    when(vcsManager.getFile("app.metadata.json")).thenReturn(vcsFile);
    doThrow(new RuntimeException("owner save failed"))
        .when(appOwnerService).assignOwner("sales-app", "alice", "alice");

    Try<Void> result;
    try (var inputStream = Files.newInputStream(zipFile)) {
      result = service.importApp(inputStream);
    }

    assertTrue(result.isFailure());
    assertFalse(Files.exists(workspace.resolve("sales-app")));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void renameAppRollsBackScopesWhenOwnerRenameFails() throws Exception {
    Path oldPath = createAppDirectory("sales-app");
    Path newPath = workspace.resolve("sales-app-v2");

    VCSManager oldVcsManager = mock(VCSManager.class);
    VCSManager newVcsManager = mock(VCSManager.class);
    VCSFile oldVcsFile = mock(VCSFile.class);
    VCSFile newVcsFile = mock(VCSFile.class);
    VCSAction.UpdateFileAction updateAction = mock(VCSAction.UpdateFileAction.class);

    when(vcsManagerCenter.getVcsManager("sales-app")).thenReturn(oldVcsManager);
    when(vcsManagerCenter.getVcsManager("sales-app-v2")).thenReturn(newVcsManager);
    when(oldVcsManager.getFile("app.metadata.json")).thenReturn(oldVcsFile);
    when(newVcsManager.getFile("app.metadata.json")).thenReturn(newVcsFile);
    when(oldVcsFile.getContent()).thenReturn(Json.toPrettyJsonString(new AppInfo().setName("sales-app")));
    when(newVcsFile.getContent()).thenReturn(
        Json.toPrettyJsonString(new AppInfo().setName("sales-app")),
        Json.toPrettyJsonString(new AppInfo().setName("sales-app-v2"))
    );
    when(newVcsManager.updateFile("app.metadata.json")).thenReturn(updateAction);
    when(updateAction.content(anyString())).thenReturn(updateAction);
    when(updateAction.unlockWhenCommit()).thenReturn(updateAction);
    when(updateAction.commit(anyString())).thenReturn(Try.success(true));
    doThrow(new RuntimeException("owner rename failed"))
        .when(appOwnerService).renameOwner("sales-app", "sales-app-v2");

    Try<Void> result = service.renameApp("sales-app", "sales-app-v2");

    assertTrue(result.isFailure());
    assertTrue(Files.exists(oldPath));
    assertFalse(Files.exists(newPath));
    verify(appEditScopeService).renameScopes("sales-app", "sales-app-v2");
    verify(appEditScopeService).renameScopes("sales-app-v2", "sales-app");
  }

  private Path createAppDirectory(String appName) throws IOException {
    Path appPath = workspace.resolve(appName);
    Files.createDirectories(appPath);
    Files.write(appPath.resolve("app.metadata.json"),
        Json.toPrettyJsonString(new AppInfo().setName(appName)).getBytes(StandardCharsets.UTF_8));
    return appPath;
  }

  private Path createImportZip(String appName) throws IOException {
    Path zipPath = workspace.resolve(appName + ".zip");
    try (OutputStream outputStream = Files.newOutputStream(zipPath);
         ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      zipOutputStream.putNextEntry(new ZipEntry("app.metadata.json"));
      zipOutputStream.write(Json.toPrettyJsonString(new AppInfo().setName(appName)).getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();
    }
    return zipPath;
  }

  private static AppEditScopeEntity scope(AppEditScopeSubjectType subjectType, String subjectId, String subjectName) {
    AppEditScopeEntity entity = new AppEditScopeEntity();
    entity.setAppName("sales-app");
    entity.setSubjectType(subjectType.name());
    entity.setSubjectId(subjectId);
    entity.setSubjectName(subjectName);
    return entity;
  }

  private static AppOwnerEntity owner(String appName, String ownerUserIdentity) {
    AppOwnerEntity owner = new AppOwnerEntity();
    owner.setAppName(appName);
    owner.setOwnerUserIdentity(ownerUserIdentity);
    return owner;
  }

  private void setSeamIdentity(String identity) {
    CurrentSecurityContextHolder.clearDelegate();
    if (identity != null) {
      CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> identity)));
    }
  }

  private void setSpringAuthentication(String identity) {
    SecurityContextHolder.clearContext();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(identity, "N/A", Collections.emptyList()));
  }
}
