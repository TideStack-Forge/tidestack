package com.ouroboros.app.datamodel.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.DataModelPlugin;
import com.ouroboros.data.model.DataModelPluginContext;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import io.vavr.control.Try;

class DataAuthorizationEnforcementTest {
  private MockedStatic<AuthorityCenter> definitions;
  private final DataModelPlugin tail = mock(DataModelPlugin.class);
  private final DataModelPluginContext context = mock(DataModelPluginContext.class);
  private AccessControlDataModelPlugin plugin;

  @BeforeEach
  void setUp() {
    definitions = mockStatic(AuthorityCenter.class);
    definitions.when(() -> AuthorityCenter.getAuthority(any())).thenAnswer(invocation -> Optional.of(authority(invocation.getArgument(0))));
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.search");
    config.setCreateAuthority("orders.add");
    config.setUpdateAuthority("orders.edit");
    config.setDeleteAuthority("orders.delete");
    plugin = new AccessControlDataModelPlugin(config);
    DataModel model = mock(DataModel.class);
    var fields = List.of("id", "name", "department", "cost").stream().map(name -> {
      var field = mock(DataModelField.class);
      when(field.getName()).thenReturn(name);
      return field;
    }).toList();
    when(model.getFields()).thenReturn(fields);
    when(context.getDataModel()).thenReturn(model);
    when(context.getNextPlugin()).thenReturn(tail);
    when(tail.query(any(), any())).thenReturn(Try.success(RecordList.empty()));
    when(tail.count(any(), any())).thenReturn(Try.success(2L));
    when(tail.update(any(), any(), any())).thenReturn(Try.success(1L));
    when(tail.delete(any(), any())).thenReturn(Try.success(1L));
    when(tail.insert(any(), any())).thenReturn(Try.success(Record.of(Map.of("id", "created"))));
    when(tail.batchInsert(any(), any())).thenReturn(Try.success(RecordList.empty()));
  }

