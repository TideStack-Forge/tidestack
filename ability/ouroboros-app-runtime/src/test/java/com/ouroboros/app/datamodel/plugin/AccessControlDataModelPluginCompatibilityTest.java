package com.ouroboros.app.datamodel.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.ModelQueryStatement;
import com.ouroboros.data.dsl.statement.ModelQueryStatementBuilder;
import com.ouroboros.data.dsl.statement.OmitClause;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModelPlugin;
import com.ouroboros.data.model.DataModelPluginContext;
import com.ouroboros.data.normalize.QueryNormalizeContext;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessControlDataModelPluginCompatibilityTest {
  private org.mockito.MockedStatic<com.ouroboros.security.authority.AuthorityCenter> definitions;

  @org.junit.jupiter.api.BeforeEach
  void defineAuthority() {
    definitions = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class);
    definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(invocation -> Optional.of(buildAuthority(invocation.getArgument(0))));
  }

  @Test
  void deletedRowsRemainHiddenFromOrdinaryReadsAndCountsWithoutTheDedicatedGrant() {
    var config = new AccessControlDataModelPlugin.Config(); config.setReadAuthority("orders.search");
    config.setDeletedReadAuthority("orders.searchDeleted"); config.setDeletedField("deletedFlag");
    var user = buildUser("reader", List.of(buildRole("reader", List.of(buildGrantedAuthority("orders.search",
        buildAuthority("orders.search"), buildAccessControlParams(Map.of("tenantId", "T1"), List.of("cost")))))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var plugin = new AccessControlDataModelPlugin(config); var tail = new RecordingTailPlugin();
    var input = QueryStatement.builder().where(equalCondition("deletedFlag", true)).build();
    assertTrue(plugin.query(input, contextWithNext(tail)).isSuccess());
    var expected = SExpression.create(Operators.AND, SExpression.create(Operators.AND,
        equalCondition("deletedFlag", true), equalCondition("deletedFlag", false)), equalCondition("tenantId", "T1"));
    assertEquals(expected, tail.forwardedStatement.getWhere());
    assertTrue(plugin.count(input, contextWithNext(tail)).isSuccess());
    assertEquals(expected, tail.forwardedStatement.getWhere());
  }

  @Test
  void deletedReadGrantKeepsTheOriginalRowAndFieldScope() {
    var config = new AccessControlDataModelPlugin.Config(); config.setReadAuthority("orders.search");
    config.setDeletedReadAuthority("orders.searchDeleted"); config.setDeletedField("deletedFlag");
    var user = buildUser("reader", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.search", buildAuthority("orders.search"), buildAccessControlParams(Map.of("tenantId", "T1"), List.of("cost"))),
        buildGrantedAuthority("orders.searchDeleted", buildAuthority("orders.searchDeleted"), buildAccessControlParams(Map.of(), List.of()))))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var tail = new RecordingTailPlugin(); var original = equalCondition("deletedFlag", true);
    assertTrue(new AccessControlDataModelPlugin(config).query(QueryStatement.builder().where(original).build(), contextWithNext(tail)).isSuccess());
    assertEquals(SExpression.create(Operators.AND, original, equalCondition("tenantId", "T1")), tail.forwardedStatement.getWhere());
    assertTrue(tail.forwardedStatement.getSelect().stream().noneMatch(field -> field.getParams().contains("cost")));
  }

  @Test
  void historicalModelDirectReadsRequireBothGrantsAndAnUnfilteredSourceReadScope() {
    var config = new AccessControlDataModelPlugin.Config(); config.setReadAuthority("orders.search");
    config.setRequiredReadAuthority("orders.searchArchive");
    var plugin = new AccessControlDataModelPlugin(config); var tail = new RecordingTailPlugin();
    for (String scenario : List.of("no-history", "no-source", "filtered")) {
      var grants = new java.util.ArrayList<OuroborosGrantedAuthority>();
      if (!scenario.equals("no-history")) { grants.add(buildGrantedAuthority("orders.searchArchive", buildAuthority("orders.searchArchive"), buildAccessControlParams(Map.of(), List.of()))); }
      if (!scenario.equals("no-source")) { grants.add(buildGrantedAuthority("orders.search", buildAuthority("orders.search"),
          buildAccessControlParams(scenario.equals("filtered") ? Map.of("tenantId", "T1") : Map.of(), List.of()))); }
      var user = buildUser("reader", List.of(buildRole("reader", grants)));
      CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
      assertTrue(plugin.query(QueryStatement.builder().build(), contextWithNext(tail)).isFailure(), scenario);
      assertTrue(plugin.count(QueryStatement.builder().build(), contextWithNext(tail)).isFailure(), scenario);
      org.junit.jupiter.api.Assertions.assertNull(tail.forwardedStatement);
    }
  }

  @Test
  void archiveReadPinsItsSourceAndTranslatesRestrictedSourceFields() {
    var config = new AccessControlDataModelPlugin.Config(); config.setReadAuthority("orders.search");
    config.setRequiredReadAuthority("orders.searchArchive");
    config.setSourceFieldMappings(Map.of("sourceModel", "sourceSourceModel", "id", "sourceId"));
    config.setFixedReadConditions(Map.of("sourceModel", "orders"));
    var user = buildUser("reader", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.search", buildAuthority("orders.search"), buildAccessControlParams(Map.of(), List.of("sourceModel", "id"))),
        buildGrantedAuthority("orders.searchArchive", buildAuthority("orders.searchArchive"), buildAccessControlParams(Map.of(), List.of()))))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var tail = new RecordingTailPlugin(); var caller = equalCondition("sourceModel", "otherModel");
    assertTrue(new AccessControlDataModelPlugin(config).query(QueryStatement.builder().where(caller).build(), contextWithNext(tail)).isSuccess());
    assertEquals(SExpression.create(Operators.AND, caller, equalCondition("sourceModel", "orders")), tail.forwardedStatement.getWhere());
    assertTrue(tail.forwardedStatement.getSelect().stream().noneMatch(field -> field.getParams().contains("sourceSourceModel") || field.getParams().contains("sourceId")));
    assertTrue(new AccessControlDataModelPlugin(config).query(QueryStatement.builder().select(SExpression.field("sourceId")).build(), contextWithNext(tail)).isFailure());
  }

  @Test
  void lockingQueryHonorsTrustedExecutionScopeWhileOrdinaryIdentityRulesStayTheFallback() {
    var params = buildAccessControlParams(Map.of("tenantId", "workflow-tenant"), List.of("secretField"));
    var identity = new DefaultSecurityIdentity(buildUser("worker", List.of()));
    var authentication = new com.ouroboros.security.authentication.OuroborosAuthentication() {
      public String getPrincipal() { return "worker"; }
      public Object getCredentials() { return null; }
      public Object getDetails() { return identity.getUserDetail(); }
      public boolean isAuthenticated() { return true; }
      public void setAuthenticated(boolean authenticated) { }
      public User getUserDetail() { return identity.getUserDetail(); }
      public List<Role> getRoles() { return List.of(); }
      public AccessControlParams getAccessControlParams() { return params; }
      public Optional<AccessControlParams> findScopedAccessControlParams(String authority) {
        return "orders.read".equals(authority) ? Optional.of(params) : Optional.empty();
      }
    };
    var scoped = new com.ouroboros.security.context.CurrentSecurityContext() {
      public Optional<com.ouroboros.security.identity.SecurityIdentity> findIdentity() { return Optional.of(identity); }
      public Optional<com.ouroboros.security.authentication.OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    };
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    var tail = new RecordingTailPlugin();
    var statement = QueryStatement.builder().where(equalCondition("id", 42)).build();
    var result = CurrentSecurityContextHolder.withContext(scoped, () ->
        new AccessControlDataModelPlugin(config).queryForUpdate(statement, contextWithNext(tail)));
    assertTrue(result.isSuccess());
    assertTrue(tail.lockingQuery);
    assertEquals(SExpression.create(Operators.AND, equalCondition("id", 42), equalCondition("tenantId", "workflow-tenant")),
        tail.forwardedStatement.getWhere());
    assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"), SExpression.field("name"), SExpression.field("internalField")), tail.forwardedStatement.getSelect());
  }

  @Test
  void historicalReadsProveTheCurrentSourceScopeAndNeverFilterHistoricalValues() {
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.search"); config.setRequiredReadAuthority("orders.searchHistory");
    config.setSourceModel("orders"); config.setSourceKeyField("id"); config.setSnapshotKeyField("sourceId");
    var user = buildUser("reader", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.search", buildAuthority("orders.search"), buildAccessControlParams(Map.of("tenantId", "T1"), List.of())),
        buildGrantedAuthority("orders.searchHistory", buildAuthority("orders.searchHistory"), buildAccessControlParams(Map.of(), List.of()))))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var source = org.mockito.Mockito.mock(com.ouroboros.data.model.DataModel.class);
    org.mockito.Mockito.when(source.getFullName()).thenReturn("orders");
    org.mockito.Mockito.when(source.getRawName()).thenReturn("orders");
    org.mockito.Mockito.when(source.count(org.mockito.ArgumentMatchers.any(QueryStatement.class))).thenReturn(Try.success(1L));
    try (var models = org.mockito.Mockito.mockStatic(com.ouroboros.data.model.DataModelCenter.class)) {
      models.when(() -> com.ouroboros.data.model.DataModelCenter.getDataModel("orders")).thenReturn(Optional.of(source));
      var input = QueryStatement.builder().where(equalCondition("sourceId", "42")).build();
      var tail = new RecordingTailPlugin(); var plugin = new AccessControlDataModelPlugin(config);
      assertTrue(plugin.queryForUpdate(input, contextWithNext(tail)).isSuccess());
      assertEquals(input.getWhere(), tail.forwardedStatement.getWhere());
      assertTrue(tail.lockingQuery);
      var proof = org.mockito.ArgumentCaptor.forClass(QueryStatement.class);
      org.mockito.Mockito.verify(source).count(proof.capture());
      assertEquals(SExpression.create(Operators.AND, equalCondition("id", "42"), equalCondition("tenantId", "T1")), proof.getValue().getWhere());
      var alternative = QueryStatement.builder().where(SExpression.create(Operators.OR,
          equalCondition("sourceId", "42"), equalCondition("sourceId", "other"))).build();
      assertTrue(plugin.query(alternative, contextWithNext(tail)).isFailure());
      org.mockito.Mockito.when(source.count(org.mockito.ArgumentMatchers.any(QueryStatement.class))).thenReturn(Try.success(0L));
      assertTrue(plugin.query(input, contextWithNext(tail)).isFailure());
    }
  }

  @Test
  void finiteSourceFieldScopeIsTranslatedBeforeSelectingSnapshotColumns() {
    var config = new AccessControlDataModelPlugin.Config(); config.setReadAuthority("orders.search");
    config.setRequiredReadAuthority("orders.searchArchive"); config.setSourceFieldMappings(Map.of("id", "sourceId"));
    var limit = new com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams(
        com.ouroboros.security.authority.accesscontrol.FieldConstraint.only(Set.of("id", "name")),
        com.ouroboros.security.authority.accesscontrol.RowConstraint.all());
    var user = buildUser("reader", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.search", buildAuthority("orders.search"), limit),
        buildGrantedAuthority("orders.searchArchive", buildAuthority("orders.searchArchive"), buildAccessControlParams(Map.of(), List.of()))))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var tail = new RecordingTailPlugin();
    var context = org.mockito.Mockito.mock(DataModelPluginContext.class);
    var model = org.mockito.Mockito.mock(com.ouroboros.data.model.DataModel.class);
    var fields = List.of("id", "sourceId", "name", "cost").stream().map(name -> {
      var field = org.mockito.Mockito.mock(com.ouroboros.data.model.DataModelField.class);
      org.mockito.Mockito.when(field.getName()).thenReturn(name); return field;
    }).toList();
    org.mockito.Mockito.when(model.getFields()).thenReturn(fields);
    org.mockito.Mockito.when(context.getDataModel()).thenReturn(model);
    org.mockito.Mockito.when(context.getNextPlugin()).thenReturn(tail);
    var plugin = new AccessControlDataModelPlugin(config);
    assertTrue(plugin.query(QueryStatement.builder().build(), context).isSuccess());
    assertEquals(List.of(SExpression.field("sourceId"), SExpression.field("name")), tail.forwardedStatement.getSelect());
    assertTrue(plugin.query(QueryStatement.builder().select(SExpression.field("cost")).build(), context).isFailure());
  }

  @AfterEach
  void cleanup() {
    definitions.close();
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void query_appliesNamedPermissionFilters_fromCurrentSecurityIdentity_evenWhenSpringAuthenticationExists() {
    AccessControlDataModelPlugin.Config config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");

    AccessControlParams params = buildAccessControlParams(
        linkedMap("tenantId", "tenant-1"),
        Collections.emptyList()
    );
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)
        ))
    ));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("spring-user", "secret"));

    AccessControlDataModelPlugin plugin = new AccessControlDataModelPlugin(config);
    RecordingTailPlugin nextPlugin = new RecordingTailPlugin();

    SExpression<Boolean> callerWhere = equalCondition("status", "ACTIVE");
    QueryStatement statement = QueryStatement.builder().where(callerWhere).build();

    Try<RecordList> result = plugin.query(statement, contextWithNext(nextPlugin));

    assertTrue(result.isSuccess());
    assertSame(nextPlugin.queryResult.get(), result.get());
    assertEquals(SExpression.create(Operators.AND, callerWhere, equalCondition("tenantId", "tenant-1")),
        nextPlugin.forwardedStatement.getWhere());
    assertEquals(callerWhere, statement.getWhere());
  }

  @Test
  void query_appliesNamedPermissionFilters_fromSpringBackedCurrentSecurityContextIdentity() {
    AccessControlDataModelPlugin.Config config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");

    AccessControlParams params = buildAccessControlParams(
        linkedMap("tenantId", "tenant-1"),
        Collections.emptyList()
    );
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)
        ))
    ));
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, "secret");
    authentication.setAuthenticated(true);
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    SecurityContextHolder.getContext().setAuthentication(authentication);

    AccessControlDataModelPlugin plugin = new AccessControlDataModelPlugin(config);
    RecordingTailPlugin nextPlugin = new RecordingTailPlugin();

    SExpression<Boolean> callerWhere = equalCondition("status", "ACTIVE");
    QueryStatement statement = QueryStatement.builder().where(callerWhere).build();

    Try<RecordList> result = plugin.query(statement, contextWithNext(nextPlugin));

    assertTrue(result.isSuccess());
    assertSame(nextPlugin.queryResult.get(), result.get());
    assertEquals(SExpression.create(Operators.AND, callerWhere, equalCondition("tenantId", "tenant-1")),
        nextPlugin.forwardedStatement.getWhere());
    assertEquals(callerWhere, statement.getWhere());
  }

  @Test
  void query_mergesNamedPermissionDisallowedFieldsIntoOmit_withoutMutatingCallerStatement() {
    AccessControlDataModelPlugin.Config config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");

    AccessControlParams params = buildAccessControlParams(
        Collections.emptyMap(),
        Collections.singletonList("secretField")
    );
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)
        ))
    ));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));

    AccessControlDataModelPlugin plugin = new AccessControlDataModelPlugin(config);
    RecordingTailPlugin nextPlugin = new RecordingTailPlugin();

    List<String> callerOmit = Collections.singletonList("internalField");
    QueryStatement statement = new ModelQueryStatementBuilder()
        .omitClause(OmitClause.fromRaw(callerOmit))
        .build();

    Try<RecordList> result = plugin.query(statement, contextWithNext(nextPlugin));

    assertTrue(result.isSuccess());
    assertSame(nextPlugin.queryResult.get(), result.get());
    ModelQueryStatement forwardedStatement = assertInstanceOf(ModelQueryStatement.class, nextPlugin.forwardedStatement);
    assertEquals(Set.of("internalField"), forwardedStatement.getOmitClause().getFields());
    assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"), SExpression.field("name")),
        forwardedStatement.getSelect());
    assertEquals(Set.of("internalField"),
        ((ModelQueryStatement) statement).getOmitClause().getFields());
  }

  @Test
  void normalizedTypedQuery_usesTheSamePermissionFilterLogicAsRawQuery() {
    AccessControlDataModelPlugin.Config config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    AccessControlParams params = buildAccessControlParams(
        linkedMap("tenantId", "tenant-1"), Collections.emptyList());
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    AccessControlDataModelPlugin plugin = new AccessControlDataModelPlugin(config);
    RecordingTailPlugin nextPlugin = new RecordingTailPlugin();
    SExpression<Boolean> callerWhere = SExpression.create(
        Operators.EQ, SExpression.field("status"), SExpression.constant("ACTIVE"));
    QueryStatement statement = QueryStatement.builder().where(callerWhere).build();

    Try<RecordList> result = plugin.query(statement, contextWithNext(nextPlugin));

    assertTrue(result.isSuccess());
    assertEquals(SExpression.create(Operators.AND, callerWhere, equalCondition("tenantId", "tenant-1")),
        nextPlugin.forwardedStatement.getWhere());
    assertEquals(callerWhere, statement.getWhere());
  }

  @Test
  void normalizedTypedQuery_mergesDisallowedFieldsThroughModelOmitClause() {
    AccessControlDataModelPlugin.Config config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    AccessControlParams params = buildAccessControlParams(
        Collections.emptyMap(), Collections.singletonList("secretField"));
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    AccessControlDataModelPlugin plugin = new AccessControlDataModelPlugin(config);
    RecordingTailPlugin nextPlugin = new RecordingTailPlugin();
    QueryStatement statement = new ModelQueryStatementBuilder()
        .omitClause(OmitClause.fromRaw(Collections.singletonList("internalField")))
        .build();

    Try<RecordList> result = plugin.query(statement, contextWithNext(nextPlugin));

    assertTrue(result.isSuccess());
    ModelQueryStatement forwardedStatement = assertInstanceOf(ModelQueryStatement.class, nextPlugin.forwardedStatement);
    assertEquals(Set.of("internalField"), forwardedStatement.getOmitClause().getFields());
    assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"), SExpression.field("name")),
        forwardedStatement.getSelect());
    assertEquals(Set.of("internalField"), ((ModelQueryStatement) statement).getOmitClause().getFields());
  }

  @Test
  void lockingQuery_appliesRowAndFieldPermissionsWithoutLosingTheLockOrMutatingInput() {
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    var params = buildAccessControlParams(linkedMap("tenantId", "tenant-1"), List.of("secretField"));
    User user = buildUser("consumer", List.of(buildRole("role", List.of(
        buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var tail = new RecordingTailPlugin();
    var originalWhere = equalCondition("id", 42);
    var statement = new ModelQueryStatementBuilder()
        .omitClause(OmitClause.fromRaw(List.of("internalField"))).where(originalWhere).build();

    var result = new AccessControlDataModelPlugin(config).queryForUpdate(statement, contextWithNext(tail));

    assertTrue(result.isSuccess());
    assertTrue(tail.lockingQuery);
    assertSame(tail.queryResult.get(), result.get());
    assertEquals(SExpression.create(Operators.AND, originalWhere, equalCondition("tenantId", "tenant-1")),
        tail.forwardedStatement.getWhere());
    assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"), SExpression.field("name")), tail.forwardedStatement.getSelect());
    assertEquals(originalWhere, statement.getWhere());
    assertEquals(Set.of("internalField"),
        assertInstanceOf(ModelQueryStatement.class, statement).getOmitClause().getFields());
  }

  @Test
  void lockingQuery_preservesTheLockWhenNoReadAuthorityIsConfigured() {
    var statement = QueryStatement.builder().where(equalCondition("id", 42)).build();
    var tail = new RecordingTailPlugin();

    var result = new AccessControlDataModelPlugin(new AccessControlDataModelPlugin.Config())
        .queryForUpdate(statement, contextWithNext(tail));

    assertTrue(result.isSuccess());
    assertTrue(tail.lockingQuery);
    assertSame(statement, tail.forwardedStatement);
  }

  @Test
  void normalizedWildcardReadsSelectOnlyAuthorizedFieldsForOrdinaryAndLockingQueries() {
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    var params = buildAccessControlParams(Map.of("tenantId", "T1"), List.of("secretField"));
    var user = buildUser("reader", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var normalizer = QueryNormalizeContext.builder().withDefaultNormalizers().build();
    var plugin = new AccessControlDataModelPlugin(config);
    for (var query : List.<Map<String, Object>>of(Map.of("from", "orders"),
        Map.of("from", "orders", "select", "*"), Map.of("from", "orders", "select", List.of("*")),
        Map.of("from", "orders", "select", "*, name"), Map.of("from", "orders", "select", List.of("*", "name")))) {
      var statement = normalizer.normalizeQuery(query).get();
      var originalSelect = List.copyOf(statement.getSelect());
      for (boolean locking : List.of(false, true)) {
        var tail = new RecordingTailPlugin();
        var context = contextWithNext(tail);
        var result = locking ? plugin.queryForUpdate(statement, context) : plugin.query(statement, context);
        assertTrue(result.isSuccess(), () -> result.toString());
        assertEquals(locking, tail.lockingQuery);
        assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"),
            SExpression.field("name"), SExpression.field("internalField")), tail.forwardedStatement.getSelect());
        assertEquals(equalCondition("tenantId", "T1"), tail.forwardedStatement.getWhere());
        assertEquals(originalSelect, statement.getSelect());
      }
    }
  }

  @Test
  void lockingQuery_appliesFieldRestrictionsToPlainStatements() {
    var config = new AccessControlDataModelPlugin.Config();
    config.setReadAuthority("orders.read");
    var params = buildAccessControlParams(Map.of(), List.of("secretField"));
    User user = buildUser("consumer", List.of(buildRole("role", List.of(
        buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    var tail = new RecordingTailPlugin();
    var statement = QueryStatement.builder().build();

    var result = new AccessControlDataModelPlugin(config).queryForUpdate(statement, contextWithNext(tail));

    assertTrue(result.isSuccess());
    assertTrue(tail.lockingQuery);
    assertEquals(List.of(SExpression.field("id"), SExpression.field("status"), SExpression.field("tenantId"), SExpression.field("name"), SExpression.field("internalField")), tail.forwardedStatement.getSelect());
    assertTrue(statement.getOmit().isEmpty());
  }

  private static User buildUser(String identity, List<Role> roles) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return identity;
      }

      @Override
      public List<Role> getRoles() {
        return roles;
      }
    };
  }

  private static Role buildRole(String id, List<? extends OuroborosGrantedAuthority> authorities) {
    return new Role() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getName() {
        return id;
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return authorities;
      }
    };
  }

  private static Authority buildAuthority(String fullName) {
    return new Authority() {
      @Override
      public String getFullName() {
        return fullName;
      }

      @Override
      public String getTitle() {
        return fullName;
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public List<String> getApis() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getUiViews() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getMenuItems() {
        return Collections.emptyList();
      }
    };
  }

  private static OuroborosGrantedAuthority buildGrantedAuthority(
      String authorityName,
      Authority authorityObject,
      AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authorityName;
      }

      @Override
      public Authority getAuthorityObject() {
        return authorityObject;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }

  private static AccessControlParams buildAccessControlParams(
      Map<String, Object> filters,
      List<String> disallowedFields) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return List.of("*");
      }

      @Override
      public List<String> getDisallowedFields() {
        return disallowedFields;
      }

      @Override
      public Map<String, Object> getFilters() {
        return filters;
      }
    };
  }

  private static Map<String, Object> linkedMap(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  private static SExpression<Boolean> equalCondition(String field, Object value) {
    return SExpression.create(Operators.EQ, SExpression.field(field), SExpression.constant(value));
  }

  private static DataModelPluginContext contextWithNext(DataModelPlugin nextPlugin) {
    return new SimplePluginContext(nextPlugin, null);
  }

  private static final class RecordingTailPlugin implements DataModelPlugin {
    private boolean lockingQuery;
    private QueryStatement forwardedStatement;
    private final Try<RecordList> queryResult = Try.success(RecordList.of(Collections.emptyList()));

    public Try<Long> count(QueryStatement statement, DataModelPluginContext context) {
      forwardedStatement = statement; return Try.success(0L);
    }

    @Override
    public Try<RecordList> query(QueryStatement statement, DataModelPluginContext context) {
      forwardedStatement = statement;
      return queryResult;
    }

    @Override
    public Try<RecordList> queryForUpdate(QueryStatement statement, DataModelPluginContext context) {
      lockingQuery = true;
      forwardedStatement = statement;
      return queryResult;
    }
  }

  private record SimplePluginContext(
      DataModelPlugin nextPlugin,
      DataModelPluginContext nextPluginContext) implements DataModelPluginContext {
    @Override
    public com.ouroboros.data.model.DataModel getDataModel() {
      var model = org.mockito.Mockito.mock(com.ouroboros.data.model.DataModel.class);
      var fields = List.of("id", "status", "tenantId", "name", "secretField", "internalField").stream().map(name -> {
        var field = org.mockito.Mockito.mock(com.ouroboros.data.model.DataModelField.class);
        org.mockito.Mockito.when(field.getName()).thenReturn(name);
        return field;
      }).toList();
      org.mockito.Mockito.when(model.getFields()).thenReturn(fields);
      return model;
    }

    @Override
    public com.ouroboros.data.model.DataModel getCoreDataModel() {
      return null;
    }

    @Override
    public DataModelPlugin getNextPlugin() {
      return nextPlugin;
    }

    @Override
    public DataModelPluginContext getNextPluginContext() {
      return nextPluginContext;
    }
  }
}
