package com.ouroboros.mothership.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.*;
import com.ouroboros.businessmodel.action.*;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration;
import com.ouroboros.businessmodel.config.DefaultBasicAuditFieldsConfiguration;
import com.ouroboros.data.model.*;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the actual app AccessControl SPI, generated feature models, SQL plugins and Action Runtime together. */
class BusinessModelFeatureReadIntegrationTest {
  @Test
  void deletedQueryIsIndependentOfSearchAndCannotOverrideRootStateOrCurrentReadScope() {
    run(true, false, false, fixture -> {
      fixture.write(() -> {
        fixture.source.insert(Map.of("id", "D1", "tenantId", "T1", "secret", "cost1", "state", "A")).get();
        fixture.source.insert(Map.of("id", "D2", "tenantId", "T2", "secret", "cost2", "state", "A")).get();
        fixture.source.insert(Map.of("id", "L1", "tenantId", "T1", "secret", "cost3", "state", "A")).get();
        fixture.source.delete(List.of("D1", "D2")).get();
      });
      assertTrue(fixture.behavior.getActions().get("searchDeleted").getReadOnly());
      assertFalse(fixture.behavior.getActions().get("search").getIsEnabled());
      assertEquals(0, fixture.behavior.getActions().get("searchDeleted").getEmits().size());
      var grants = fixture.permissions(true, Map.of("tenantId", "T1"), List.of("secret"));
      var result = fixture.invoke("searchDeleted", Map.of("query", Map.of("order", "id", "limit", 1)), grants);
      assertEquals(1L, result.get("total"));
      var rows = (List<?>) result.get("items"); assertEquals(1, rows.size());
      var row = (Map<?, ?>) rows.getFirst(); assertEquals("D1", row.get("id")); assertFalse(row.containsKey("secret"));
      var http = fixture.http("searchDeleted", Map.of("query", Map.of("skip", 1, "limit", 1)), grants);
      assertEquals(1, ((Number) http.get("total")).intValue()); assertTrue(((List<?>) http.get("items")).isEmpty());
      assertEquals(0L, fixture.invoke("searchDeleted", Map.of("query", Map.of("where", Map.of("isDeleted", false))), grants).get("total"));
      var either = Map.of("$or", List.of(Map.of("isDeleted", false), Map.of("state", "A")));
      assertEquals(1L, fixture.invoke("searchDeleted", Map.of("query", Map.of("where", either)), grants).get("total"));
      var ordinary = fixture.permissions(false, Map.of("tenantId", "T1"), List.of("secret"));
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchDeleted", Map.of(), ordinary));
      fixture.as(ordinary, () -> {
        assertTrue(fixture.source.query(Map.of("where", Map.of("isDeleted", true))).get().isEmpty());
        assertEquals(0L, fixture.source.count(Map.of("isDeleted", true)).get());
        assertFalse(fixture.source.query(Map.of()).get().isEmpty()); return null;
      });
    });
  }

