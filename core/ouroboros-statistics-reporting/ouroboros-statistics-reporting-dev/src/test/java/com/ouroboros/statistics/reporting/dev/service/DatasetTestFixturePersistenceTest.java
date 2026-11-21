package com.ouroboros.statistics.reporting.dev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.migration.DatabaseMigration;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DefaultDataModel;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;

class DatasetTestFixturePersistenceTest {
  @Test
  void testConnectionReferenceRoundTripsThroughThePlatformTypedModel() throws Exception {
    var source = source();
    source.setURL(source.getURL() + ";DATABASE_TO_LOWER=TRUE");
    var meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.statistics.reporting.dev.model")
        .get("DatasetDevConfigModel");
    new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
    var station = new com.ouroboros.data.station.SqlDataStation("fixture-test", source, List.of(meta),
        com.ouroboros.data.model.MigrationStrategy.DISABLED, "H2");
    var model = station.getDataModelList().getFirst();
    assertTrue(station.migrate(List.of(model)).isRight());
    var typed = new com.ouroboros.data.model.BaseTypedDataModel<String, com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel>(
        model, com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel.class);
    var service = new com.ouroboros.statistics.reporting.dev.service.impl.DefaultDatasetDevConfigService() {
      @Override
      protected com.ouroboros.data.model.TypedDataModel<String, com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel> getDataModel() {
        return typed;
      }
    };
    var fixture = new com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel();
    fixture.setId("connection-fixture");
    fixture.setAppName("crm");
    fixture.setDatasetName("sales.orders");
    fixture.setDatasource("default");
    var selected = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    selected.setKind("platform");
    selected.setPlatformDataSourceName("warehouse");
    selected.setEnvName("dev");
    fixture.setTestConnection(selected);
    service.save(fixture).get();
    var loaded = service.findByDataset("crm", "sales.orders").orElseThrow();
    assertEquals("warehouse", loaded.getTestConnection().getPlatformDataSourceName());
    assertEquals("dev", loaded.getTestConnection().getEnvName());
    assertTrue(service.findByDataset("other", "sales.orders").isEmpty());
    selected.setKind("runtime");
    selected.setRuntimeId("runtime-1");
    fixture.setTestConnection(selected);
    service.save(fixture).get();
    assertEquals("runtime-1", service.findByDataset("crm", "sales.orders").orElseThrow().getTestConnection().getRuntimeId());
  }

  @Test
  void concurrentInsertsKeepOnlyOneFixtureForTheSameAppAndDataset() throws Exception {
    var source = source();
    migrate(source, model(true));
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> {
        start.await();
        return insert(source, "1", "app-a");
      });
      var second = executor.submit(() -> {
        start.await();
        return insert(source, "2", "app-a");
      });
      start.countDown();
      assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    }
    assertEquals(1, count(source));
    assertTrue(insert(source, "3", "app-b"));
    assertEquals(2, count(source));
  }

  @Test
  void migrationRejectsHistoricalDuplicatesWithoutDeletingThem() throws Exception {
    var source = source();
    migrate(source, model(false));
    assertTrue(insert(source, "1", "app-a"));
    assertTrue(insert(source, "2", "app-a"));
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      var result = new DatabaseMigration(connection, "", "fixture-test").migrate(List.of(model(true)));
      assertTrue(result.isLeft());
    }
    assertEquals(2, count(source));
  }

  private DataModel model(boolean unique) {
    var meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.statistics.reporting.dev.model")
        .get("DatasetDevConfigModel");
    if (!unique) {
      meta.setUniqueConstraints(List.of());
    }
    DataStation<?> station = mock(DataStation.class);
    when(station.getDataAdapter()).thenReturn(mock(DataAdapter.class));
    return new DefaultDataModel(meta, station);
  }

  private JdbcDataSource source() {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:fixture_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    return source;
  }

  private void migrate(JdbcDataSource source, DataModel model) throws SQLException {
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      var result = new DatabaseMigration(connection, "", "fixture-test").migrate(List.of(model));
      assertTrue(result.isRight(), () -> result.getLeft().toString());
    }
  }

  private boolean insert(JdbcDataSource source, String id, String app) throws SQLException {
    try (var connection = source.getConnection(); var statement = connection.prepareStatement(
        "insert into t_statistics_dataset_dev_config "
            + "(id, app_name, dataset_name, datasource, parameters) values (?, ?, 'sales', 'main', '{}')")) {
      statement.setString(1, id);
      statement.setString(2, app);
      statement.executeUpdate();
      return true;
    } catch (SQLException error) {
      if ("23505".equals(error.getSQLState())) {
        return false;
      }
      throw error;
    }
  }

  private int count(JdbcDataSource source) throws SQLException {
    try (var connection = source.getConnection(); var statement = connection.createStatement();
         var result = statement.executeQuery("select count(*) from t_statistics_dataset_dev_config")) {
      result.next();
      return result.getInt(1);
    }
  }
}
