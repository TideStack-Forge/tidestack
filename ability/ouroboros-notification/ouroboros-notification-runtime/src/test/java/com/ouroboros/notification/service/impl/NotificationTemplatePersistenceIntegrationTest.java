package com.ouroboros.notification.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.notification.template.NotificationTemplateRevision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class NotificationTemplatePersistenceIntegrationTest {
  @Test
  void conditionalWritesSurviveRestartAndRejectStaleUpdatesAndDeletes() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      var service = new NotificationTemplateServiceImpl();
      service.initializeRuntimeManagement();
      var first = template("first", "Welcome");
      assertTrue(service.insertOverride(first));
      assertFalse(service.insertOverride(template("raced", "WELCOME")));
      var restarted = new NotificationTemplateServiceImpl();
      var stored = restarted.getOverride("welcome");
      assertEquals("first", stored.getId());
      assertEquals(1L, stored.getRevision());
      assertFalse(stored.isEnabled());
      assertTrue(stored.getChannel().isEmpty());
      var updated = template("first", "Welcome"); updated.setTitle("next"); updated.setRevision(2L);
      assertTrue(restarted.replaceOverride(updated, 1L));
      assertFalse(restarted.replaceOverride(updated, 1L));
      assertFalse(restarted.deleteOverride("first", 1L));
      assertEquals("next", restarted.getOverride("WELCOME").getTitle());
      assertTrue(restarted.deleteOverride("first", 2L));
      assertNull(restarted.getOverride("welcome"));
      return null;
    });
  }

  @Test
  void onlyOneConcurrentFirstInsertCanClaimACaseInsensitiveName() throws Exception {
    var fixture = new Fixture(false);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withModels(() -> {
        await(start); return new NotificationTemplateServiceImpl().insertOverride(template("one", "Notice"));
      }));
      var second = workers.submit(() -> fixture.withModels(() -> {
        await(start); return new NotificationTemplateServiceImpl().insertOverride(template("two", "NOTICE"));
      }));
      start.countDown();
      assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }
    assertEquals(1L, fixture.model.count(Map.of()).get());
  }

  @Test
  void failedManagementTransactionLeavesThePreviousSnapshotAndRevision() {
    var fixture = new Fixture(false);
    fixture.withModels(() -> {
      var service = new NotificationTemplateServiceImpl();
      assertTrue(service.insertOverride(template("one", "notice")));
      assertThrows(IllegalStateException.class, () -> new TransactionTemplate(fixture.manager).execute(status -> {
        var update = template("one", "notice"); update.setTitle("rolled back"); update.setRevision(2L);
        assertTrue(service.replaceOverride(update, 1L));
        throw new IllegalStateException("validation after conditional write failed");
      }));
      assertEquals("", service.getOverride("notice").getTitle());
      assertEquals(1L, service.getOverride("notice").getRevision());
      return null;
    });
  }

  @Test
  void additiveMigrationKeepsLegacyContentAndBackfillsOnlyConcurrencyMetadata() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var service = new NotificationTemplateServiceImpl();
      var report = service.initializeRuntimeManagement();
      assertEquals(1, report);
      var old = service.getOverride("LEGACY");
      assertEquals("old-id", old.getId()); assertEquals("old title", old.getTitle());
      assertEquals(1L, old.getRevision()); assertNull(old.getBaseDefaultRevision());
      assertEquals(0, service.initializeRuntimeManagement());
      assertEquals(Map.of("email", Map.of("schema", Map.of("body", "old"))), old.getChannel());
      return null;
    });
  }

  @Test
  void migrationReportsCaseConflictsWithoutChoosingOrChangingHistoricalRows() {
    var fixture = new Fixture(true);
    fixture.model.insert(Map.of("id", "duplicate", "name", "LEGACY", "title", "second", "isEnabled", false,
        "payloadSchema", Map.of(), "channel", Map.of())).get();
    fixture.withModels(() -> {
      var failure = assertThrows(BusinessException.class, () -> new NotificationTemplateServiceImpl().initializeRuntimeManagement());
      assertTrue(failure.getMessage().contains("NOTIFICATION_TEMPLATE_DUPLICATE"));
      assertEquals(2L, fixture.model.count(Map.of()).get());
      assertNull(fixture.model.get("old-id").get().get("revision"));
      return null;
    });
  }

  static NotificationTemplateModel template(String id, String name) {
    var model = new NotificationTemplateModel(); model.setId(id); model.setName(name); model.setTitle("");
    model.setPayloadSchema(Map.of()); model.setChannel(Map.of()); model.setEnabled(false);
    model.setNameKey(NotificationTemplateRevision.nameKey(name)); model.setRevision(1L);
    model.setBaseDefaultRevision("sha256:base");
    return model;
  }

  static void await(CountDownLatch latch) {
    try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
  }

  static class Fixture {
    final DataModel model;
    final DataSourceTransactionManager manager;

    Fixture(boolean legacy) {
      new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
      String mysqlUrl = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      boolean mysql = mysqlUrl != null && !mysqlUrl.isBlank();
      DataSource source;
      if (mysql) {
        source = new DriverManagerDataSource(mysqlUrl, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
            System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      } else {
        var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:template_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa"); source = h2;
      }
      manager = new DataSourceTransactionManager(source);
      var jdbc = new JdbcTemplate(source);
      var meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.notification.service.model").get("NotificationTemplateModel");
      meta.setRawName("t_notification_template_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
      String dbType = mysql ? "MySQL" : "H2";
      if (legacy) {
        var fields = new ArrayList<>(meta.getFields());
        meta.setFields(fields.stream().filter(field -> !List.of("revision", "nameKey", "baseDefaultRevision", "updateBy", "updateAt")
            .contains(field.getName())).toList());
        var constraints = meta.getUniqueConstraints(); meta.setUniqueConstraints(List.of());
        var old = new SqlDataStation("old-template", source, List.of(meta), MigrationStrategy.AUTO, dbType);
        old.generateMigrationSql(old.getDataModelList()).get().forEach(jdbc::execute);
        old.getDataModelList().getFirst().insert(Map.of("id", "old-id", "name", "legacy", "title", "old title",
            "isEnabled", false, "payloadSchema", Map.of(), "channel", Map.of("email", Map.of("schema", Map.of("body", "old"))))).get();
        meta.setFields(fields); meta.setUniqueConstraints(constraints);
      }
      var station = new SqlDataStation("template-integration", source, List.of(meta), MigrationStrategy.AUTO, dbType);
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      model = station.getDataModelList().getFirst();
    }

    <T> T withModels(Supplier<T> action) {
      try (var center = mockStatic(DataModelCenter.class)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call ->
            "NotificationTemplateModel".equals(call.getArgument(0)) ? Optional.of(model) : Optional.empty());
        return action.get();
      }
    }
  }
}