  @Test
  void archiveAndHistoryUseTheProjectedBusinessKeyMappingsAndRejectRawModelBypasses() {
    run(false, true, true, fixture -> {
      fixture.write(() -> {
        fixture.source.insert(Map.of("id", "O1", "tenantId", "T1", "secret", "cost", "state", "DRAFT", "businessKey", "original", "sourceModel", "business-value")).get();
        fixture.source.update("O1", Map.of("state", "DONE")).get();
        fixture.source.delete("O1").get();
      });
      var grants = fixture.permissions(true, Map.of(), List.of("secret"));
      var archive = fixture.invoke("searchArchive", Map.of(), grants);
      assertEquals(1L, archive.get("total"));
      var row = (Map<?, ?>) ((List<?>) archive.get("items")).getFirst();
      assertEquals("O1", row.get("sourceId")); assertEquals(fixture.model, row.get("sourceModel"));
      assertEquals("business-value", row.get("sourceSourceModel")); assertFalse(row.containsKey("secret"));
      var plan = fixture.behavior.getFeatureQueryPlans().get("searchHistory");
      assertEquals("businessKey1", plan.businessKeyField());
      var history = fixture.invoke("searchHistory", Map.of("recordId", "O1", "query", Map.of("limit", 1, "order", "id DESC")), grants);
      assertTrue(((Number) history.get("total")).longValue() >= 2);
      var version = (Map<?, ?>) ((List<?>) history.get("items")).getFirst();
      assertEquals("O1", version.get(plan.businessKeyField())); assertFalse(version.containsKey("secret"));
      assertEquals(0L, fixture.invoke("searchArchive", Map.of("query", Map.of("where", Map.of("sourceModel", "other"))), grants).get("total"));
      assertEquals(0L, fixture.invoke("searchHistory", Map.of("recordId", "O2"), grants).get("total"));
      assertEquals(1, ((Number) fixture.http("searchArchive", Map.of(), grants).get("total")).intValue());
      assertTrue(((Number) fixture.http("searchHistory", Map.of("recordId", "O1"), grants).get("total")).longValue() >= 2);
      var ordinary = fixture.permissions(false, Map.of(), List.of());
      fixture.as(ordinary, () -> {
        for (var query : fixture.behavior.getFeatureQueryPlans().values()) {
          var target = fixture.models.get(query.queryModel());
          assertTrue(target.query(Map.of()).isFailure()); assertTrue(target.count(Map.of()).isFailure());
        }
        return null;
      });
      var partial = fixture.permissions(true, Map.of("tenantId", "T1"), List.of());
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchArchive", Map.of(), partial));
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchHistory", Map.of("recordId", "O1"), partial));
    });
  }

  @Test
  void auditedSnapshotsKeepSourceOperatorsAndEnforceTheirFieldReadRestrictions() {
    var meta = meta(false, true, true);
    meta.setExtraProp("features", Map.of("isEnabledAuditFields", true));
    run(meta, new DefaultBasicAuditFieldsConfiguration(), fixture -> {
      DataOperationIdentityProviders.withCurrentOperator("original-operator", () -> {
        fixture.write(() -> {
          fixture.source.insert(Map.of("id", "AUDIT", "state", "DRAFT")).get();
          fixture.source.update("AUDIT", Map.of("state", "DONE")).get();
          fixture.source.delete("AUDIT").get();
        });
        return null;
      });
      for (String action : List.of("searchArchive", "searchHistory")) {
        var plan = fixture.behavior.getFeatureQueryPlans().get(action);
        var input = "searchHistory".equals(action) ? Map.<String, Object>of("recordId", "AUDIT") : Map.<String, Object>of();
        var result = fixture.invoke(action, input, fixture.permissions(true, Map.of(), List.of()));
        var rows = (List<?>) result.get("items");
        assertEquals("searchHistory".equals(action) ? 3 : 1, rows.size());
        for (var value : rows) {
          var row = (Map<?, ?>) value;
          assertEquals("original-operator", row.get(plan.sourceFieldMappings().get("createdBy")));
          assertNotNull(row.get(plan.sourceFieldMappings().get("createdAt")));
        }
        var restricted = fixture.invoke(action, input, fixture.permissions(true, Map.of(), List.of("createdBy", "updatedBy")));
        for (var value : (List<?>) restricted.get("items")) {
          var row = (Map<?, ?>) value;
          assertFalse(row.containsKey(plan.sourceFieldMappings().get("createdBy")));
          assertFalse(row.containsKey(plan.sourceFieldMappings().get("updatedBy")));
        }
      }
    });
  }

