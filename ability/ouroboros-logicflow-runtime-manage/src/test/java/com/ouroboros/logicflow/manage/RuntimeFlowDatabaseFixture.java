package com.ouroboros.logicflow.manage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.logicflow.manage.repository.DataRuntimeFlowStore;


/** Real typed data + QueryDSL + H2 + Spring transaction boundary; directory binding uses the real station SPI. */
final class RuntimeFlowDatabaseFixture {
  final Map<String, DataModel> models = new HashMap<>();
  final DataSourceTransactionManager transactions;

  RuntimeFlowDatabaseFixture() {
    this("jdbc:h2:mem:runtime_flows_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
  }

  RuntimeFlowDatabaseFixture(String url) {
    var datasource = new JdbcDataSource();
    datasource.setURL(url);
    datasource.setUser("sa");
    transactions = new DataSourceTransactionManager(datasource);
    var metadata = TypedModelMetaHelper.scanModelMate("com.ouroboros.logicflow.manage.model");
    metadata.putAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.configuration.model"));
    var station = new SqlDataStation("runtime-flow-test", datasource, List.copyOf(metadata.values()), MigrationStrategy.AUTO, "H2");
    var jdbc = new JdbcTemplate(datasource);
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    RuntimeFlowTestStationProvider.station = station;
    DataModelCenter.refresh();
    station.getDataModelList().forEach(model -> models.put(model.getName(), model));
  }

  DataRuntimeFlowStore store() { return new DataRuntimeFlowStore(transactions); }

  <T> T withModels(Supplier<T> execution) {
    return execution.get();
  }
}
