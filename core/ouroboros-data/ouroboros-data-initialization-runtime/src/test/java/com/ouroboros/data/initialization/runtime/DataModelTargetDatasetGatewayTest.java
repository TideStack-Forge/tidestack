package com.ouroboros.data.initialization.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.vavr.control.Try;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.RecordList;

class DataModelTargetDatasetGatewayTest {

  @AfterEach
  void resetDataModelMap() throws Exception {
    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  void findOneReturnsEmptyWhenNoRowsMatch() throws Exception {
    Map<String, Object> matchValues = Map.of("code", "enabled");
    DataModel model = publishModel("Status");
    when(model.query(Map.of("WHERE", matchValues))).thenReturn(Try.success(RecordList.empty()));

    var row = new DataModelTargetDatasetGateway().findOne("Status", matchValues);

    assertTrue(row.isEmpty());
  }

  @Test
  void findOneReturnsMatchedRow() throws Exception {
    Map<String, Object> matchValues = Map.of("code", "enabled");
    Map<String, Object> values = Map.of("code", "enabled", "label", "Enabled");
    DataModel model = publishModel("Status");
    when(model.query(Map.of("WHERE", matchValues))).thenReturn(Try.success(RecordList.of(List.of(values))));

    var row = new DataModelTargetDatasetGateway().findOne("Status", matchValues);

    assertEquals("Enabled", row.orElseThrow().values().get("label"));
  }

  @Test
  void findOneFailsWhenMatchIsNotUnique() throws Exception {
    Map<String, Object> matchValues = Map.of("code", "enabled");
    DataModel model = publishModel("Status");
    when(model.query(Map.of("WHERE", matchValues))).thenReturn(Try.success(RecordList.of(List.of(
        Map.of("code", "enabled", "label", "Enabled"),
        Map.of("code", "enabled", "label", "Duplicate")
    ))));

    assertThrows(IllegalStateException.class, () -> new DataModelTargetDatasetGateway().findOne("Status", matchValues));
  }

  @Test
  void insertDelegatesToTargetModel() throws Exception {
    Map<String, Object> values = Map.of("code", "enabled", "label", "Enabled");
    DataModel model = publishModel("Status");
    when(model.insert(values)).thenReturn(Try.success(mock(com.ouroboros.data.record.Record.class)));

    assertDoesNotThrow(() -> new DataModelTargetDatasetGateway().insert("Status", values));
  }

  private DataModel publishModel(String modelName) throws Exception {
    DataModel model = mock(DataModel.class);
    var dataModels = new TreeMap<String, DataModel>(String.CASE_INSENSITIVE_ORDER);
    dataModels.put(modelName, model);

    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, dataModels);
    return model;
  }
}