  @Test
  void historyOfAnExistingObjectUsesItsCurrentReadScopeInsteadOfSnapshotFilterValues() {
    run(false, false, true, fixture -> {
      fixture.write(() -> {
        fixture.source.insert(Map.of("id", "H1", "tenantId", "OLD", "secret", "cost", "state", "DRAFT")).get();
        fixture.source.update("H1", Map.of("tenantId", "CURRENT", "state", "DONE")).get();
      });
      var current = fixture.permissions(true, Map.of("tenantId", "CURRENT"), List.of("secret"));
      var result = fixture.invoke("searchHistory", Map.of("recordId", "H1"), current);
      assertEquals(2L, result.get("total"));
      var items = (List<?>) result.get("items");
      assertTrue(items.stream().map(Map.class::cast).anyMatch(row -> "OLD".equals(row.get("tenantId"))));
      assertTrue(items.stream().map(Map.class::cast).noneMatch(row -> row.containsKey("secret")));
      var previous = fixture.permissions(true, Map.of("tenantId", "OLD"), List.of());
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchHistory", Map.of("recordId", "H1"), previous));
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchHistory", Map.of(), current));
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchHistory", Map.of("recordId", "H1", "query", Map.of("from", "other")), current));
      var plan = fixture.behavior.getFeatureQueryPlans().get("searchHistory");
      fixture.as(current, () -> {
        var target = fixture.models.get(plan.queryModel());
        var rows = target.query(Map.of("where", Map.of(plan.businessKeyField(), "H1"))).get();
        assertEquals(2, rows.size()); assertTrue(rows.stream().noneMatch(row -> row.containsKey("secret")));
        Map<String, Object> unbounded = Map.of("$or", List.of(Map.of(plan.businessKeyField(), "H1"), Map.of("state", "DRAFT")));
        assertTrue(target.query(Map.of("where", unbounded)).isFailure());
        assertTrue(target.count(unbounded).isFailure()); return null;
      });
    });
  }

