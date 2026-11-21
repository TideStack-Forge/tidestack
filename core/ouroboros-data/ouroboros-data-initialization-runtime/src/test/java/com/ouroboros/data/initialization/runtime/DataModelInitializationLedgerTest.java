package com.ouroboros.data.initialization.runtime;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.vavr.control.Try;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.initialization.DataInitializationAsset;
import com.ouroboros.data.initialization.DataInitializationPlan;
import com.ouroboros.data.initialization.InitializationBlock;
import com.ouroboros.data.initialization.LedgerKey;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.RecordList;

class DataModelInitializationLedgerTest {

  @AfterEach
  void resetDataModelMap() throws Exception {
    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  void beginReusesConcurrentSuccessRecordAsNewRun() throws Exception {
    DataModel model = publishLedgerModel();
    when(model.query(anyMap()))
        .thenReturn(Try.success(RecordList.empty()))
        .thenReturn(Try.success(RecordList.of(List.of(Map.of("status", "SUCCESS")))));
    when(model.insert(anyMap())).thenReturn(Try.failure(new IllegalStateException("duplicate ledger")));
    when(model.update(anyString(), anyMap())).thenReturn(Try.success(1L));

    var run = new DataModelInitializationLedger().begin(asset(), block(), key());

    assertEquals(key(), run.key());
    verify(model).update(anyString(), anyMap());
  }

  private DataModel publishLedgerModel() throws Exception {
    DataModel model = mock(DataModel.class);
    var dataModels = new TreeMap<String, DataModel>(String.CASE_INSENSITIVE_ORDER);
    dataModels.put(DataModelInitializationLedger.MODEL_NAME, model);

    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, dataModels);
    return model;
  }

  private DataInitializationAsset asset() {
    return new DataInitializationAsset("classpath", "META-INF/ouroboros/metadata/status.data-init.json",
        new DataInitializationPlan("system-status", "v1", List.of(block())));
  }

  private InitializationBlock block() {
    return new InitializationBlock("statuses", "Status", "v1", List.of("code"),
        List.of(Map.of("code", "enabled", "label", "Enabled")));
  }

  private LedgerKey key() {
    return new LedgerKey("system-status", "statuses", "Status", "v1", "fixed-hash");
  }
}
