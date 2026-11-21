package com.ouroboros.dynamicform.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

import com.ouroboros.data.exception.DataModelException;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;

class DynamicFormDataStationTest {

  @Test
  void addModelMigratesAndRefreshesCacheWhenModelAlreadyRegistered() {
    var station = new RecordingDynamicFormDataStation();
    var existing = model("DynamicFormExpense", "Old Expense");
    var replacement = model("DynamicFormExpense", "New Expense");
    station.initModelMetas(Collections.singletonList(existing));

    var result = station.addModel(replacement);

    assertTrue(result);
    assertEquals(1, station.migrateCalls);
    assertEquals("New Expense", station.getDataModel("DynamicFormExpense").orElseThrow().getLabel());
  }

  @Test
  void addModelDoesNotRefreshCacheWhenDuplicateMigrationFails() {
    var station = new RecordingDynamicFormDataStation();
    station.migrateResult = false;
    var existing = model("DynamicFormExpense", "Old Expense");
    var replacement = model("DynamicFormExpense", "New Expense");
    station.initModelMetas(Collections.singletonList(existing));

    var result = station.addModel(replacement);

    assertFalse(result);
    assertEquals(1, station.migrateCalls);
    assertEquals("Old Expense", station.getDataModel("DynamicFormExpense").orElseThrow().getLabel());
  }

  private static DataModelMeta model(String name, String label) {
    var meta = new DataModelMeta();
    meta.setName(name);
    meta.setRawName("t_" + name.toLowerCase());
    meta.setLabel(label);
    return meta;
  }

  private static class RecordingDynamicFormDataStation extends DynamicFormDataStation {
    private int migrateCalls;
    private boolean migrateResult = true;

    RecordingDynamicFormDataStation() {
      super(null);
    }

    @Override
    public Either<DataModelException, Boolean> migrate(List<DataModel> dataModels) {
      migrateCalls++;
      return Either.right(migrateResult);
    }
  }
}