  @AfterEach
  void cleanup() {
    definitions.close();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void availableButUnconfiguredControlsDoNotRestrictOriginalDataOperations() {
    var define = mock(com.ouroboros.security.authority.accesscontrol.AccessControlDefine.class);
    when(define.getCanControlFields()).thenReturn(true);
    when(define.getCommonFields()).thenReturn(List.of());
    when(define.getControlledFields()).thenReturn(List.of(
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("name", "名称", "String", null)));
    when(define.getFilterDefine()).thenReturn(List.of());
    authenticate(new com.ouroboros.security.authority.accesscontrol.DefaultAccessControlParams(define, Map.of()));

    var query = QueryStatement.builder().select(List.of(SExpression.field("id"), SExpression.field("cost")))
        .where(eq("department", "d2")).build();
    assertTrue(plugin.query(query, context).isSuccess());
    assertTrue(plugin.insert(Map.of("name", "new", "department", "d2", "cost", 100), context).isSuccess());
    assertTrue(plugin.update(eq("id", "one"), Map.of("cost", 200), context).isSuccess());
    assertTrue(plugin.delete(eq("id", "one"), context).isSuccess());

    var executed = ArgumentCaptor.forClass(QueryStatement.class);
    verify(tail).query(executed.capture(), any());
    assertEquals(query.getSelect(), executed.getValue().getSelect());
    assertEquals(query.getWhere(), executed.getValue().getWhere());
  }

  @Test
  void missingIdentityCannotReachAnyDataHook() {
    assertTrue(plugin.query(QueryStatement.builder().build(), context).isFailure());
    assertTrue(plugin.count(QueryStatement.builder().build(), context).isFailure());
    assertTrue(plugin.insert(Map.of("name", "x"), context).isFailure());
    assertTrue(plugin.batchInsert(List.of(Map.of("name", "x")), context).isFailure());
    assertTrue(plugin.update(SExpression.empty(Boolean.class), Map.of("name", "x"), context).isFailure());
    assertTrue(plugin.delete(SExpression.empty(Boolean.class), context).isFailure());
    org.mockito.Mockito.verifyNoInteractions(tail);
  }

  @Test
  void rowNoneShortCircuitsReadsAndRejectsWrites() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.all(), RowConstraint.none()));
    assertTrue(plugin.query(QueryStatement.builder().build(), context).get().isEmpty());
    assertEquals(0L, plugin.count(QueryStatement.builder().build(), context).get());
    assertTrue(plugin.insert(Map.of("name", "x"), context).isFailure());
    assertTrue(plugin.delete(SExpression.empty(Boolean.class), context).isFailure());
    org.mockito.Mockito.verifyNoInteractions(tail);
  }

  @Test
  void finiteFieldsBecomeAnExplicitSelectAndForbiddenAliasOrFilterIsRejected() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.only(Set.of("id", "name")), RowConstraint.all()));
    assertTrue(plugin.query(QueryStatement.builder().build(), context).isSuccess());
    var query = ArgumentCaptor.forClass(QueryStatement.class);
    verify(tail).query(query.capture(), any());
    assertEquals(List.of(SExpression.field("id"), SExpression.field("name")), query.getValue().getSelect());
    assertTrue(plugin.query(QueryStatement.builder().select(SExpression.alias(SExpression.field("cost"), "name")).build(), context)
        .isFailure());
    assertTrue(plugin.count(QueryStatement.builder().where(eq("cost", 10)).build(), context).isFailure());
    verify(tail, never()).count(any(), any());
  }

  @Test
  void outOfScopeInputIsRejectedInsteadOfSilentlyRemoved() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.only(Set.of("name")), RowConstraint.all()));
    assertTrue(plugin.insert(Map.of("name", "x", "cost", 100), context).isFailure());
    assertTrue(plugin.update(SExpression.empty(Boolean.class), Map.of("cost", 100), context).isFailure());
    verify(tail, never()).insert(any(), any());
    verify(tail, never()).update(any(), any(), any());
  }

  @Test
  void batchIsValidatedCompletelyBeforeForwarding() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.all(), RowConstraint.filter(Map.of("department", List.of("d1")))));
    assertTrue(plugin.batchInsert(List.of(Map.of("department", "d1"), Map.of("department", "d2")), context).isFailure());
    verify(tail, never()).batchInsert(any(), any());
    assertTrue(plugin.batchInsert(List.of(Map.of("department", "d1")), context).isSuccess());
  }

  @Test
  void insertNeedsAProvableFinalRowAndUpdateCannotMoveRecordsOutsideScope() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.all(), RowConstraint.filter(Map.of("department", "d1"))));
    assertTrue(plugin.insert(Map.of("name", "x"), context).isFailure());
    assertTrue(plugin.insert(Map.of("name", "x", "department", "d2"), context).isFailure());
    assertTrue(plugin.insert(Map.of("name", "x", "department", "d1"), context).isSuccess());
    assertTrue(plugin.update(eq("id", "one"), Map.of("department", "d2"), context).isFailure());
    assertTrue(plugin.update(eq("id", "one"), Map.of("name", "renamed"), context).isSuccess());
    var where = ArgumentCaptor.forClass(SExpression.class);
    verify(tail).update(where.capture(), any(), any());
    assertEquals(SExpression.create(Operators.AND, eq("id", "one"), eq("department", "d1")), where.getValue());
  }

  @Test
  void deleteAndCountReceiveTheSameRowRestriction() {
    authenticate(new ResolvedAccessControlParams(FieldConstraint.all(), RowConstraint.filter(Map.of("department", "d1"))));
    assertTrue(plugin.delete(eq("id", "one"), context).isSuccess());
    assertTrue(plugin.count(QueryStatement.builder().where(eq("id", "one")).build(), context).isSuccess());
    var where = ArgumentCaptor.forClass(SExpression.class);
    verify(tail).delete(where.capture(), any());
    var query = ArgumentCaptor.forClass(QueryStatement.class);
    verify(tail).count(query.capture(), any());
    assertEquals(where.getValue(), query.getValue().getWhere());
  }

  private void authenticate(AccessControlParams params) {
    Role role = mock(Role.class);
    when(role.getAuthorities()).thenAnswer(invocation -> List.of("orders.search", "orders.add", "orders.edit", "orders.delete").stream()
        .map(name -> new EffectiveAuthority(authority(name), params)).toList());
    User user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("user");
    when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }

  private BaseAuthority authority(String name) {
    var authority = new BaseAuthority();
    authority.setFullName(name);
    return authority;
  }

  private SExpression<Boolean> eq(String field, Object value) {
    return SExpression.create(Operators.EQ, SExpression.field(field), SExpression.constant(value));
  }
}