  @Test
  void historyKeepsNumericBusinessKeysAndRejectsInvalidKeysOrHistoricalRowIdsAsObjectIdentity() {
    var meta = meta(false, false, true); meta.getFields().getFirst().setType("整数数字");
    run(meta, new DefaultBasicAuditFieldsConfiguration(), fixture -> {
      fixture.write(() -> fixture.source.insert(Map.of("id", 701, "tenantId", "T1", "state", "A")).get());
      var grants = fixture.permissions(true, Map.of(), List.of());
      var result = fixture.invoke("searchHistory", Map.of("recordId", 701), grants);
      assertEquals(1L, result.get("total"));
      var plan = fixture.behavior.getFeatureQueryPlans().get("searchHistory");
      var row = (Map<?, ?>) ((List<?>) result.get("items")).getFirst();
      assertEquals(701, ((Number) row.get(plan.businessKeyField())).intValue());
      assertEquals(0L, fixture.invoke("searchHistory", Map.of("recordId", row.get("id")), grants).get("total"));
      assertEquals(1L, fixture.invoke("searchHistory", Map.of("recordId", "701"), grants).get("total"));
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke("searchHistory", Map.of("recordId", "invalid-number"), grants));
    });
  }

  @Test
  void softDeleteReadsTheConfiguredPhysicalColumnsAndDisabledAuditFields() {
    var properties = new com.ouroboros.businessmodel.config.BasicAuditFieldsProperties();
    properties.setIsDeleted(new com.ouroboros.businessmodel.config.BasicAuditFieldsProperties.FieldSettingImpl("removed_flag"));
    properties.setDeletedAt(new com.ouroboros.businessmodel.config.BasicAuditFieldsProperties.FieldSettingImpl("removed_time"));
    var disabled = new com.ouroboros.businessmodel.config.BasicAuditFieldsProperties.FieldSettingImpl(); disabled.setDisabled(true);
    properties.setDeletedBy(disabled);
    run(meta(true, false, false), properties, fixture -> {
      fixture.write(() -> { fixture.source.insert(Map.of("id", "C1", "state", "A")).get(); fixture.source.delete("C1").get(); });
      var grants = fixture.permissions(true, Map.of(), List.of());
      var result = fixture.invoke("searchDeleted", Map.of(), grants);
      assertEquals(1L, result.get("total"));
      var row = (Map<?, ?>) ((List<?>) result.get("items")).getFirst();
      assertEquals(true, row.get("isDeleted")); assertNotNull(row.get("deletedAt")); assertFalse(row.containsKey("deletedBy"));
    });
  }

  @Test
  void archiveWinsOverSoftDeleteAndDisabledFeaturesCannotBeInvoked() {
    var meta = meta(true, true, false); var provider = emptyProvider();
    var business = new DefaultBusinessModel(meta, provider);
    assertFalse(business.getBehavior().getActions().containsKey("searchDeleted"));
    assertTrue(business.getBehavior().getActions().containsKey("searchArchive"));
    meta.setEnableArchiveDelete(false); meta.setExtraProp("features", Map.of("isEnabledSoftDelete", false));
    var disabled = new DefaultBusinessModel(meta, provider);
    assertFalse(disabled.getBehavior().getActions().containsKey("searchDeleted"));
    assertFalse(disabled.getBehavior().getActions().containsKey("searchArchive"));
    run(false, false, false, fixture -> {
      for (String action : List.of("searchDeleted", "searchArchive", "searchHistory")) {
        assertThrows(com.ouroboros.exception.BusinessException.class, () -> fixture.invoke(action, Map.of(), fixture.permissions(true, Map.of(), List.of())));
        assertThrows(jakarta.servlet.ServletException.class, () -> fixture.http(action, Map.of(), fixture.permissions(true, Map.of(), List.of())));
      }
    });
  }

  private static void run(boolean soft, boolean archive, boolean history, java.util.function.Consumer<Fixture> check) {
    run(meta(soft, archive, history), new DefaultBasicAuditFieldsConfiguration(), check);
  }

  private static void run(DefaultBusinessModelMeta meta, BasicAuditFieldsConfiguration config, java.util.function.Consumer<Fixture> check) {
    var business = new DefaultBusinessModel(meta, emptyProvider());
    com.ouroboros.businessmodel.projection.ModelBundle bundle;
    try (var configuration = mockStatic(BasicAuditFieldsConfiguration.class)) {
      configuration.when(BasicAuditFieldsConfiguration::getConfiguration).thenReturn(config);
      bundle = business.buildModelBundle();
    }
    String model = business.getFullName();
    var behavior = bundle.getModel(BusinessModelBehavior.class, model).orElseThrow();
    javax.sql.DataSource connection; String dialect;
    String mysql = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    if (mysql == null) {
      var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:feature_" + UUID.randomUUID() + ";MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"); h2.setUser("sa");
      connection = h2; dialect = "H2";
    } else {
      connection = new DriverManagerDataSource(mysql, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")); dialect = "MySQL";
    }
    var tx = new DataSourceTransactionManager(connection); var jdbc = new JdbcTemplate(connection);
    var station = new SqlDataStation("featureReads", connection, bundle.getModelMap(DataModelMeta.class).values(), MigrationStrategy.AUTO, dialect);
    station.generateMigrationSql(station.getDataModelList()).getOrElseThrow(error -> error).forEach(jdbc::execute);
    Map<String, DataModel> models = new LinkedHashMap<>(); station.getDataModelList().forEach(data -> models.put(data.getFullName(), data));
    var contracts = new BusinessModelBehaviorProvider() {
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.ofNullable(name.equals(model) ? behavior : null); }
      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(model, behavior); }
    };
    var runtime = new DefaultBusinessActionRuntime(contracts, (flow, input) -> Try.failure(new AssertionError("feature must use its Handler")),
        List.of(new BusinessModelFeatureQueryHandler(contracts)), tx, audit -> { });
    var definitions = bundle.getModelMap(Authority.class);
    try (var data = mockStatic(DataModelCenter.class); var authorities = mockStatic(AuthorityCenter.class)) {
      authorities.when(() -> AuthorityCenter.getAuthority(anyString()))
          .thenAnswer(invocation -> Optional.ofNullable(definitions.get(invocation.getArgument(0, String.class))));
      data.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(invocation -> Optional.ofNullable(models.get(invocation.getArgument(0, String.class))));
      data.when(DataModelCenter::getDataModelMap).thenReturn(models); data.when(DataModelCenter::hasSnapshot).thenReturn(true);
      check.accept(new Fixture(model, models.get(model), models, behavior, runtime, new TransactionTemplate(tx)));
    }
  }

  private record Fixture(String model, DataModel source, Map<String, DataModel> models, BusinessModelBehavior behavior,
                         DefaultBusinessActionRuntime runtime, TransactionTemplate tx) {
    void write(Runnable operation) { tx.executeWithoutResult(status -> operation.run()); }
    Map<String, AccessControlParams> permissions(boolean feature, Map<String, Object> filter, List<String> omit) {
      var grants = new LinkedHashMap<String, AccessControlParams>(); grants.put(model + ".search", params(filter, omit));
      if (feature) { for (String action : List.of("searchDeleted", "searchArchive", "searchHistory")) { grants.put(model + "." + action, params(Map.of(), List.of())); } }
      return grants;
    }
    Map<?, ?> invoke(String action, Map<String, Object> input, Map<String, AccessControlParams> permissions) {
      return (Map<?, ?>) as(permissions, () -> runtime.invoke(model, action, BusinessActionInput.paramsOnly(input)));
    }
    Map<?, ?> http(String action, Map<String, Object> params, Map<String, AccessControlParams> permissions) {
      return as(permissions, () -> Try.of(() -> {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new BusinessActionDescriptionController(runtime)).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/business-model-behaviors/" + model + "/actions/" + action + "/invoke")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(com.ouroboros.util.Json.toJsonString(Map.of("input", Map.of("params", params)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        return (Map<?, ?>) com.ouroboros.util.Json.toMap(response.getContentAsString()).get("data");
      }).get());
    }
    <T> T as(Map<String, AccessControlParams> permissions, Supplier<T> operation) {
      var role = mock(Role.class); var user = mock(User.class); when(user.getUserIdentity()).thenReturn("feature-reader");
      when(user.getRoles()).thenReturn(List.of(role));
      var grants = permissions.entrySet().stream().map(entry -> {
        var grant = mock(OuroborosGrantedAuthority.class); var authority = mock(Authority.class);
        when(authority.getFullName()).thenReturn(entry.getKey()); when(grant.getAuthority()).thenReturn(entry.getKey());
        when(grant.getAuthorityObject()).thenReturn(authority); when(grant.getAccessControlParams()).thenReturn(entry.getValue()); return grant;
      }).toList();
      when(role.getAuthorities()).thenAnswer(invocation -> grants);
      return CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user), operation);
    }
  }

  private static AccessControlParams params(Map<String, Object> filters, List<String> omit) {
    return new AccessControlParams() {
      public Map<String, Object> getFilters() { return filters; }
      public List<String> getAllowedFields() { return List.of("*"); }
      public List<String> getDisallowedFields() { return omit; }
    };
  }

  private static DefaultBusinessModelMeta meta(boolean soft, boolean archive, boolean history) {
    var meta = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse("""
        {"modelType":"default","namespace":"featureReads","name":"Order","title":"Order","isEnabled":true,
        "fields":[{"name":"id","type":"String","isPrimaryKey":true},{"name":"tenantId","type":"String"},
          {"name":"secret","type":"String"},{"name":"state","type":"String"},
          {"name":"businessKey","type":"String"},{"name":"sourceModel","type":"String"}]}
        """).get();
    meta.setExtraProp("rawTableName", "feature_read_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    meta.setExtraProp("features", Map.of("isEnabledSoftDelete", soft)); meta.setEnableArchiveDelete(archive); meta.setEnableScd2History(history);
    return meta;
  }

  private static BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
  }
}
