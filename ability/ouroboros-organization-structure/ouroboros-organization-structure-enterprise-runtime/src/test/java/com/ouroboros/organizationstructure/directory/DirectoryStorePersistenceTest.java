package com.ouroboros.organizationstructure.directory;

import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import com.ouroboros.data.model.*;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import static org.assertj.core.api.Assertions.*;

class DirectoryStorePersistenceTest {
  private org.mockito.MockedStatic<DataOperationIdentityProviders> operator;
  @org.junit.jupiter.api.BeforeEach void authenticatedOperator() {
    operator = org.mockito.Mockito.mockStatic(DataOperationIdentityProviders.class);
    operator.when(DataOperationIdentityProviders::findCurrentOperator).thenReturn(Optional.of("administrator"));
  }
  @org.junit.jupiter.api.AfterEach void clearOperator() { operator.close(); }
  @Test void databaseEnforcesTwoWayIdentityAndOneSyncOwnerAndKeepsUnboundHistory() {
    var fixture = new Fixture(); var store = fixture.store;
    var first = store.insert(binding("one", "Member-A", "employee"));
    assertThatThrownBy(() -> store.insert(binding("one", "Member-A", "another"))).hasMessageContaining("读写失败");
    assertThatThrownBy(() -> store.insert(binding("one", "other", "employee"))).hasMessageContaining("读写失败");
    assertThatThrownBy(() -> store.insert(binding("two", "other", "employee"))).hasMessageContaining("读写失败");
    var changes = new LinkedHashMap<String, Object>(); changes.put("active", false); changes.put("syncOwnerKey", null);
    changes.put("externalRouteKey", null); changes.put("localRouteKey", null); store.update(first, changes);
    var second = store.insert(binding("one", "Member-A", "employee"));
    assertThat(second.getId()).isNotEqualTo(first.getId()); assertThat(store.bindings("one")).hasSize(2);
    assertThat(store.binding(first.getId()).orElseThrow().getActive()).isFalse();
    // Opaque external IDs are case sensitive even on a case insensitive database collation.
    assertThat(store.insert(binding("one", "member-a", "different"))).isNotNull();
  }

  @Test void staleCompareAndSetFailsAndOrganizationWritesRollbackWithTheAssociation() {
    var fixture = new Fixture(); var store = fixture.store;
    var original = store.insert(binding("one", "m", "employee"));
    var stale = store.binding(original.getId()).orElseThrow();
    store.update(original, Map.of("active", false));
    assertThatThrownBy(() -> store.update(stale, Map.of("active", true))).hasMessageContaining("已改变");
      assertThatThrownBy(() -> new TransactionTemplate(fixture.manager).execute(status -> {
      var employee = new EmployeeEntity(); employee.setId("new-person"); employee.setName("不应保留");
      fixture.employees.insert(employee).getOrElseThrow(error -> error instanceof com.ouroboros.data.exception.RecordValidationException validation
          ? new IllegalStateException(validation.getFieldErrors().toString(), error) : new IllegalStateException(error));
      store.insert(binding("other", "m2", "employee")); return null;
      })).hasMessageContaining("读写失败");
    assertThat(fixture.employees.get("new-person").get()).isNull();
    assertThat(store.bindings("other")).isEmpty();
  }

  @Test void scopeLockAndRunCompletionPersistAndRejectASecondWriter() {
    var fixture = new Fixture(); var store = fixture.store;
    var scope = new DirectorySyncScope(); scope.setId("company"); scope.setConnectionId("one"); scope.setExternalRootId("root");
    scope.setEnabled(true); scope.setSyncMobile(false); scope.setSyncEmail(false); store.saveScope(scope, null);
    var stale = store.scope("company").orElseThrow();
    new TransactionTemplate(fixture.manager).executeWithoutResult(status -> store.claimScope(scope));
    assertThatThrownBy(() -> store.claimScope(stale)).hasMessageContaining("已改变");
    var run = new DirectorySyncRun(); run.setId("run"); run.setConnectionId("one"); run.setConnectionRevision("rev");
    run.setScopeId("company"); run.setScopeVersion(1L); run.setStatus("PREVIEW"); run.setComplete(true);
    run.setSnapshotJson("{}"); run.setBaseline("digest"); run.setCreatedAt(java.time.LocalDateTime.now());
    store.insertRun(run); store.completeRun("run");
    assertThat(store.run("run").orElseThrow().getStatus()).isEqualTo("APPLIED");
    assertThatThrownBy(() -> store.completeRun("run")).hasMessageContaining("已改变");
  }

