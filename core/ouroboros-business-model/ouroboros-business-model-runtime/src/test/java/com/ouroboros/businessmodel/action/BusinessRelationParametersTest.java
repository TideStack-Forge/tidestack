package com.ouroboros.businessmodel.action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessRelationParametersTest {
  private static final Map<String, Object> MARKER = Map.of("modelFullName", "sales.Order", "relationName", "customer");

  @Test
  void decimalReferenceKeysUseDatabaseEqualityAcrossScale() {
    withModels(true, () -> withPermission(true, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
      var result = assertInstanceOf(Record.class, BusinessRelationParameters.resolve(MARKER, 1));
      assertEquals("C-1", result.get("id"));
      assertEquals("trusted", result.get("state"));
      assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, 2));
      return null;
    }));
  }

  @Test
  void scalarAndObjectInputsResolveServerStateAndIgnoreForgedFields() {
    withModels(() -> withPermission(true, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
      var scalar = assertInstanceOf(Record.class, BusinessRelationParameters.resolve(MARKER, "C-1"));
      assertEquals("trusted", scalar.get("state"));
      var object = assertInstanceOf(Record.class, BusinessRelationParameters.resolve(MARKER, Map.of("id", "C-1", "state", "forged")));
      assertEquals(new java.util.LinkedHashMap<>(scalar), new java.util.LinkedHashMap<>(object));
      assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER,
          Map.of("id", "C-1", "modelFullName", "sales.Other")));
      assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, List.of("C-1")));
      assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "missing"));
      assertNull(BusinessRelationParameters.resolve(MARKER, null));
      return null;
    }));
  }

  @Test
  void relationTargetsEnforceCurrentReadAuthorityRowScopeAndFieldScope() {
    withModels(() -> {
      withPermission(false, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
        assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "C-1"));
        return null;
      });
      var rowDenied = params(Map.of("state", "other"), List.of());
      withPermission(true, rowDenied, () -> {
        assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "C-1"));
        return null;
      });
      withPermission(true, params(Map.of(), List.of("state")), () -> {
        var resolved = assertInstanceOf(Record.class, BusinessRelationParameters.resolve(MARKER, "C-1"));
        assertEquals("C-1", resolved.get("id"));
        assertFalse(resolved.containsKey("state"));
        return null;
      });
      withPermission(true, params(Map.of(), List.of("id")), () -> {
        assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "C-1"));
        return null;
      });
      return null;
    });
  }

  @Test
  void lockedRelationRevalidationUsesDatabaseEqualityAndRejectsChangesOrRetargeting() {
    withModels(true, (data, manager) -> withPermission(true, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
      var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
      transaction.execute(status -> {
        var states = BusinessActionObjectStates.resolve(Map.of("x-business-relation", MARKER), 1);
        states.lockAndRevalidate(com.ouroboros.businessmodel.transaction.BusinessTransactionResources.capture(manager, status));
        assertEquals("C-1", states.values().get("").get("id"));
        return null;
      });
      for (boolean rebind : List.of(false, true)) {
        assertThrows(BusinessException.class, () -> transaction.execute(status -> {
          var states = BusinessActionObjectStates.resolve(Map.of("x-business-relation", MARKER), 1);
          data.update("C-1", Map.of("code", new java.math.BigDecimal("2"))).get();
          if (rebind) { data.insert(Map.of("id", "C-2", "state", "other", "code", new java.math.BigDecimal("1"))).get(); }
          states.lockAndRevalidate(com.ouroboros.businessmodel.transaction.BusinessTransactionResources.capture(manager, status));
          return null;
        }));
        assertEquals(1, data.count(Map.of()).get());
        assertEquals(0, new java.math.BigDecimal("1").compareTo((java.math.BigDecimal) data.get("C-1").get().get("code")));
      }
      return null;
    }));
  }

  @Test
  void unreadableRuleFieldsAreRejectedBeforeNullCouldSatisfyThePredicate() {
    withModels(() -> withPermission(true, params(Map.of(), List.of("state")), () -> {
      var schema = Map.of("type", "object", "properties", Map.of("params", Map.of("type", "object", "properties", Map.of(
          "customer", Map.of("x-business-relation", MARKER)))));
      var states = BusinessActionObjectStates.resolve(schema, Map.of("params", Map.of("customer", "C-1")));
      assertFalse(states.values().get("/params/customer").containsKey("state"));
      var plan = new BusinessActionPreconditionPlan(Map.of("expression", "${objectStates['/params/customer'].state != 'CLOSED'}"));
      assertThrows(BusinessException.class, () -> states.requireReadableFields(plan));
      return null;
    }));
  }


  @Test
  void alternativeModelProvidersUseTheSameAuthorityAndRecordResolution() {
    withModels(false, true, (data, manager) -> {
      withPermission(true, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
        var result = assertInstanceOf(Record.class, BusinessRelationParameters.resolve(MARKER, "C-1"));
        assertEquals("trusted", result.get("state"));
        assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "missing"));
        return null;
      });
      withPermission(false, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), () -> {
        assertThrows(BusinessException.class, () -> BusinessRelationParameters.resolve(MARKER, "C-1"));
        return null;
      });
      return null;
    });
  }

  private static <T> T withModels(Supplier<T> operation) {
    return withModels(false, operation);
  }

  private static <T> T withModels(boolean decimal, Supplier<T> operation) {
    return withModels(decimal, (model, manager) -> operation.get());
  }

  private static <T> T withModels(boolean decimal,
      java.util.function.BiFunction<com.ouroboros.data.model.DataModel,
          org.springframework.jdbc.datasource.DataSourceTransactionManager, T> operation) {
    return withModels(decimal, false, operation);
  }

  private static <T> T withModels(boolean decimal, boolean alternate,
      java.util.function.BiFunction<com.ouroboros.data.model.DataModel,
          org.springframework.jdbc.datasource.DataSourceTransactionManager, T> operation) {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:relation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    var meta = new DataModelMeta();
    meta.setNamespace("sales");
    meta.setName("Customer");
    meta.setRawName("customers");
    meta.setPrimaryKeys(List.of("id"));
    var fields = new java.util.ArrayList<>(List.of(field("id"), field("state")));
    if (decimal) {
      var key = field("code");
      key.setType("Decimal");
      fields.add(key);
    }
    meta.setFields(fields);
    var station = new SqlDataStation("relation-test", source, List.of(meta), MigrationStrategy.AUTO, "H2");
    var jdbc = new JdbcTemplate(source);
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    var data = station.getDataModelList().get(0);
    if (decimal) {
      data.insert(Map.of("id", "C-1", "state", "trusted", "code", new java.math.BigDecimal("1.00"))).get();
    } else {
      data.insert(Map.of("id", "C-1", "state", "trusted")).get();
    }
    var target = new DefaultBusinessModel(businessMeta("Customer"), mock(BusinessModelProvider.class));
    var provider = mock(BusinessModelProvider.class);
    when(provider.getBusinessModel("sales.Customer")).thenReturn(Optional.of(target));
    var ownerMeta = businessMeta("Order");
    var relation = new BusinessModelRelationMeta();
    relation.setName("customer");
    relation.setType("reference");
    relation.setModel("sales.Customer");
    if (decimal) {
      relation.setReferenceKey("code");
    }
    ownerMeta.setRelations(List.of(relation));
    var owner = new DefaultBusinessModel(ownerMeta, provider);
    com.ouroboros.businessmodel.BusinessModel resolvedOwner = owner;
    if (alternate) {
      var publicTarget = mock(com.ouroboros.businessmodel.BusinessModel.class, CALLS_REAL_METHODS);
      when(publicTarget.getFullName()).thenReturn("sales.Customer");
      when(publicTarget.isEnabled()).thenReturn(true);
      var publicRelation = mock(com.ouroboros.businessmodel.BusinessRelationView.class);
      when(publicRelation.getName()).thenReturn("customer");
      when(publicRelation.getType()).thenReturn("reference");
      when(publicRelation.getRelatedModel()).thenReturn(publicTarget);
      var publicOwner = mock(com.ouroboros.businessmodel.BusinessModel.class, CALLS_REAL_METHODS);
      doReturn(List.of(publicRelation)).when(publicOwner).getRelations();
      resolvedOwner = publicOwner;
    }
    var selectedOwner = resolvedOwner;
    try (var businessModels = mockStatic(BusinessModelCenter.class); var dataModels = mockStatic(DataModelCenter.class)) {
      businessModels.when(() -> BusinessModelCenter.getBusinessModel("sales.Order")).thenReturn(Optional.of(selectedOwner));
      dataModels.when(() -> DataModelCenter.getDataModel("sales.Customer")).thenReturn(Optional.of(data));
      return operation.apply(data, new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
    }
  }

  private static DefaultBusinessModelMeta businessMeta(String name) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sales");
    meta.setName(name);
    meta.setIsEnabled(true);
    return meta;
  }

  private static DataModelFieldMeta field(String name) {
    var field = new DataModelFieldMeta();
    field.setName(name);
    field.setRawName(name);
    field.setType("String");
    return field;
  }

  private static AccessControlParams params(Map<String, Object> filters, List<String> omitted) {
    var params = mock(AccessControlParams.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    when(params.getAllowedFields()).thenReturn(List.of("*"));
    when(params.getFilters()).thenReturn(filters);
    when(params.getDisallowedFields()).thenReturn(omitted);
    return params;
  }

  private static <T> T withPermission(boolean granted, AccessControlParams params, Supplier<T> operation) {
    var authentication = mock(OuroborosAuthentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    var authority = mock(OuroborosGrantedAuthority.class);
    when(authority.getAuthority()).thenReturn("sales.Customer.search");
    doReturn(granted ? List.of(authority) : List.of()).when(authentication).getAuthorities();
    when(authentication.getAccessControlParams("sales.Customer.search")).thenReturn(params);
    var context = mock(CurrentSecurityContext.class);
    when(context.findAuthentication()).thenReturn(Optional.of(authentication));
    return CurrentSecurityContextHolder.withContext(context, operation);
  }
}
