package com.ouroboros.dev.app.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditAccessSubject;
import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppEditScopeServiceImplTest {

  @Mock
  private TypedDataModel<String, AppEditScopeEntity> dataModel;

  private AppEditScopeServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AppEditScopeServiceImpl() {
      @Override
      protected TypedDataModel<String, AppEditScopeEntity> getDataModel() {
        return dataModel;
      }
    };
  }

  @Test
  void getByAppNameReturnsRowsAndIsRestrictedWhenRowsExist() {
    AppEditScopeEntity scope = new AppEditScopeEntity();
    scope.setAppName("sales-app");
    scope.setSubjectType(AppEditScopeSubjectType.USER.name());
    scope.setSubjectId("alice");
    scope.setSubjectName("Alice");

    List<AppEditScopeEntity> scopes = Collections.singletonList(scope);
    when(dataModel.query(queryStatement("sales-app")))
        .thenReturn(Try.success(scopes), Try.success(scopes));

    assertEquals(scopes, service.getByAppName("sales-app"));
    assertTrue(service.isRestricted("sales-app"));
  }

  @Test
  void replaceScopesOverwritesExistingRowsWithTypedSubjects() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(2L));
    when(dataModel.batchInsert(anyCollection())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Collection<AppEditScopeEntity> entities = invocation.getArgument(0);
      return Try.success(new ArrayList<>(entities));
    });

    AppEditAccessConfigRequest request = new AppEditAccessConfigRequest();
    request.setUsers(Collections.singletonList(subject("alice", "Alice", null)));
    request.setRoles(Collections.singletonList(subject("role-dev", "开发者", null)));
    request.setOrganizations(Collections.singletonList(subject("dept-rd", "研发部", "department")));

    service.replaceScopes("sales-app", request, "admin");

    ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
    InOrder ordered = inOrder(dataModel);
    ordered.verify(dataModel).delete(where("sales-app"));
    ordered.verify(dataModel).batchInsert(captor.capture());

    @SuppressWarnings("unchecked")
    Collection<AppEditScopeEntity> insertedScopes = captor.getValue();
    Map<String, AppEditScopeEntity> scopesByKey = insertedScopes.stream()
        .collect(Collectors.toMap(
            scope -> scope.getSubjectType() + ":" + scope.getSubjectId(),
            scope -> scope
        ));

    assertEquals(3, scopesByKey.size());
    assertScope(scopesByKey.get(scopeKey(AppEditScopeSubjectType.USER, "alice")), "sales-app", "Alice", "admin");
    assertScope(scopesByKey.get(scopeKey(AppEditScopeSubjectType.ROLE, "role-dev")), "sales-app", "开发者", "admin");
    assertScope(scopesByKey.get(scopeKey(AppEditScopeSubjectType.ORG, "dept-rd")), "sales-app", "研发部", "admin");
  }

  @Test
  void replaceScopesTreatsMissingListsAsEmptyAndOnlyClearsExistingRows() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(1L));

    service.replaceScopes("sales-app", new AppEditAccessConfigRequest(), "admin");

    verify(dataModel).delete(where("sales-app"));
    verify(dataModel, never()).batchInsert(anyCollection());
  }

  @Test
  void replaceScopesTreatsExplicitEmptyListsAsClearingAllScopes() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(2L));

    AppEditAccessConfigRequest request = new AppEditAccessConfigRequest();
    request.setUsers(Collections.emptyList());
    request.setRoles(Collections.emptyList());
    request.setOrganizations(Collections.emptyList());

    service.replaceScopes("sales-app", request, "admin");

    verify(dataModel).delete(where("sales-app"));
    verify(dataModel, never()).batchInsert(anyCollection());
  }

  @Test
  void replaceScopesRejectsDuplicateSubjectsInSameRequest() {
    AppEditAccessConfigRequest request = new AppEditAccessConfigRequest();
    request.setUsers(Arrays.asList(
        subject("alice", "Alice", null),
        subject("alice", "Alice", null)
    ));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.replaceScopes("sales-app", request, "admin"));

    assertTrue(exception.getMessage().contains("重复"));
    verifyNoInteractions(dataModel);
  }

  @Test
  void renameScopesMovesAllRowsToNewAppName() {
    when(dataModel.update(where("sales-app"), appNameUpdate("sales-app-v2")))
        .thenReturn(Try.success(2L));

    service.renameScopes("sales-app", "sales-app-v2");

    verify(dataModel).update(where("sales-app"), appNameUpdate("sales-app-v2"));
  }

  @Test
  void deleteScopesRemovesAllRowsForApp() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(2L));

    service.deleteScopes("sales-app");

    verify(dataModel).delete(where("sales-app"));
  }

  private static void assertScope(AppEditScopeEntity scope, String appName, String subjectName, String createdBy) {
    assertNotNull(scope);
    assertEquals(appName, scope.getAppName());
    assertEquals(subjectName, scope.getSubjectName());
    assertEquals(createdBy, scope.getCreatedBy());
    assertNotNull(scope.getCreatedAt());
  }

  private static String scopeKey(AppEditScopeSubjectType subjectType, String subjectId) {
    return subjectType.name() + ":" + subjectId;
  }

  private static AppEditAccessSubject subject(String id, String name, String type) {
    AppEditAccessSubject subject = new AppEditAccessSubject();
    subject.setId(id);
    subject.setName(name);
    subject.setType(type);
    return subject;
  }

  private static Map<String, Object> queryStatement(String appName) {
    return Collections.<String, Object>singletonMap(Keyword.WHERE.toString(), where(appName));
  }

  private static Map<String, Object> where(String appName) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    return where;
  }

  private static Map<String, Object> appNameUpdate(String newAppName) {
    Map<String, Object> update = new HashMap<>();
    update.put("appName", newAppName);
    return update;
  }
}