  @Test void cleanupDropsExpiredPersonalContentsButKeepsBindingsAndStableRunFacts() {
    var fixture = new Fixture(); var store = fixture.store;
    var now = java.time.LocalDateTime.now();
    store.insert(binding("one", "member", "employee"));
    // Historical fixtures must retain their original dates instead of receiving today's audit timestamp.
    var history = fixture.runs.withoutPlugins();
    history.insert(run("recent", "PREVIEW", now.minusMinutes(5), null)).get();
    history.insert(run("abandoned", "PREVIEW", now.minusDays(8), null)).get();
    history.insert(run("applied", "APPLIED", now.minusDays(10), now.minusDays(8))).get();
    history.insert(run("old", "APPLIED", now.minusDays(40), now.minusDays(31))).get();
    history.insert(run("recoverable", "FAILED", now.minusDays(50), null)).get();
    long cleared = store.clearExpiredRunContents(now, 7, 30);
    assertThat(store.run("recent").orElseThrow().getSnapshotJson()).contains("personal");
    var expired = store.run("abandoned").orElseThrow();
    assertThat(expired.getStatus()).isEqualTo("EXPIRED");
    assertThat(expired.getSnapshotJson()).isEqualTo("{}");
    assertThat(expired.getConnectionId()).isEqualTo("one");
    assertThat(store.run("applied").orElseThrow().getDecisionsJson()).contains("choice");
    assertThat(store.run("old").orElseThrow().getDecisionsJson()).isEqualTo("{}");
    assertThat(cleared).isEqualTo(4);
    assertThat(store.run("recoverable").orElseThrow().getSnapshotJson()).contains("personal");
    assertThat(store.bindings("one")).hasSize(1);
    assertThat(store.clearExpiredRunContents(now, 7, 30)).isZero();
    assertThatThrownBy(() -> store.clearExpiredRunContents(now, 7, 3)).hasMessageContaining("保留期");
  }

  private static DirectorySyncRun run(String id, String status, java.time.LocalDateTime created,
      java.time.LocalDateTime applied) {
    var run = new DirectorySyncRun(); run.setId(id); run.setConnectionId("one"); run.setConnectionRevision("rev");
    run.setScopeId("company"); run.setScopeVersion(1L); run.setStatus(status); run.setComplete(true);
    run.setSnapshotJson("{\"personal\":\"sensitive\"}"); run.setDecisionsJson("{\"choice\":\"member\"}");
    run.setBaseline("digest"); run.setCreatedAt(created); run.setAppliedAt(applied);
    run.setCreatedBy("administrator"); run.setUpdatedBy("administrator"); run.setUpdatedAt(created); return run;
  }

  private static DirectoryBinding binding(String connection, String external, String local) {
    var b = new DirectoryBinding(); b.setConnectionId(connection); b.setObjectType("MEMBER"); b.setExternalId(external);
    b.setLocalId(local); b.setActive(true); b.setSyncOwnerKey("MEMBER:" + local); DirectoryBindingCommands.route(b); return b;
  }

  static class Fixture {
    final DirectoryStore store;
    final TypedDataModel<String, EmployeeEntity> employees;
    final TypedDataModel<String, DirectorySyncRun> runs;
    final DataSourceTransactionManager manager;
    Fixture() {
      new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
      String mysql = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      javax.sql.DataSource source;
      if (mysql != null && !mysql.isBlank()) {
        source = new DriverManagerDataSource(mysql, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      } else {
        var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:directory_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa"); source = h2;
      }
      manager = new DataSourceTransactionManager(source);
      var metadata = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.organizationstructure.directory").values());
      metadata.add(TypedModelMetaHelper.scanModelMate("com.ouroboros.organizationstructure.entity").get("EmployeeEntity"));
      String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
      metadata.forEach(meta -> meta.setRawName(meta.getRawName() + "_" + suffix));
      var station = new SqlDataStation("directory-test", source, metadata, MigrationStrategy.AUTO, mysql == null ? "H2" : "MySQL");
      var jdbc = new JdbcTemplate(source); station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      var models = new HashMap<String, DataModel>(); station.getDataModelList().forEach(model -> models.put(model.getName(), model));
      runs = new BaseTypedDataModel<>(models.get("DirectorySyncRun"), DirectorySyncRun.class);
      store = new DirectoryStore(new BaseTypedDataModel<>(models.get("DirectoryBinding"), DirectoryBinding.class),
          new BaseTypedDataModel<>(models.get("DirectorySyncScope"), DirectorySyncScope.class),
          runs);
      employees = new BaseTypedDataModel<>(models.get("EmployeeEntity"), EmployeeEntity.class);
    }
  }
}
